package com.openttd.launcher.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MacDmgInstallerTest {
    @TempDir Path root;

    @Test void copiesAppAndDetachesImage() throws Exception { simulate(false); }
    @Test void detachesImageWhenCopyFails() throws Exception { simulate(true); }

    private void simulate(boolean failCopy) throws Exception {
        var calls = new ArrayList<List<String>>();
        Path target = Files.createDirectory(root.resolve("installed"));
        var installer = new MacDmgInstaller(args -> {
            calls.add(args);
            if (args.contains("attach")) {
                Path mount = Path.of(args.get(args.indexOf("-mountpoint") + 1));
                Path binary = mount.resolve("OpenTTD.app/Contents/MacOS/openttd");
                Files.createDirectories(binary.getParent()); Files.writeString(binary, "fixture");
            } else if (args.getFirst().endsWith("ditto")) {
                if (failCopy) throw new IOException("Copy failed");
                assertTrue(args.get(1).endsWith("OpenTTD.app"));
                assertEquals(target.resolve("OpenTTD.app").toString(), args.get(2));
            } else if (args.contains("detach")) {
                Path mount = Path.of(args.getLast());
                try (var paths = Files.walk(mount)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) if (!path.equals(mount)) Files.delete(path);
                }
            }
        });
        if (failCopy) assertThrows(IOException.class, () -> installer.installImage(root.resolve("game.dmg"), target));
        else installer.installImage(root.resolve("game.dmg"), target);
        assertEquals(3, calls.size());
        assertTrue(calls.getFirst().contains("-readonly"));
        assertTrue(calls.getLast().contains("detach"));
        assertFalse(Files.exists(Path.of(calls.getLast().getLast())));
    }

    @Test void launchUsesBundleAndDataStaysOutsideIt() throws Exception {
        Path executable = root.resolve("OpenTTD.app/Contents/MacOS/openttd");
        Files.createDirectories(executable.getParent()); Files.writeString(executable, "fixture");
        assertEquals(root.resolve("OpenTTD.app"), MacDmgInstaller.appBundle(executable));
        assertEquals(List.of("/usr/bin/open", "-W", "-n", root.resolve("OpenTTD.app").toString()), MacDmgInstaller.launchCommand(executable));
        assertEquals(root, MacDmgInstaller.workingDirectory(executable));
        Path cache = Files.createDirectory(root.resolve("ttd-data"));
        for (String name : TtdDataService.REQUIRED) Files.writeString(cache.resolve(name), "data");
        new TtdDataService().applyCached(root, executable);
        assertTrue(Files.exists(root.resolve("baseset/sample.cat")));
        assertFalse(Files.exists(executable.getParent().resolve("baseset")));
        assertEquals(executable, new InstallService(new ReleaseService()).executable(root));
    }

    @Test @EnabledOnOs(OS.MAC)
    void mountsRealDiskImageAndCopiesBundle() throws Exception {
        Path source = Files.createDirectory(root.resolve("source"));
        Path binary = source.resolve("OpenTTD.app/Contents/MacOS/openttd");
        Files.createDirectories(binary.getParent()); Files.writeString(binary, "#!/bin/sh\nexit 0\n");
        assertTrue(binary.toFile().setExecutable(true));
        Path image = root.resolve("test.dmg");
        var create = new ProcessBuilder("/usr/bin/hdiutil", "create", "-quiet", "-srcfolder", source.toString(), image.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
        assertTrue(create.waitFor(60, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, create.exitValue());
        Path target = Files.createDirectory(root.resolve("target"));
        new MacDmgInstaller().install(image, target);
        assertTrue(Files.isExecutable(target.resolve("OpenTTD.app/Contents/MacOS/openttd")));
    }

    @Test @EnabledOnOs(OS.MAC)
    void installsPublishedJgrDiskImage() throws Exception {
        var releases = new ReleaseService();
        var release = new com.openttd.launcher.model.ReleaseInfo(com.openttd.launcher.model.ReleaseChannel.JGRPP, "0.73.2",
                java.net.URI.create("https://github.com/JGRennison/OpenTTD-patches/releases/download/jgrpp-0.73.2/openttd-jgrpp-0.73.2-macos-universal.dmg"), "test");
        var installer = new InstallService(releases);
        Path target = installer.install(release, root, (m, c, t) -> {});
        var installed = installer.findInstalled(root, release.channel());
        assertNotNull(installed);
        assertEquals(target.resolve("OpenTTD.app"), MacDmgInstaller.appBundle(installed.executable()));
        assertTrue(Files.isExecutable(installed.executable()));
    }
}
