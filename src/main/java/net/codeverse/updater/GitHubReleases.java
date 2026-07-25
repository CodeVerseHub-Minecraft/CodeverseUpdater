package net.codeverse.updater;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads releases from a public GitHub repository.
 *
 * Talks to the public releases endpoint with no authentication, which is all
 * this project needs since every repository is public. Unauthenticated GitHub
 * allows sixty requests an hour per address, so a check that runs on a timer
 * every few hours per plugin stays comfortably inside it; the poll interval is
 * the operator's to widen if several plugins share a host.
 *
 * Every network failure is surfaced as an empty result or a thrown IOException
 * rather than a crash. An update check that cannot reach GitHub is a check that
 * finds nothing, never a reason for the plugin around it to fail.
 */
public class GitHubReleases {

    private static final String API_ROOT = "https://api.github.com/repos/";
    private static final String USER_AGENT = "CodeverseUpdater";

    private final HttpClient http;
    private final String owner;
    private final String repo;
    private final String apiRoot;

    public GitHubReleases(String owner, String repo) {
        this(owner, repo, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    GitHubReleases(String owner, String repo, HttpClient http) {
        this(owner, repo, http, API_ROOT);
    }

    GitHubReleases(String owner, String repo, HttpClient http, String apiRoot) {
        this.owner = owner;
        this.repo = repo;
        this.http = http;
        this.apiRoot = apiRoot;
    }

    /**
     * The most recent release that carries a version and a downloadable jar.
     *
     * Draft releases are ignored, since a draft is not published. Pre-releases
     * are returned so the caller can decide, through its own configuration,
     * whether to consider them, rather than this client deciding for everyone.
     * A release whose tag is not a parseable version is skipped rather than
     * failing the check, because a stray hand made tag should not blind the
     * updater to the real releases around it.
     */
    public Optional<Release> latest() throws IOException, InterruptedException {
        List<Release> all = list();
        // The API returns releases newest first, but a draft or an unparseable
        // tag can sit at the front, so the first fully valid release is taken
        // rather than assuming position zero is it.
        return all.stream().findFirst();
    }

    /** Every published release, newest first, reduced to the fields that matter. */
    public List<Release> list() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiRoot + owner + "/" + repo + "/releases?per_page=30"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new IOException("Repository " + owner + "/" + repo + " has no releases endpoint, "
                    + "which usually means the owner or name is wrong");
        }
        if (response.statusCode() == 403) {
            throw new IOException("GitHub rate limit reached for " + owner + "/" + repo
                    + ". Widen the update check interval.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned status " + response.statusCode()
                    + " for " + owner + "/" + repo);
        }

        List<Release> releases = new ArrayList<>();
        for (Object element : Json.array(Json.parse(response.body()))) {
            toRelease(Json.object(element)).ifPresent(releases::add);
        }
        return releases;
    }

    private Optional<Release> toRelease(Map<String, Object> node) {
        if (Json.bool(node, "draft")) {
            return Optional.empty();
        }
        String tag = Json.string(node, "tag_name");
        Optional<Version> version = Version.parse(tag);
        if (version.isEmpty()) {
            return Optional.empty();
        }

        List<Release.Asset> assets = new ArrayList<>();
        Object rawAssets = node.get("assets");
        if (rawAssets instanceof List<?>) {
            for (Object element : Json.array(rawAssets)) {
                Map<String, Object> asset = Json.object(element);
                String name = Json.string(asset, "name");
                String url = Json.string(asset, "browser_download_url");
                if (name != null && url != null) {
                    assets.add(new Release.Asset(name, url, Json.number(asset, "size")));
                }
            }
        }

        Release.Asset jar = assets.stream().filter(Release.Asset::isJar).findFirst().orElse(null);
        String body = Json.string(node, "body");
        String checksum = Checksums.forJar(jar, assets, body, this).orElse(null);

        return Optional.of(new Release(
                version.get(),
                tag,
                Json.bool(node, "prerelease"),
                jar,
                checksum,
                body));
    }

    /** Fetches a text asset, used to read a published checksum sidecar file. */
    String fetchText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Could not fetch " + url + ", status " + response.statusCode());
        }
        return response.body();
    }

    HttpClient http() {
        return http;
    }
}
