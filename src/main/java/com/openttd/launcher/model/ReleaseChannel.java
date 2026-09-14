package com.openttd.launcher.model;

public enum ReleaseChannel {
    STABLE("Stable", "https://www.openttd.org/downloads/openttd-releases/latest.html"),
    TESTING("Testing", "https://www.openttd.org/downloads/openttd-releases/testing.html"),
    NIGHTLY("Nightly", "https://www.openttd.org/downloads/openttd-nightlies/latest.html"),
    JGRPP("JGR Patch Pack", "https://api.github.com/repos/JGRennison/OpenTTD-patches/releases/latest");

    private final String displayName;
    private final String metadataUrl;

    ReleaseChannel(String displayName, String metadataUrl) {
        this.displayName = displayName;
        this.metadataUrl = metadataUrl;
    }

    public String displayName() { return displayName; }
    public String metadataUrl() { return metadataUrl; }
    public String sourceName() { return this == JGRPP ? "GitHub / JGRennison" : "openttd.org"; }

    @Override public String toString() { return displayName; }
}
