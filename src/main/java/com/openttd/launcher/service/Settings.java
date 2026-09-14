package com.openttd.launcher.service;

import com.openttd.launcher.model.ReleaseChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class Settings {
    private final Path file = Paths.get(System.getProperty("user.home"), ".openttd-launcher.properties");
    private final Properties values = new Properties();

    public Settings() { load(); }
    public ReleaseChannel channel() { try { return ReleaseChannel.valueOf(values.getProperty("channel", "STABLE")); } catch (IllegalArgumentException e) { return ReleaseChannel.STABLE; } }
    public void channel(ReleaseChannel channel) { values.setProperty("channel", channel.name()); save(); }
    public Path installRoot() { return Paths.get(values.getProperty("installRoot", Paths.get(System.getProperty("user.home"), "OpenTTD Launcher").toString())); }
    public void installRoot(Path path) { values.setProperty("installRoot", path.toAbsolutePath().toString()); save(); }

    private void load() { if (!Files.exists(file)) return; try (InputStream in = Files.newInputStream(file)) { values.load(in); } catch (IOException ignored) { } }
    private void save() { try (OutputStream out = Files.newOutputStream(file)) { values.store(out, "OpenTTD Launcher settings"); } catch (IOException ignored) { } }
}
