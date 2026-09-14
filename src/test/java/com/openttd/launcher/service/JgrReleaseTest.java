package com.openttd.launcher.service;

import com.openttd.launcher.model.ReleaseChannel;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class JgrReleaseTest {
    private String metadata(String suffix) {
        String name = "openttd-jgrpp-0.73.2-" + suffix;
        return """
                {"tag_name":"jgrpp-0.73.2","assets":[
                  {"name":"source.zip","browser_download_url":"https://example.com/source.zip"},
                  {"name":"%s","browser_download_url":"https://github.com/JGRennison/OpenTTD-patches/releases/download/jgrpp-0.73.2/%s"}
                ]}
                """.formatted(name, name);
    }

    @Test void selectsWindowsX64() throws Exception {
        var release = ReleaseService.parseJgrRelease(metadata("windows-win64.zip"), "windows-win64");
        assertEquals(ReleaseChannel.JGRPP, release.channel());
        assertEquals("0.73.2", release.version());
        assertTrue(release.downloadUri().toString().endsWith("windows-win64.zip"));
    }

    @Test void selectsWindowsArm64() throws Exception {
        assertTrue(ReleaseService.parseJgrRelease(metadata("windows-arm64.zip"), "windows-arm64")
                .downloadUri().toString().endsWith("windows-arm64.zip"));
    }

    @Test void selectsLinuxArchive() throws Exception {
        assertTrue(ReleaseService.parseJgrRelease(metadata("linux-generic-amd64.tar.xz"), "linux-generic-amd64")
                .downloadUri().toString().endsWith("linux-generic-amd64.tar.xz"));
    }

    @Test void rejectsWrongArchitecture() {
        assertThrows(IOException.class, () -> ReleaseService.parseJgrRelease(metadata("windows-win64.zip"), "windows-arm64"));
    }

    @Test void explainsUnsupportedDiskImage() {
        var failure = assertThrows(IOException.class, () -> ReleaseService.parseJgrRelease(metadata("macos-universal.dmg"), "macos-universal"));
        assertTrue(failure.getMessage().contains("manual installation"));
    }

    @Test void rejectsMalformedMetadataAndUnexpectedDownloadHost() {
        assertThrows(IOException.class, () -> ReleaseService.parseJgrRelease("{}", "windows-win64"));
        assertThrows(IOException.class, () -> ReleaseService.parseJgrRelease(
                metadata("windows-win64.zip").replace("https://github.com/", "https://example.com/"), "windows-win64"));
    }
}
