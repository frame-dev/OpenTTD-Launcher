package com.openttd.launcher.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TtdDataServiceTest {
    @TempDir Path root;

    @Test void incompleteCacheIsNotUsed() throws Exception {
        Path cache = Files.createDirectories(root.resolve("ttd-data"));
        Files.writeString(cache.resolve("trg1r.grf"), "partial");
        Path game = Files.createDirectories(root.resolve("game"));
        new TtdDataService().applyCached(root, game.resolve("openttd.exe"));
        assertFalse(Files.exists(game.resolve("data")));
    }

    @Test void copiesOnlyRequiredFilesToBothLayoutsWithoutOverwriting() throws Exception {
        Path cache = Files.createDirectories(root.resolve("ttd-data"));
        for (String name : TtdDataService.REQUIRED) Files.writeString(cache.resolve(name), "original data");
        Files.writeString(cache.resolve("setup.exe"), "must not copy");
        Path game = Files.createDirectories(root.resolve("versions/stable-old/nested"));
        Files.createDirectories(game.resolve("data"));
        Files.writeString(game.resolve("data/sample.cat"), "existing sound");
        new TtdDataService().applyCached(root, game.resolve("openttd.exe"));
        assertEquals("existing sound", Files.readString(game.resolve("data/sample.cat")));
        for (String name : TtdDataService.REQUIRED) assertTrue(Files.exists(game.resolve("baseset").resolve(name)));
        assertFalse(Files.exists(game.resolve("data/setup.exe")));
        assertFalse(Files.exists(game.resolve("baseset/setup.exe")));
    }

    @Test void zeroLengthRequiredFilePreventsCacheUse() throws Exception {
        Path cache = Files.createDirectories(root.resolve("ttd-data"));
        for (String name : TtdDataService.REQUIRED) Files.writeString(cache.resolve(name), "data");
        Files.writeString(cache.resolve("sample.cat"), "");
        assertFalse(TtdDataService.complete(cache));
    }

    @Test void completeCacheWorksWithoutDownload() throws Exception {
        Path cache = Files.createDirectories(root.resolve("ttd-data"));
        for (String name : TtdDataService.REQUIRED) Files.writeString(cache.resolve(name), "data");
        new TtdDataService().prepare(root, (message, count, total) -> assertEquals("Using cached TTD files", message));
    }
}
