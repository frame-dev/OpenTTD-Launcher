package com.openttd.launcher.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TtdDataServiceTest {
    @TempDir Path root;

    @Test void importsCaseInsensitiveLocalFilesAndExcludesUnrelatedFiles() throws Exception {
        Path source = Files.createDirectory(root.resolve("original"));
        for (String name : TtdDataService.REQUIRED) Files.writeString(source.resolve(name.toUpperCase(java.util.Locale.ROOT)), "data");
        Files.writeString(source.resolve("setup.exe"), "ignored");
        new TtdDataService().importDirectory(source, root);
        assertTrue(TtdDataService.complete(root.resolve("ttd-data")));
        assertFalse(Files.exists(root.resolve("ttd-data/setup.exe")));
    }

    @Test void incompleteImportPreservesCacheAndMissingCacheExplainsSetup() throws Exception {
        Path source = Files.createDirectory(root.resolve("incomplete"));
        Path cache = Files.createDirectory(root.resolve("ttd-data"));
        Files.writeString(cache.resolve("sample.cat"), "keep");
        var service = new TtdDataService();
        assertThrows(java.io.IOException.class, () -> service.importDirectory(source, root));
        assertEquals("keep", Files.readString(cache.resolve("sample.cat")));
        var failure = assertThrows(java.io.IOException.class, () -> service.prepare(root, (m, c, t) -> {}));
        assertTrue(failure.getMessage().contains("Set up TTD files"));
    }

    @Test void legacyLaunchRestoresMissingAndEmptyFilesFromCache() throws Exception {
        Path cache = Files.createDirectories(root.resolve("ttd-data"));
        for (String name : TtdDataService.REQUIRED) Files.writeString(cache.resolve(name), "original data");
        Path game = Files.createDirectories(root.resolve("versions/stable-0.5.3"));
        Path data = Files.createDirectories(game.resolve("data"));
        Files.writeString(data.resolve("openttd.grf"), "bundled graphics");
        Files.writeString(data.resolve("sample.cat"), "");
        assertTrue(TtdDataService.legacyLayout(game.resolve("openttd.exe")));
        new TtdDataService().ensureForLaunch(root, game.resolve("openttd.exe"), (m, c, t) -> {});
        assertTrue(TtdDataService.complete(data));
        assertEquals("original data", Files.readString(data.resolve("sample.cat")));
        assertEquals("bundled graphics", Files.readString(data.resolve("openttd.grf")));
    }

    @Test void modernLayoutDoesNotRequireOriginalFilesOrDownload() throws Exception {
        Path game = Files.createDirectories(root.resolve("modern"));
        new TtdDataService().ensureForLaunch(root, game.resolve("openttd.exe"), (m, c, t) -> fail("Should not download"));
        assertFalse(Files.exists(root.resolve("ttd-data")));
        Path data = Files.createDirectories(game.resolve("data"));
        Files.writeString(data.resolve("openttd.grf"), "graphics");
        Files.writeString(data.resolve("opengfx.obg"), "base set descriptor");
        assertFalse(TtdDataService.legacyLayout(game.resolve("openttd.exe")));
    }

    @Test void completeLegacyInstallationWorksWithoutCache() throws Exception {
        Path data = Files.createDirectories(root.resolve("game/data"));
        Files.writeString(data.resolve("openttd.grf"), "bundled graphics");
        for (String name : TtdDataService.REQUIRED) Files.writeString(data.resolve(name), "existing data");
        new TtdDataService().ensureForLaunch(root, data.getParent().resolve("openttd.exe"), (m, c, t) -> fail("Should not download"));
        assertFalse(Files.exists(root.resolve("ttd-data")));
    }

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
