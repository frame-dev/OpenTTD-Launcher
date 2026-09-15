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

    @ParameterizedTest @ValueSource(strings = {"15.3", "1.5.3"})
    void installsOfficialModernAndHistoricalVersions(String version) throws Exception {
        var service = new ReleaseService();
        var release = service.resolveDownload(new ReleaseInfo(ReleaseChannel.STABLE, version, null,
                "https://cdn.openttd.org/openttd-releases/" + version + "/"));
        var installer = new InstallService(service);
        installer.install(release, root, (m, c, t) -> {});
        var game = installer.findInstalled(root, ReleaseChannel.STABLE);
        assertNotNull(game);
        assertEquals(version, game.version());
        assertTrue(Files.isRegularFile(game.executable()));
        if (version.equals("15.3") && System.getProperty("os.name").contains("Mac")) {
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

    @Test @EnabledOnOs(OS.WINDOWS)
    void installsEarly32BitWindowsRelease() throws Exception {
        var service = new ReleaseService();
        var release = service.resolveDownload(new ReleaseInfo(ReleaseChannel.STABLE, "0.3.6", null,
                "https://cdn.openttd.org/openttd-releases/0.3.6/"));
        assertTrue(release.downloadUri().toString().endsWith("windows-win32.zip"));
        assertNotNull(new InstallService(service).install(release, root, (m, c, t) -> {}));
    }
}
