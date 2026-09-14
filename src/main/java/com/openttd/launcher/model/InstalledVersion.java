package com.openttd.launcher.model;

import java.nio.file.Path;

public record InstalledVersion(ReleaseChannel channel, String version, Path directory, Path executable) {
    public String label() { return channel.displayName() + " " + version; }
}
