package com.openttd.launcher.service;

import com.openttd.launcher.model.ReleaseChannel;
import com.openttd.launcher.model.ReleaseInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseService {
    private static final Pattern ASSET = Pattern.compile(
            "https://cdn\\.openttd\\.org/(openttd-releases|openttd-nightlies)/(?:[^/]+/)*(openttd-[^/\\\"<>]+-(?:windows-win64|windows-arm64|linux-generic-amd64|linux-generic-arm64|macos-universal)\\.(?:zip|tar\\.xz))",
            Pattern.CASE_INSENSITIVE);
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public ReleaseInfo fetchLatest(ReleaseChannel channel) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(channel.metadataUrl()))
                .timeout(Duration.ofSeconds(30)).header("User-Agent", "OpenTTD-Launcher/1.0").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("OpenTTD returned HTTP " + response.statusCode());
        String assetSuffix = platformAssetSuffix();
        Matcher matcher = ASSET.matcher(response.body());
        URI download = null;
        String assetName = null;
        while (matcher.find()) {
            if (matcher.group(2).toLowerCase(java.util.Locale.ROOT).contains("-" + assetSuffix + ".")) {
                download = URI.create(matcher.group(0));
                assetName = matcher.group(2);
                break;
            }
        }
        if (download == null) throw new IOException("No official " + platformDisplayName() + " release archive was published on the page");
        String suffix = "-" + assetSuffix + (assetName.endsWith(".tar.xz") ? ".tar.xz" : ".zip");
        String version = assetName.substring("openttd-".length(), assetName.length() - suffix.length());
        return new ReleaseInfo(channel, version, download, channel.metadataUrl());
    }

    private static String platformAssetSuffix() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) return architecture.contains("aarch64") || architecture.contains("arm64") ? "windows-arm64" : "windows-win64";
        if (os.contains("mac") || os.contains("darwin")) return "macos-universal";
        if (os.contains("linux")) return architecture.contains("aarch64") || architecture.contains("arm64") ? "linux-generic-arm64" : "linux-generic-amd64";
        throw new IOException("Unsupported operating system: " + os);
    }

    private static String platformDisplayName() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) return "Windows";
        if (os.contains("mac") || os.contains("darwin")) return "macOS";
        if (os.contains("linux")) return "Linux";
        return os;
    }

    public HttpClient client() { return client; }
}
