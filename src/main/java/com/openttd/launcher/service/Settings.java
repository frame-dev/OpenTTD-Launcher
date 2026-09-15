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
    private final Path file;
    private final Properties values = new Properties();

    public Settings() { this(Paths.get(System.getProperty("user.home"), ".openttd-launcher.properties")); }
    public Settings(Path file) { this.file = file; load(); }

    public record Preferences(String theme, String accent, int scale, boolean checkOnStartup,
            boolean minimizeOnLaunch, boolean restoreOnExit, boolean timestamps, int logLines, String arguments) {
        public Preferences {
            if (!java.util.Set.of("Dark", "Light").contains(theme)) throw new IllegalArgumentException("Choose a valid theme");
            if (!accent.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Choose a valid accent color");
            if (scale < 85 || scale > 150) throw new IllegalArgumentException("Text size must be between 85% and 150%");
            if (logLines < 100 || logLines > 10000) throw new IllegalArgumentException("Activity limit must be between 100 and 10000 lines");
            if (arguments.length() > 8192 || arguments.indexOf('\0') >= 0) throw new IllegalArgumentException("Launch arguments are invalid or too long");
        }
        public java.util.List<String> argumentList() {
            return arguments.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
        }
    }
    public static Preferences defaults() { return new Preferences("Dark", "#5bb1ff", 100, true, false, true, false, 1000, ""); }
    public Preferences preferences() {
        var d = defaults();
        try {
            return new Preferences(values.getProperty("theme", d.theme()), values.getProperty("accent", d.accent()),
                Integer.parseInt(values.getProperty("scale", "100")), Boolean.parseBoolean(values.getProperty("checkOnStartup", "true")),
                Boolean.parseBoolean(values.getProperty("minimizeOnLaunch", "false")), Boolean.parseBoolean(values.getProperty("restoreOnExit", "true")),
                Boolean.parseBoolean(values.getProperty("timestamps", "false")), Integer.parseInt(values.getProperty("logLines", "1000")), values.getProperty("arguments", ""));
        } catch (IllegalArgumentException ex) { return d; }
    }
    public void preferences(Preferences p) {
        Properties previous = new Properties(); previous.putAll(values);
        values.setProperty("theme", p.theme()); values.setProperty("accent", p.accent()); values.setProperty("scale", "" + p.scale());
        values.setProperty("checkOnStartup", "" + p.checkOnStartup()); values.setProperty("minimizeOnLaunch", "" + p.minimizeOnLaunch());
        values.setProperty("restoreOnExit", "" + p.restoreOnExit()); values.setProperty("timestamps", "" + p.timestamps());
        values.setProperty("logLines", "" + p.logLines()); values.setProperty("arguments", p.arguments());
        try { save(); } catch (java.io.UncheckedIOException ex) { values.clear(); values.putAll(previous); throw ex; }
    }
    public ReleaseChannel channel() { try { return ReleaseChannel.valueOf(values.getProperty("channel", "STABLE")); } catch (IllegalArgumentException e) { return ReleaseChannel.STABLE; } }
    public void channel(ReleaseChannel channel) { values.setProperty("channel", channel.name()); save(); }
    public Path installRoot() { return Paths.get(values.getProperty("installRoot", Paths.get(System.getProperty("user.home"), "OpenTTD Launcher").toString())); }
    public void installRoot(Path path) { values.setProperty("installRoot", path.toAbsolutePath().toString()); save(); }

    private void load() { if (!Files.exists(file)) return; try (InputStream in = Files.newInputStream(file)) { values.load(in); } catch (IOException ignored) { } }
    private void save() {
        Path temporary = null;
        try {
            Path absolute = file.toAbsolutePath(); Files.createDirectories(absolute.getParent());
            temporary = Files.createTempFile(absolute.getParent(), ".launcher-settings-", ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary)) { values.store(out, "OpenTTD Launcher settings"); }
            try { Files.move(temporary, absolute, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temporary, absolute, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) { throw new java.io.UncheckedIOException("Could not save launcher settings", ex); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {} }
    }
}
