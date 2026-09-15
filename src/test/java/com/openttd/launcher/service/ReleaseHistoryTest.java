package com.openttd.launcher.service;

import com.openttd.launcher.model.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseHistoryTest {
    @Test void coversAllPublishedMacAndWindowsArchiveNamesInAudit() throws Exception {
        try (var input = getClass().getResourceAsStream("/official-archive-index.json")) {
            assertNotNull(input);
            var entries = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(241, entries.size());
            int windows = 0, mac = 0;
            for (var entry : entries) {
                var object = entry.getAsJsonObject();
                String version = object.get("version").getAsString();
                String html = "";
                for (var asset : object.getAsJsonArray("assets")) html += "<a href=\"" + asset.getAsString() + "\">file</a>";
                var release = new ReleaseInfo(ReleaseChannel.STABLE, version, null, "https://cdn.openttd.org/openttd-releases/" + version + "/");
                if (html.contains("windows-win32.zip") || html.contains("windows-win64.zip")) {
                    assertNotNull(ReleaseService.parseArchive(html, release, "windows-win64"), version); windows++;
                }
                if (html.contains("windows-win32.zip")) {
                    assertTrue(ReleaseService.parseArchive(html, release, "windows-win32").downloadUri().toString().endsWith("windows-win32.zip"), version);
                }
                if (html.matches("(?s).*-macos(?:x)?(?:-universal)?\\.(zip|dmg).*")) {
                    assertNotNull(ReleaseService.parseArchive(html, release, "macos-universal"), version); mac++;
                }
            }
            assertEquals(234, windows);
            assertEquals(214, mac);
        }
    }
    @Test void supportsHistoricalMacNamesAndPrefersModernBuild() throws Exception {
        var release = new ReleaseInfo(ReleaseChannel.STABLE, "1.5.3", null, "https://cdn.openttd.org/openttd-releases/1.5.3/");
        for (String suffix : java.util.List.of("macosx-universal.zip", "macosx.zip", "macosx.dmg")) {
            var resolved = ReleaseService.parseArchive("<a href=\"openttd-1.5.3-" + suffix + "\">file</a>", release, "macos-universal");
            assertTrue(resolved.downloadUri().toString().endsWith(suffix));
        }
    }

    @Test void windowsX64FallsBackToX86ButPrefersNative() throws Exception {
        var release = new ReleaseInfo(ReleaseChannel.STABLE, "0.3.6", null, "https://cdn.openttd.org/openttd-releases/0.3.6/");
        String x86 = "<a href=\"openttd-0.3.6-windows-win32.zip\">file</a>";
        assertTrue(ReleaseService.parseArchive(x86, release, "windows-win64").downloadUri().toString().endsWith("win32.zip"));
        String both = x86 + "<a href=\"openttd-0.3.6-windows-win64.zip\">file</a>";
        assertTrue(ReleaseService.parseArchive(both, release, "windows-win64").downloadUri().toString().endsWith("win64.zip"));
        assertTrue(ReleaseService.parseArchive(both, release, "windows-win32").downloadUri().toString().endsWith("win32.zip"));
    }
    private static final String INDEX = """
            <a href="../">Parent</a><a href="14.1/">14.1</a>
            <a href="9.0/">9.0</a><a href="15.0-RC2/">RC</a>
            <a href="15.0-beta1/">Beta</a><a href="internal/">internal</a>
            <a href="14.1/">duplicate</a>
            """;
    @Test void stableHistoryExcludesTestingAndSortsNumerically() {
        var releases = ReleaseService.parseOfficialHistory(INDEX, ReleaseChannel.STABLE, "https://cdn.openttd.org/openttd-releases/");
        assertEquals(java.util.List.of("14.1", "9.0"), releases.stream().map(ReleaseInfo::version).toList());
    }
    @Test void testingHistoryExcludesStable() {
        var releases = ReleaseService.parseOfficialHistory(INDEX, ReleaseChannel.TESTING, "https://cdn.openttd.org/openttd-releases/");
        assertEquals(java.util.List.of("15.0-RC2", "15.0-beta1"), releases.stream().map(ReleaseInfo::version).toList());
    }
    @Test void nightlyHistoryKeepsYearInSourceAddress() {
        var releases = ReleaseService.parseOfficialHistory("<a href=\"20260912-master-g0c2e7ec534/\">nightly</a>", ReleaseChannel.NIGHTLY, "https://cdn.openttd.org/openttd-nightlies/2026/");
        assertEquals("https://cdn.openttd.org/openttd-nightlies/2026/20260912-master-g0c2e7ec534/", releases.getFirst().pageUri());
    }
    @Test void resolvesExactVersionAndArchitecture() throws Exception {
        var release = new ReleaseInfo(ReleaseChannel.STABLE, "14.1", null, "https://cdn.openttd.org/openttd-releases/14.1/");
        var resolved = ReleaseService.parseArchive("<a href=\"openttd-14.1-windows-win64.zip\">download</a>", release, "windows-win64");
        assertEquals("https://cdn.openttd.org/openttd-releases/14.1/openttd-14.1-windows-win64.zip", resolved.downloadUri().toString());
        assertThrows(IOException.class, () -> ReleaseService.parseArchive("<a href=\"openttd-14.1-windows-win64.zip\">download</a>", release, "windows-arm64"));
    }
}
