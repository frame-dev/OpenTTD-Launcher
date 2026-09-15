package com.openttd.launcher.service;

import com.openttd.launcher.model.ReleaseChannel;
import com.openttd.launcher.model.ReleaseInfo;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseService {
    public record HistoryPage(java.util.List<ReleaseInfo> releases, boolean hasMore) {}
    private static final String CDN = "https://cdn.openttd.org/";

    private String read(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "OpenTTD-Launcher/1.0").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Release source returned HTTP " + response.statusCode() + ". Please try again later.");
        return response.body();
    }

    public HistoryPage fetchHistory(ReleaseChannel channel, int page) throws IOException, InterruptedException {
        if (page < 0) throw new IllegalArgumentException("Invalid history page");
        if (channel == ReleaseChannel.JGRPP) {
            var entries = JsonParser.parseString(read("https://api.github.com/repos/JGRennison/OpenTTD-patches/releases?per_page=30&page=" + (page + 1))).getAsJsonArray();
            var releases = new java.util.ArrayList<ReleaseInfo>();
            for (var entry : entries) {
                var object = entry.getAsJsonObject();
                if (object.has("draft") && object.get("draft").getAsBoolean()) continue;
                try { releases.add(parseJgrRelease(entry.toString(), platformAssetSuffix())); }
                catch (IOException unsupported) { /* Older releases may not have a compatible archive. */ }
            }
            return new HistoryPage(releases, entries.size() == 30);
        }
        String base = CDN + (channel == ReleaseChannel.NIGHTLY ? "openttd-nightlies/" : "openttd-releases/");
        if (channel == ReleaseChannel.NIGHTLY) {
            var years = directories(read(base)).stream().filter(v -> v.matches("[0-9]{4}"))
                    .sorted(java.util.Comparator.reverseOrder()).toList();
            if (page >= years.size()) return new HistoryPage(java.util.List.of(), false);
            base += years.get(page) + "/";
            return new HistoryPage(parseOfficialHistory(read(base), channel, base), page + 1 < years.size());
        }
        return new HistoryPage(parseOfficialHistory(read(base), channel, base), false);
    }

    static java.util.List<String> directories(String html) {
        var result = new java.util.LinkedHashSet<String>();
        var matcher = Pattern.compile("href=\"([A-Za-z0-9][A-Za-z0-9._+-]*)/\"").matcher(html);
        while (matcher.find()) result.add(matcher.group(1));
        return java.util.List.copyOf(result);
    }

    static java.util.List<ReleaseInfo> parseOfficialHistory(String html, ReleaseChannel channel, String base) {
        return directories(html).stream().filter(version -> switch (channel) {
            case STABLE -> version.matches("[0-9]+(?:\\.[0-9]+)+");
            case TESTING -> version.matches("(?i)[0-9]+(?:\\.[0-9]+)+-(?:beta|RC)[0-9]+");
            case NIGHTLY -> version.matches("(?:[0-9]{8}-[A-Za-z0-9._+-]+|r[0-9]+)");
            default -> false;
        }).sorted((a, b) -> InstallService.compareVersions(b, a))
                .map(version -> new ReleaseInfo(channel, version, null, base + version + "/")).toList();
    }

    public ReleaseInfo resolveDownload(ReleaseInfo release) throws IOException, InterruptedException {
        if (release.downloadUri() != null) return release;
        if (release.pageUri() == null) throw new IOException("Load older versions to find a download for this installed version.");
        return parseArchive(read(release.pageUri()), release, platformAssetSuffix());
    }

    static ReleaseInfo parseArchive(String html, ReleaseInfo release, String platform) throws IOException {
        var matcher = Pattern.compile("href=\"([^\"]+)\"").matcher(html);
        String prefix = "openttd-" + release.version() + "-";
        String best = null; int rank = Integer.MAX_VALUE;
        while (matcher.find()) {
            String name = matcher.group(1);
            int candidate = assetRank(name, prefix, platform);
            if (candidate < rank) { best = name; rank = candidate; }
        }
        if (best != null) return new ReleaseInfo(release.channel(), release.version(), URI.create(release.pageUri()).resolve(best), release.pageUri());
        throw new IOException("No compatible archive was published for " + platform + " in release " + release.version());
    }
    private static final Pattern ASSET = Pattern.compile(
            "https://cdn\\.openttd\\.org/(openttd-releases|openttd-nightlies)/(?:[^/]+/)*(openttd-[^/\\\"<>]+-(?:windows-win64|windows-win32|windows-arm64|linux-generic-amd64|linux-generic-arm64|macos-universal|macosx-universal|macosx)\\.(?:zip|tar\\.xz|dmg))",
            Pattern.CASE_INSENSITIVE);
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public ReleaseInfo fetchLatest(ReleaseChannel channel) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(channel.metadataUrl()))
                .timeout(Duration.ofSeconds(30)).header("User-Agent", "OpenTTD-Launcher/1.0").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException(channel.sourceName() + " returned HTTP " + response.statusCode()
                + (channel == ReleaseChannel.JGRPP && (response.statusCode() == 403 || response.statusCode() == 429)
                ? ". GitHub may be rate limiting requests; try again later." : ""));
        String assetSuffix = platformAssetSuffix();
        if (channel == ReleaseChannel.JGRPP) return parseJgrRelease(response.body(), assetSuffix);
        Matcher matcher = ASSET.matcher(response.body());
        ReleaseInfo best = null; int rank = Integer.MAX_VALUE;
        while (matcher.find()) {
            String name = matcher.group(2);
            for (String variant : platformVariants(assetSuffix)) {
                for (String extension : extensions(assetSuffix)) {
                    String suffix = "-" + variant + extension;
                    if (!name.endsWith(suffix)) continue;
                    String version = name.substring("openttd-".length(), name.length() - suffix.length());
                    int candidate = assetRank(name, "openttd-" + version + "-", assetSuffix);
                    if (candidate < rank) { best = new ReleaseInfo(channel, version, URI.create(matcher.group(0)), channel.metadataUrl()); rank = candidate; }
                }
            }
        }
        if (best == null) throw new IOException("No compatible " + assetSuffix + " archive was published on the release page");
        return best;
    }

    static ReleaseInfo parseJgrRelease(String body, String platform) throws IOException {
        try {
            var release = JsonParser.parseString(body).getAsJsonObject();
            String tag = release.get("tag_name").getAsString();
            if (!tag.matches("jgrpp-[0-9][A-Za-z0-9._+-]*")) throw new IOException("Unrecognized JGR release tag: " + tag);
            String version = tag.substring("jgrpp-".length());
            String prefix = "openttd-jgrpp-" + version + "-";
            ReleaseInfo best = null; int rank = Integer.MAX_VALUE;
            for (var element : release.getAsJsonArray("assets")) {
                var asset = element.getAsJsonObject();
                String name = asset.get("name").getAsString();
                int candidate = assetRank(name, prefix, platform);
                if (candidate >= rank) continue;
                URI download = URI.create(asset.get("browser_download_url").getAsString());
                String expected = "https://github.com/JGRennison/OpenTTD-patches/releases/download/" + tag + "/" + name;
                if (!download.toString().equals(expected)) throw new IOException("Unexpected JGR download address");
                rank = candidate;
                best = new ReleaseInfo(ReleaseChannel.JGRPP, version, download,
                        "https://github.com/JGRennison/OpenTTD-patches/releases/tag/" + tag);
            }
            if (best != null) return best;
            throw new IOException("JGR Patch Pack has no supported archive download for " + platform
                    + ". Check github.com/JGRennison/OpenTTD-patches/releases for manual installation options.");
        } catch (RuntimeException malformed) {
            throw new IOException("Could not read JGR release metadata from GitHub", malformed);
        }
    }

    private static java.util.List<String> platformVariants(String platform) {
        return switch (platform) {
            case "macos-universal" -> java.util.List.of("macos-universal", "macosx-universal", "macosx");
            case "windows-win64" -> java.util.List.of("windows-win64", "windows-win32");
            default -> java.util.List.of(platform);
        };
    }

    private static java.util.List<String> extensions(String platform) {
        return platform.startsWith("macos") ? java.util.List.of(".dmg", ".zip")
                : platform.startsWith("windows") ? java.util.List.of(".zip") : java.util.List.of(".tar.xz", ".zip");
    }

    private static int assetRank(String name, String prefix, String platform) {
        int rank = 0;
        for (String variant : platformVariants(platform)) {
            for (String extension : extensions(platform)) {
                if (name.equals(prefix + variant + extension)) return rank;
                rank++;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String platformAssetSuffix() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        if (os.equals("darwin")) return "macos-universal";
        if (os.contains("win")) return architecture.contains("aarch64") || architecture.contains("arm64") ? "windows-arm64"
                : architecture.equals("x86") || architecture.matches("i[3-6]86") ? "windows-win32" : "windows-win64";
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
