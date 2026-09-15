package com.openttd.launcher.service;

import com.openttd.launcher.model.InstalledVersion;
import com.openttd.launcher.model.ReleaseChannel;
import com.openttd.launcher.model.ReleaseInfo;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class InstallService {
    private static final Pattern VERSION = Pattern.compile("(?:OpenTTD|openttd)[^0-9]*([0-9][^\\s]*)", Pattern.CASE_INSENSITIVE);
    public interface ProgressListener { void update(String message, long completed, long total); }
    private final ReleaseService releaseService;

    public InstallService(ReleaseService releaseService) { this.releaseService = releaseService; }

    public Path install(ReleaseInfo release, Path root, ProgressListener listener) throws Exception {
        if (release.version() == null || !release.version().matches("[A-Za-z0-9][A-Za-z0-9._+-]*")) {
            throw new IOException("Invalid release version");
        }
        Path versions = root.toAbsolutePath().normalize().resolve("versions");
        Files.createDirectories(versions);
        Path temp = Files.createTempFile("openttd-", release.downloadUri().getPath().endsWith(".dmg") ? ".dmg" : ".archive");
        Path target = versions.resolve(release.channel().name().toLowerCase(Locale.ROOT) + "-" + release.version());
        Path staging = null;
        Path backup = null;
        try {
            listener.update("Downloading " + release.label(), 0, 1);
            HttpRequest request = HttpRequest.newBuilder(release.downloadUri()).header("User-Agent", "OpenTTD-Launcher/1.0").GET().build();
            HttpResponse<InputStream> response = releaseService.client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IOException("Download failed with HTTP " + response.statusCode());
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            copy(response.body(), temp, total, listener);
            staging = Files.createTempDirectory(versions, ".install-");
            listener.update("Extracting files", 0, 1);
            extract(temp, staging, release.downloadUri().getPath());
            Path executable = findExecutable(staging);
            if (executable == null) throw new IOException("The archive did not contain an OpenTTD executable");
            executable.toFile().setExecutable(true, false);
            new TtdDataService().applyCached(root, executable);
            Properties manifest = new Properties();
            manifest.setProperty("channel", release.channel().name());
            manifest.setProperty("version", release.version());
            try (var out = Files.newOutputStream(staging.resolve(".launcher.properties"))) { manifest.store(out, "OpenTTD Launcher managed version"); }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                backup = versions.resolve(".backup-" + java.util.UUID.randomUUID());
                Files.move(target, backup);
            }
            try {
                Files.move(staging, target);
                staging = null;
            } catch (IOException failure) {
                if (backup != null) {
                    try { Files.move(backup, target); backup = null; }
                    catch (IOException rollback) { failure.addSuppressed(rollback); }
                }
                throw failure;
            }
            if (backup != null) {
                try { deleteTree(backup); }
                catch (IOException cleanup) { /* Preserve the backup if it is still in use. */ }
            }
            listener.update("Installed " + release.label(), 1, 1);
            return target;
        } finally {
            try { if (staging != null) deleteTree(staging); }
            finally { Files.deleteIfExists(temp); }
        }
    }

    public InstalledVersion findInstalled(Path root, ReleaseChannel channel) throws IOException {
        Path versions = root.resolve("versions");
        InstalledVersion managed = null;
        if (Files.isDirectory(versions)) {
        try (Stream<Path> paths = Files.list(versions)) {
            managed = paths.filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(Files::isDirectory).map(path -> readInstalled(path, channel)).filter(java.util.Objects::nonNull)
                    .max(Comparator.comparing(InstalledVersion::version, InstallService::compareVersions)).orElse(null);
        }
        }
        if (managed != null) return managed;
        Path externalExecutable = findExecutable(root, versions);
        if (externalExecutable == null) return null;
        return new InstalledVersion(channel, detectVersion(externalExecutable), root, externalExecutable);
    }

    public Path executable(Path directory) throws IOException { return findExecutable(directory); }

    public java.util.List<InstalledVersion> listInstalled(Path root, ReleaseChannel channel) throws IOException {
        Path versions = root.resolve("versions");
        if (!Files.isDirectory(versions)) return java.util.List.of();
        try (var paths = Files.list(versions)) {
            return paths.filter(path -> !path.getFileName().toString().startsWith("."))
                    .map(path -> readInstalled(path, channel)).filter(java.util.Objects::nonNull)
                    .sorted((a, b) -> compareVersions(b.version(), a.version())).toList();
        }
    }

    private InstalledVersion readInstalled(Path path, ReleaseChannel channel) {
        try {
            Properties properties = new Properties();
            Path marker = path.resolve(".launcher.properties");
            if (!Files.exists(marker)) return null;
            try (var in = Files.newInputStream(marker)) { properties.load(in); }
            if (!channel.name().equals(properties.getProperty("channel"))) return null;
            if (properties.getProperty("version", "").isBlank()) return null;
            Path executable = findExecutable(path);
            return executable == null ? null : new InstalledVersion(channel, properties.getProperty("version"), path, executable);
        } catch (IOException e) { return null; }
    }

    static int compareVersions(String left, String right) {
        Matcher a = Pattern.compile("[0-9]+|[^0-9]+").matcher(left);
        Matcher b = Pattern.compile("[0-9]+|[^0-9]+").matcher(right);
        while (true) {
            boolean hasA = a.find(), hasB = b.find();
            if (!hasA || !hasB) {
                if (hasA == hasB) return 0;
                // A final release follows its beta/RC versions.
                String remainder = hasA ? left.substring(a.start()) : right.substring(b.start());
                int order = remainder.matches("(?i)[-._]?(alpha|beta|rc).*" ) ? -1 : 1;
                return hasA ? order : -order;
            }
            String x = a.group(), y = b.group();
            int order = Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))
                    ? new BigInteger(x).compareTo(new BigInteger(y)) : x.compareToIgnoreCase(y);
            if (order != 0) return order;
        }
    }

    private static String detectVersion(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "--version").redirectErrorStream(true).start();
            String output;
            try (var reader = process.inputReader()) { output = reader.readLine(); }
            process.waitFor();
            if (output != null) {
                Matcher matcher = VERSION.matcher(output);
                if (matcher.find()) return matcher.group(1);
            }
        } catch (Exception ignored) { }
        return "Detected";
    }

    private static Path findExecutable(Path root) throws IOException {
        return findExecutable(root, null);
    }

    private static Path findExecutable(Path root, Path excluded) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> paths = Files.walk(root, 4)) {
            return paths.filter(path -> excluded == null || !path.startsWith(excluded))
                    .filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.equalsIgnoreCase("openttd.exe") || name.equals("openttd");
            }).findFirst().orElse(null);
        }
    }

    private static void copy(InputStream input, Path target, long total, ProgressListener listener) throws IOException {
        try (input; var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024]; long count = 0; int read;
            while ((read = input.read(buffer)) >= 0) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download interrupted");
                output.write(buffer, 0, read); count += read; listener.update("Downloading archive", count, total);
            }
            if (total >= 0 && count != total) throw new IOException("The archive download was incomplete");
        }
    }

    private static void extract(Path archive, Path target, String archivePath) throws IOException, InterruptedException {
        if (archivePath.toLowerCase(Locale.ROOT).endsWith(".dmg")) { new MacDmgInstaller().install(archive, target); return; }
        if (archivePath.toLowerCase(Locale.ROOT).endsWith(".tar.xz")) extractTarXz(archive, target);
        else extractZip(archive, target);
    }

    private static void extractZip(Path zip, Path target) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        try (var input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path destination = normalizedTarget.resolve(entry.getName()).normalize();
                if (!destination.startsWith(normalizedTarget)) throw new IOException("Unsafe archive entry: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(destination);
                else { Files.createDirectories(destination.getParent()); Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }

    private static void extractTarXz(Path archive, Path target) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        try (var input = new TarArchiveInputStream(new XZInputStream(Files.newInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory() && !entry.isFile()) throw new IOException("Unsupported archive entry: " + entry.getName());
                Path destination = normalizedTarget.resolve(entry.getName()).normalize();
                if (!destination.startsWith(normalizedTarget)) throw new IOException("Unsafe archive entry: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(destination);
                else { Files.createDirectories(destination.getParent()); Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
