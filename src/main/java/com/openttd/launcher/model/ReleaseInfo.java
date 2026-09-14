package com.openttd.launcher.model;

import java.net.URI;

public record ReleaseInfo(ReleaseChannel channel, String version, URI downloadUri, String pageUri) {
    public String label() { return channel.displayName() + " " + version; }
}
