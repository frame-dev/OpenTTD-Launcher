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
        Path versions = root.resolve("versions");
        Files.createDirectories(versions);
        Path temp = Files.createTempFile("openttd-", ".zip");
        Path target = versions.resolve(release.channel().name().toLowerCase(Locale.ROOT) + "-" + release.version());
        try {
            listener.update("Downloading " + release.label(), 0, 1);
            HttpRequest request = HttpRequest.newBuilder(release.downloadUri()).header("User-Agent", "OpenTTD-Launcher/1.0").GET().build();
            HttpResponse<InputStream> response = releaseService.client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) throw new IOException("Download failed with HTTP " + response.statusCode());
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            copy(response.body(), temp, total, listener);
            if (Files.exists(target)) deleteTree(target);
            Files.createDirectories(target);
            listener.update("Extracting files", 0, 1);
            extract(temp, target, release.downloadUri().getPath());
            Path executable = findExecutable(target);
            if (executable == null) throw new IOException("The archive did not contain an OpenTTD executable");
            executable.toFile().setExecutable(true, false);
            Properties manifest = new Properties();
            manifest.setProperty("channel", release.channel().name());
            manifest.setProperty("version", release.version());
            try (var out = Files.newOutputStream(target.resolve(".launcher.properties"))) { manifest.store(out, "OpenTTD Launcher managed version"); }
            listener.update("Installed " + release.label(), 1, 1);
            return target;
        } finally { Files.deleteIfExists(temp); }
    }

    public InstalledVersion findInstalled(Path root, ReleaseChannel channel) throws IOException {
        Path versions = root.resolve("versions");
        if (!Files.isDirectory(versions)) return null;
        InstalledVersion managed;
        try (Stream<Path> paths = Files.list(versions)) {
            managed = paths.filter(Files::isDirectory).map(path -> readInstalled(path, channel)).filter(java.util.Objects::nonNull)
                    .max(Comparator.comparing(InstalledVersion::version, InstallService::compareVersions)).orElse(null);
        }
        if (managed != null) return managed;
        Path externalExecutable = findExecutable(root);
        if (externalExecutable == null) return null;
        return new InstalledVersion(channel, detectVersion(externalExecutable), root, externalExecutable);
    }

    public Path executable(Path directory) throws IOException { return findExecutable(directory); }

    private InstalledVersion readInstalled(Path path, ReleaseChannel channel) {
        try {
            Properties properties = new Properties();
            Path marker = path.resolve(".launcher.properties");
            if (!Files.exists(marker)) return null;
            try (var in = Files.newInputStream(marker)) { properties.load(in); }
            if (!channel.name().equals(properties.getProperty("channel"))) return null;
            Path executable = findExecutable(path);
            return executable == null ? null : new InstalledVersion(channel, properties.getProperty("version"), path, executable);
        } catch (IOException e) { return null; }
    }

    private static int compareVersions(String left, String right) {
        return left.compareToIgnoreCase(right);
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
        try (Stream<Path> paths = Files.walk(root, 4)) {
            return paths.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.equalsIgnoreCase("openttd.exe") || name.equals("openttd");
            }).findFirst().orElse(null);
        }
    }

    private static void copy(InputStream input, Path target, long total, ProgressListener listener) throws IOException {
        try (input; var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024]; long count = 0; int read;
            while ((read = input.read(buffer)) >= 0) { output.write(buffer, 0, read); count += read; listener.update("Downloading archive", count, total); }
        }
    }

    private static void extract(Path archive, Path target, String archivePath) throws IOException {
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
            while ((entry = (TarArchiveEntry) input.getNextEntry()) != null) {
                Path destination = normalizedTarget.resolve(entry.getName()).normalize();
                if (!destination.startsWith(normalizedTarget)) throw new IOException("Unsafe archive entry: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(destination);
                else { Files.createDirectories(destination.getParent()); Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.delete(path); } catch (IOException e) { throw new RuntimeException(e); } }); }
    }
}
