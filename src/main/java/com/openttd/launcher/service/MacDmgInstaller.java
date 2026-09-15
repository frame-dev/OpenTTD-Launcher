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
        String architectures = run(List.of("/usr/bin/lipo", "-archs", executable.toString()));
        if (!architectures.contains("x86_64") && !architectures.contains("arm64")) {
            throw new IOException("This OpenTTD release contains only legacy Mac code (" + architectures.trim()
                    + "). Modern macOS cannot run 32-bit or PowerPC applications. Choose a newer release.");
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

    public static Path workingDirectory(Path executable) {
        Path app = appBundle(executable);
        return app == null ? executable.toAbsolutePath().getParent() : app.getParent();
    }
}
