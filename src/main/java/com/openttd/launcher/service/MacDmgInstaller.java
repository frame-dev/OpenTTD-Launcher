package com.openttd.launcher.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Uses macOS tools to preserve app bundle permissions, links and metadata. */
public final class MacDmgInstaller {
    @FunctionalInterface interface Command { void run(List<String> arguments) throws IOException, InterruptedException; }
    private final Command command;
    public MacDmgInstaller() { this(MacDmgInstaller::run); }
    MacDmgInstaller(Command command) { this.command = command; }

    public void install(Path image, Path target) throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac") && !os.equals("darwin")) throw new IOException("DMG installation requires macOS. Choose the Windows or Linux download on this computer.");
        installImage(image, target);
    }

    void installImage(Path image, Path target) throws IOException, InterruptedException {
        Path mount = Files.createTempDirectory("openttd-dmg-");
        boolean attached = false;
        Exception failure = null;
        try {
            command.run(List.of("/usr/bin/hdiutil", "attach", "-readonly", "-nobrowse", "-noautoopen", "-mountpoint", mount.toString(), image.toAbsolutePath().toString()));
            attached = true;
            Path app;
            try (var paths = Files.walk(mount, 3)) {
                var apps = paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().endsWith(".app"))
                        .filter(path -> Files.isRegularFile(path.resolve("Contents/MacOS/openttd"))).toList();
                if (apps.size() != 1) throw new IOException("Expected one OpenTTD application in the disk image; found " + apps.size());
                app = apps.getFirst();
            }
            command.run(List.of("/usr/bin/ditto", app.toString(), target.resolve(app.getFileName()).toAbsolutePath().toString()));
        } catch (IOException | InterruptedException ex) {
            failure = ex;
            throw ex;
        } finally {
            // Never recursively delete a mount point: it may still contain a mounted volume.
            boolean interrupted = Thread.interrupted() || failure instanceof InterruptedException;
            try {
                if (attached) command.run(List.of("/usr/bin/hdiutil", "detach", mount.toString()));
                else if (failure != null) {
                    // A failed or interrupted attach may still have mounted the image.
                    try { command.run(List.of("/usr/bin/hdiutil", "detach", mount.toString())); }
                    catch (IOException | InterruptedException cleanup) { failure.addSuppressed(cleanup); }
                }
                Files.deleteIfExists(mount);
            } catch (IOException | InterruptedException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
    }

    static void extractZip(Path archive, Path target) throws IOException, InterruptedException {
        run(List.of("/usr/bin/ditto", "-x", "-k", archive.toAbsolutePath().toString(), target.toAbsolutePath().toString()));
    }

    static void validateExecutable(Path executable) throws IOException, InterruptedException {
        if (appBundle(executable) == null) return;
        String architectures = architectures(executable);
        compatibility(architectures, appleSilicon(), System.getProperty("os.version"));
    }

    enum Compatibility { NATIVE, ROSETTA, INTEL_32 }

    static Compatibility compatibility(String architectures, boolean appleSilicon, String osVersion) throws IOException {
        Set<String> available = new HashSet<>(Arrays.asList(architectures.trim().split("\\s+")));
        if (appleSilicon && available.contains("arm64")) return Compatibility.NATIVE;
        if (available.contains("x86_64")) return appleSilicon ? Compatibility.ROSETTA : Compatibility.NATIVE;
        String[] version = osVersion.split("\\.");
        int major = Integer.parseInt(version[0]);
        int minor = version.length > 1 ? Integer.parseInt(version[1]) : 0;
        if (!appleSilicon && available.contains("i386") && (major < 10 || major == 10 && minor <= 14)) {
            return Compatibility.INTEL_32;
        }
        if (!appleSilicon && available.contains("arm64")) throw new IOException("This build requires an Apple Silicon Mac. Choose an Intel or Universal build.");
        throw new IOException("This release contains legacy Mac code (" + architectures.trim()
                + "). This Mac cannot run these 32-bit or PowerPC applications. Rosetta supports 64-bit Intel apps, not 32-bit Mac apps. Use an older compatible Intel Mac or choose a newer release.");
    }

    private static boolean appleSilicon() throws IOException, InterruptedException {
        // Works even when the launcher itself is running through Rosetta.
        if (System.getProperty("os.arch", "").matches("aarch64|arm64")) return true;
        try { return run(List.of("/usr/sbin/sysctl", "-n", "hw.optional.arm64")).trim().equals("1"); }
        catch (IOException absentOnOlderIntelMac) { return false; }
    }

    public static String launchCompatibility(Path executable) throws IOException, InterruptedException {
        if (appBundle(executable) == null) return null;
        var mode = compatibility(architectures(executable),
                appleSilicon(), System.getProperty("os.version"));
        return switch (mode) {
            case NATIVE -> "Launching the native Mac build";
            case INTEL_32 -> "Launching the 32-bit build on legacy Intel macOS";
            case ROSETTA -> "Launching the Intel build with Rosetta. If macOS asks, follow its Install Rosetta prompt.";
        };
    }

    static String architectures(Path executable) throws IOException {
        try (var file = new java.io.RandomAccessFile(executable.toFile(), "r")) {
            int magic = file.readInt();
            boolean little = magic == 0xbebafeca || magic == 0xbfbafeca || magic == 0xcefaedfe || magic == 0xcffaedfe;
            boolean fat = magic == 0xcafebabe || magic == 0xbebafeca || magic == 0xcafebabf || magic == 0xbfbafeca;
            boolean fat64 = magic == 0xcafebabf || magic == 0xbfbafeca;
            if (!fat && magic != 0xfeedface && magic != 0xfeedfacf && magic != 0xcefaedfe && magic != 0xcffaedfe) {
                throw new IOException("The Mac executable has an unrecognized format");
            }
            int count = fat ? file.readInt() : 1;
            if (fat && little) count = Integer.reverseBytes(count);
            if (count < 1 || count > 64) throw new IOException("Invalid Mac architecture table");
            var names = new ArrayList<String>();
            for (int i = 0; i < count; i++) {
                if (fat) file.seek(8L + i * (fat64 ? 32L : 20L));
                int cpu = file.readInt();
                if (little) cpu = Integer.reverseBytes(cpu);
                names.add(switch (cpu) {
                    case 7 -> "i386";
                    case 0x01000007 -> "x86_64";
                    case 0x0100000c -> "arm64";
                    case 18 -> "ppc";
                    case 0x01000012 -> "ppc64";
                    default -> "unknown-" + cpu;
                });
            }
            return String.join(" ", names);
        }
    }

    private static String run(List<String> arguments) throws IOException, InterruptedException {
        Path output = Files.createTempFile("openttd-macos-command-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            process.getOutputStream().close();
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new IOException(arguments.getFirst() + " timed out");
            if (process.exitValue() != 0) throw new IOException(arguments.getFirst() + " failed: " + Files.readString(output));
            return Files.readString(output);
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
            Files.deleteIfExists(output);
        }
    }

    public static Path appBundle(Path executable) {
        Path parent = executable.toAbsolutePath().getParent();
        if (parent != null && parent.getFileName().toString().equals("MacOS")) {
            Path contents = parent.getParent();
            if (contents != null && contents.getFileName().toString().equals("Contents")) {
                Path app = contents.getParent();
                if (app != null && app.getFileName().toString().endsWith(".app")) return app;
            }
        }
        return null;
    }

    public static List<String> launchCommand(Path executable) {
        Path app = appBundle(executable);
        return app == null ? List.of(executable.toString()) : List.of("/usr/bin/open", "-W", "-n", app.toString());
    }

    public static List<String> launchCommand(Path executable, List<String> arguments) {
        var command = new java.util.ArrayList<>(launchCommand(executable));
        if (!arguments.isEmpty() && appBundle(executable) != null) command.add("--args");
        command.addAll(arguments);
        return List.copyOf(command);
    }

    public static Path workingDirectory(Path executable) {
        Path app = appBundle(executable);
        return app == null ? executable.toAbsolutePath().getParent() : app.getParent();
    }
}
