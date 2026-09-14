package com.openttd.launcher.service;

import com.openttd.launcher.model.ReleaseChannel;
import com.openttd.launcher.model.ReleaseInfo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class InstallServiceTest {
    @TempDir Path root;
    private final InstallService service = new InstallService(new ReleaseService());

    @Test void sortsNumericVersionsAndPrereleases() {
        assertTrue(InstallService.compareVersions("14.0", "9.0") > 0);
        assertTrue(InstallService.compareVersions("14.10", "14.9") > 0);
        assertTrue(InstallService.compareVersions("14.0", "14.0-RC1") > 0);
        assertTrue(InstallService.compareVersions("14.0-beta2", "14.0") < 0);
        assertTrue(InstallService.compareVersions("14.0-RC10", "14.0-RC2") > 0);
        assertEquals(0, InstallService.compareVersions("14.0", "14.0"));
    }

    @Test void selectsNewestManagedRelease() throws Exception {
        managed("stable-9.0", "9.0", ReleaseChannel.STABLE);
        managed("stable-14.0", "14.0", ReleaseChannel.STABLE);
        assertEquals("14.0", service.findInstalled(root, ReleaseChannel.STABLE).version());
        assertEquals(java.util.List.of("14.0", "9.0"), service.listInstalled(root, ReleaseChannel.STABLE)
                .stream().map(com.openttd.launcher.model.InstalledVersion::version).toList());
    }

    @Test void ignoresOtherChannelsAndTemporaryFolders() throws Exception {
        managed("stable-14.0", "14.0", ReleaseChannel.STABLE);
        managed(".install-pending", "99.0", ReleaseChannel.NIGHTLY);
        managed(".backup-old", "99.0", ReleaseChannel.NIGHTLY);
        assertNull(service.findInstalled(root, ReleaseChannel.NIGHTLY));
    }

    @Test void keepsJgrAndOfficialInstallationsSeparate() throws Exception {
        managed("stable-14.0", "14.0", ReleaseChannel.STABLE);
        managed("jgrpp-0.73.2", "0.73.2", ReleaseChannel.JGRPP);
        assertEquals("0.73.2", service.findInstalled(root, ReleaseChannel.JGRPP).version());
        assertEquals("14.0", service.findInstalled(root, ReleaseChannel.STABLE).version());
    }

    @Test void ignoresIncompleteManifest() throws Exception {
        Path directory = managed("stable-broken", "", ReleaseChannel.STABLE);
        assertTrue(Files.exists(directory));
        assertNull(service.findInstalled(root, ReleaseChannel.STABLE));
    }

    @Test void failedRepairPreservesWorkingInstallation() throws Exception {
        Path existing = managed("stable-14.0", "14.0", ReleaseChannel.STABLE);
        assertThrows(Exception.class, () -> installArchive("readme.txt", "No executable"));
        assertEquals("old game", Files.readString(existing.resolve("openttd.exe")));
        assertEquals("14.0", service.findInstalled(root, ReleaseChannel.STABLE).version());
        assertNoTemporaryDirectories();
    }

    @Test void successfulRepairReplacesWorkingInstallation() throws Exception {
        Path existing = managed("stable-14.0", "14.0", ReleaseChannel.STABLE);
        installArchive("openttd.exe", "new game");
        assertEquals("new game", Files.readString(existing.resolve("openttd.exe")));
        assertEquals("14.0", service.findInstalled(root, ReleaseChannel.STABLE).version());
        assertNoTemporaryDirectories();
    }

    @Test void rejectsArchiveTraversalWithoutDamagingInstallation() throws Exception {
        Path existing = managed("stable-14.0", "14.0", ReleaseChannel.STABLE);
        assertThrows(Exception.class, () -> installArchive("../../escaped.txt", "unsafe"));
        assertFalse(Files.exists(root.resolve("escaped.txt")));
        assertEquals("old game", Files.readString(existing.resolve("openttd.exe")));
        assertNoTemporaryDirectories();
    }

    @Test void rejectsVersionContainingPathBeforeDownloading() {
        var release = new ReleaseInfo(ReleaseChannel.STABLE, "../../outside", URI.create("http://127.0.0.1:1/game.zip"), "test");
        var failure = assertThrows(java.io.IOException.class, () -> service.install(release, root, (m, c, t) -> {}));
        assertEquals("Invalid release version", failure.getMessage());
        assertFalse(Files.exists(root.resolve("versions")));
    }

    private Path managed(String folder, String version, ReleaseChannel channel) throws Exception {
        Path directory = Files.createDirectories(root.resolve("versions").resolve(folder));
        Files.writeString(directory.resolve("openttd.exe"), "old game");
        Properties manifest = new Properties();
        manifest.setProperty("version", version);
        manifest.setProperty("channel", channel.name());
        try (var out = Files.newOutputStream(directory.resolve(".launcher.properties"))) { manifest.store(out, "test"); }
        return directory;
    }

    private void installArchive(String entry, String contents) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] archive = bytes.toByteArray();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/game.zip", exchange -> {
            exchange.sendResponseHeaders(200, archive.length);
            try (var out = exchange.getResponseBody()) { out.write(archive); }
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/game.zip");
            service.install(new ReleaseInfo(ReleaseChannel.STABLE, "14.0", uri, "test"), root, (m, c, t) -> {});
        } finally { server.stop(0); }
    }

    private void assertNoTemporaryDirectories() throws Exception {
        try (var paths = Files.list(root.resolve("versions"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".")));
        }
    }
}
