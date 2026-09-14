package com.openttd.launcher.service;

import com.openttd.launcher.model.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseHistoryTest {
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
