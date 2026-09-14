package com.openttd.launcher;

import com.openttd.launcher.model.*;
import com.openttd.launcher.service.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.*;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class VersionSelectionTest {
    @TempDir Path root;

    @Test void selectedOlderVersionRemainsLaunchTargetAfterRefresh() throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", root.toString());
        try {
            for (String version : java.util.List.of("14.0", "15.0")) {
                Path folder = Files.createDirectories(root.resolve("versions/stable-" + version));
                Files.writeString(folder.resolve("openttd.exe"), "test fixture");
                Files.writeString(folder.resolve(".launcher.properties"), "channel=STABLE\nversion=" + version + "\n");
            }
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var app = new LauncherApp();
                    ((Settings) get(app, "settings")).installRoot(root);
                    invoke(app, "buildContent");
                    invoke(app, "resetVersions");
                    @SuppressWarnings("unchecked") var versions = (JComboBox<ReleaseInfo>) get(app, "versionBox");
                    assertEquals(2, versions.getItemCount());
                    versions.setSelectedIndex(1);
                    assertEquals("14.0", ((InstalledVersion) get(app, "installed")).version());
                    assertTrue(((JButton) get(app, "launchButton")).isEnabled());
                    var latest = new ReleaseInfo(ReleaseChannel.STABLE, "15.0", URI.create("https://example.com/game.zip"), "source");
                    var field = LauncherApp.class.getDeclaredField("latest"); field.setAccessible(true); field.set(app, latest);
                    var merge = LauncherApp.class.getDeclaredMethod("mergeVersions", java.util.List.class); merge.setAccessible(true);
                    merge.invoke(app, java.util.List.of(latest));
                    invoke(app, "refreshInstalled");
                    assertEquals("14.0", ((InstalledVersion) get(app, "installed")).version());
                    assertTrue(((JButton) get(app, "updateButton")).isEnabled());
                    versions.addItem(new ReleaseInfo(ReleaseChannel.STABLE, "13.0", null, "source"));
                    versions.setSelectedIndex(2);
                    assertNull(get(app, "installed"));
                    assertFalse(((JButton) get(app, "launchButton")).isEnabled());
                    assertTrue(((JButton) get(app, "installButton")).isEnabled());
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } finally { System.setProperty("user.home", home); }
    }

    private Object get(LauncherApp app, String name) throws Exception {
        var field = LauncherApp.class.getDeclaredField(name); field.setAccessible(true); return field.get(app);
    }
    private void invoke(LauncherApp app, String name) throws Exception {
        var method = LauncherApp.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app);
    }
}
