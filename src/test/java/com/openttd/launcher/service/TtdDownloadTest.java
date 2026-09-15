package com.openttd.launcher.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class TtdDownloadTest {
    @TempDir Path root;
    @Test void selectsNativeExtractorPackages() throws Exception {
        assertEquals("7z2603.exe",TtdArchiveTool.packageName("Windows 10","x86"));
        assertEquals("7z2603-x64.exe",TtdArchiveTool.packageName("Windows 11","amd64"));
        assertEquals("7z2603-arm64.exe",TtdArchiveTool.packageName("Windows 11","aarch64"));
        assertEquals("7z2603-mac.tar.xz",TtdArchiveTool.packageName("Mac OS X","aarch64"));
        assertThrows(java.io.IOException.class,()->TtdArchiveTool.packageName("Other","unknown"));
    }
    @Test void completeCacheNeedsNoDownload() throws Exception {
        Path cache=Files.createDirectories(root.resolve("ttd-data"));
        for(String name:TtdDataService.REQUIRED) Files.writeString(cache.resolve(name),"cached");
        new TtdDataService().downloadAndPrepare(root,(m,c,t)->{});
        assertFalse(Files.exists(root.resolve("tools")));
    }
    @Test @EnabledIfSystemProperty(named="ttd.download.test",matches="true")
    void downloadsExtractsAndInstallsOriginalFiles() throws Exception {
        var service=new TtdDataService();
        service.downloadAndPrepare(root,(m,c,t)->System.out.println(m));
        assertTrue(TtdDataService.complete(root.resolve("ttd-data")));
        Path game=Files.createDirectories(root.resolve("game"));
        service.applyCached(root,game.resolve("openttd.exe"));
        assertTrue(TtdDataService.complete(game.resolve("data")));
        assertTrue(TtdDataService.complete(game.resolve("baseset")));
        try(var files=Files.list(root.resolve("ttd-data"))) { assertEquals(6,files.count()); }
        try(var files=Files.list(root)) { assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".ttd-download-"))); }
    }
}
