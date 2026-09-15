package com.openttd.launcher.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Imports original graphics and sound from a user-selected local directory. */
public final class TtdDataService {
    static final Set<String> REQUIRED = Set.of("trg1r.grf", "trgcr.grf", "trghr.grf", "trgir.grf", "trgtr.grf", "sample.cat");

    public void prepare(Path root, InstallService.ProgressListener progress) throws IOException {
        if (!complete(root.resolve("ttd-data"))) throw new IOException("Original TTD files are required. Click Set up TTD files and select a folder containing your original graphics and sound files.");
        progress.update("Using cached TTD files", 1, 1);
    }

    public void importDirectory(Path source, Path root) throws IOException {
        Map<String, Path> files = new HashMap<>();
        try (var entries = Files.list(source)) {
            entries.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (REQUIRED.contains(name)) files.put(name, p);
            });
        }
        for (String name : REQUIRED) {
            if (!files.containsKey(name) || Files.size(files.get(name)) == 0)
                throw new IOException("Selected folder is missing a non-empty " + name);
        }
        Path cache = root.resolve("ttd-data");
        Files.createDirectories(cache);
        for (String name : REQUIRED) {
            Path destination = cache.resolve(name);
            if (!Files.exists(destination) || !Files.isSameFile(files.get(name), destination))
                Files.copy(files.get(name), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static boolean complete(Path directory) {
        return REQUIRED.stream().allMatch(name -> {
            try { return Files.isRegularFile(directory.resolve(name), LinkOption.NOFOLLOW_LINKS) && Files.size(directory.resolve(name)) > 0; }
            catch (IOException ex) { return false; }
        });
    }

    static boolean legacyLayout(Path executable) throws IOException {
        Path data = MacDmgInstaller.workingDirectory(executable).resolve("data");
        if (!Files.isRegularFile(data.resolve("openttd.grf"))) return false;
        try (var files = Files.walk(data, 2)) {
            return files.noneMatch(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obg"));
        }
    }

    public void ensureForLaunch(Path root, Path executable, InstallService.ProgressListener progress) throws Exception {
        boolean legacy = legacyLayout(executable);
        Path data = MacDmgInstaller.workingDirectory(executable).resolve("data");
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
        Path game = MacDmgInstaller.workingDirectory(executable);
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
