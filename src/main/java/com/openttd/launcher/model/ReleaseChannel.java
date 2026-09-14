package com.openttd.launcher.model;

public enum ReleaseChannel {
    STABLE("Stable", "https://www.openttd.org/downloads/openttd-releases/latest.html"),
    TESTING("Testing", "https://www.openttd.org/downloads/openttd-releases/testing.html"),
    NIGHTLY("Nightly", "https://www.openttd.org/downloads/openttd-nightlies/latest.html");

    private final String displayName;
    private final String metadataUrl;

    ReleaseChannel(String displayName, String metadataUrl) {
        this.displayName = displayName;
        this.metadataUrl = metadataUrl;
    }

    public String displayName() { return displayName; }
    public String metadataUrl() { return metadataUrl; }

    @Override public String toString() { return displayName; }
}
