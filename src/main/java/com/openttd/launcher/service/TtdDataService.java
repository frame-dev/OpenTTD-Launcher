package com.openttd.launcher.service;

import com.github.junrar.Archive;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Imports only the original graphics and sound files; never executes archive contents. */
public final class TtdDataService {
    public static final URI SOURCE = URI.create("https://www.tt-ms.de/downloads/ttd302011.rar");
    static final Set<String> REQUIRED = Set.of("trg1r.grf", "trgcr.grf", "trghr.grf", "trgir.grf", "trgtr.grf", "sample.cat");
    private static final long MAX_DOWNLOAD = 128L * 1024 * 1024;
    private static final long MAX_EXTRACTED = 512L * 1024 * 1024;

    public void prepare(Path root, InstallService.ProgressListener progress) throws Exception {
        Path cache = root.resolve("ttd-data");
        if (complete(cache)) { progress.update("Using cached TTD files", 1, 1); return; }
        Files.createDirectories(root);
        Path staging = Files.createTempDirectory(root, ".ttd-import-");
        try {
            Path rar = staging.resolve("original.rar");
            progress.update("Downloading original TTD files", 0, -1);
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
            var request = HttpRequest.newBuilder(SOURCE).timeout(Duration.ofMinutes(2)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() / 100 != 2) throw new IOException("TTD archive download returned HTTP " + response.statusCode());
                try (var output = Files.newOutputStream(rar)) {
                    byte[] buffer = new byte[65536]; long count = 0; int size;
                    while ((size = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("TTD download interrupted");
                        count += size;
                        if (count > MAX_DOWNLOAD) throw new IOException("TTD archive exceeds the download limit");
                        output.write(buffer, 0, size);
                    }
                }
            }
            Path extracted = Files.createDirectory(staging.resolve("data"));
            progress.update("Extracting TTD graphics and sound", 0, -1);
            importArchive(rar, extracted);
            Files.createDirectories(cache);
            // The completion check prevents an interrupted import from being used.
            for (String name : REQUIRED) Files.move(extracted.resolve(name), cache.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            progress.update("TTD files ready", 1, 1);
        } finally {
            try (var paths = Files.walk(staging)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    void importArchive(Path rar, Path destination) throws Exception {
        long[] extractedBytes = {0};
        try (var archive = new Archive(rar.toFile())) {
            var entry = archive.nextFileHeader();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String name = entry.getFileName().replace('\\', '/').toLowerCase(Locale.ROOT);
                    name = name.substring(name.lastIndexOf('/') + 1);
                    // Solid RAR streams must decode preceding files, even when discarded.
                    try (var output = REQUIRED.contains(name) ? Files.newOutputStream(destination.resolve(name)) : OutputStream.nullOutputStream()) {
                        archive.extractFile(entry, new FilterOutputStream(output) {
                            @Override public void write(int b) throws IOException { check(1); out.write(b); }
                            @Override public void write(byte[] b, int off, int len) throws IOException { check(len); out.write(b, off, len); }
                            private void check(int count) throws IOException {
                                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("TTD import interrupted");
                                extractedBytes[0] += count;
                                if (extractedBytes[0] > MAX_EXTRACTED) throw new IOException("TTD archive exceeds the extraction limit");
                            }
                        });
                    }
                }
                entry = archive.nextFileHeader();
            }
        }
        if (!complete(destination)) throw new IOException("The archive is missing required TTD graphics or sound files");
    }

    static boolean complete(Path directory) {
        return REQUIRED.stream().allMatch(name -> {
            try { return Files.isRegularFile(directory.resolve(name), LinkOption.NOFOLLOW_LINKS) && Files.size(directory.resolve(name)) > 0; }
            catch (IOException ex) { return false; }
        });
    }

    static boolean legacyLayout(Path executable) throws IOException {
        Path data = executable.toAbsolutePath().getParent().resolve("data");
        if (!Files.isRegularFile(data.resolve("openttd.grf"))) return false;
        try (var files = Files.walk(data, 2)) {
            return files.noneMatch(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obg"));
        }
    }

    public void ensureForLaunch(Path root, Path executable, InstallService.ProgressListener progress) throws Exception {
        boolean legacy = legacyLayout(executable);
        Path data = executable.toAbsolutePath().getParent().resolve("data");
        if (legacy && !complete(data)) {
            progress.update("This version needs original TTD files. Preparing them...", 0, -1);
            prepare(root, progress);
        }
        applyCached(root, executable);
        if (legacy && !complete(data)) throw new IOException("Required TTD files are missing from " + data + ". Use Set up TTD files before launching.");
    }

    public void applyCached(Path root, Path executable) throws IOException {
        Path cache = root.resolve("ttd-data");
        if (!complete(cache)) return;
        Path game = executable.toAbsolutePath().getParent();
        for (String folder : java.util.List.of("data", "baseset")) {
            Path destination = game.resolve(folder);
            Files.createDirectories(destination);
            for (String name : REQUIRED) {
                Path target = destination.resolve(name);
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Files.copy(cache.resolve(name), target);
                else if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && Files.size(target) == 0) {
                    Files.copy(cache.resolve(name), target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
