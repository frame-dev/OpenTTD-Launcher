package com.openttd.launcher.service;

import com.openttd.launcher.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs({OS.WINDOWS, OS.MAC})
class OfficialPlatformTest {
    @TempDir Path root;

    @ParameterizedTest @ValueSource(strings = {"15.3", "1.10.3", "1.5.3"})
    void installsOfficialModernAndHistoricalVersions(String version) throws Exception {
        var service = new ReleaseService();
        var release = service.resolveDownload(new ReleaseInfo(ReleaseChannel.STABLE, version, null,
                "https://cdn.openttd.org/openttd-releases/" + version + "/"));
        var installer = new InstallService(service);
        if (version.equals("1.5.3") && System.getProperty("os.name").contains("Mac")) {
            var failure = assertThrows(java.io.IOException.class, () -> installer.install(release, root, (m, c, t) -> {}));
            assertTrue(failure.getMessage().contains("32-bit or PowerPC"));
            assertNull(installer.findInstalled(root, ReleaseChannel.STABLE));
            return;
        }
        installer.install(release, root, (m, c, t) -> {});
        var game = installer.findInstalled(root, ReleaseChannel.STABLE);
        assertNotNull(game);
        assertEquals(version, game.version());
        assertTrue(Files.isRegularFile(game.executable()));
        if (System.getProperty("os.name").contains("Mac")) {
            assertNotNull(MacDmgInstaller.launchCompatibility(game.executable()));
            Path output = root.resolve("help.log");
            Process process = new ProcessBuilder(game.executable().toString(), "-h").redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Game help command timed out");
                assertEquals(0, process.exitValue(), Files.readString(output));
                assertTrue(Files.readString(output).contains("OpenTTD"));
            } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"0.3.6", "1.5.3", "15.3"}) @EnabledOnOs(OS.WINDOWS)
    void installs32BitWindowsRelease(String version) throws Exception {
        var service = new ReleaseService();
        String url = "https://cdn.openttd.org/openttd-releases/" + version + "/";
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).header("User-Agent", "OpenTTD-Launcher/1.0").GET().build();
        var response = service.client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var release = ReleaseService.parseArchive(response.body(), new ReleaseInfo(ReleaseChannel.STABLE, version, null, url), "windows-win32");
        assertTrue(release.downloadUri().toString().endsWith("windows-win32.zip"));
        var installer = new InstallService(service);
        installer.install(release, root, (m, c, t) -> {});
        Path executable = installer.findInstalled(root, ReleaseChannel.STABLE).executable();
        assertNotNull(WindowsCompatibility.launchCompatibility(executable));
        try (var file = new java.io.RandomAccessFile(executable.toFile(), "r")) {
            file.seek(0x3c); int pe = Integer.reverseBytes(file.readInt());
            file.seek(pe); assertEquals(0x50450000, file.readInt());
            assertEquals(0x4c01, file.readUnsignedShort(), "Expected an x86 (32-bit) PE executable");
        }
    }
}
