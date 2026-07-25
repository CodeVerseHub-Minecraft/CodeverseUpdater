package net.codeverse.updater;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the releases client and the updater against a real in process server,
 * so the HttpClient path, the JSON parsing and the checksum discovery are all
 * exercised together rather than mocked apart.
 */
class GitHubReleasesTest {

    private static final byte[] JAR = "pretend this is a compiled plugin jar".getBytes(StandardCharsets.UTF_8);

    private static String sha256(byte[] data) throws Exception {
        return Checksums.sha256(new ByteArrayInputStream(data));
    }

    private String releasesJson(String base, String tag, boolean prerelease, boolean withChecksumAsset,
                                String bodyChecksumLine) throws Exception {
        String hash = sha256(JAR);
        StringBuilder assets = new StringBuilder();
        assets.append("{\"name\":\"Plugin-").append(tag).append(".jar\",\"browser_download_url\":\"")
                .append(base).append("/dl/plugin.jar\",\"size\":").append(JAR.length).append("}");
        if (withChecksumAsset) {
            assets.append(",{\"name\":\"Plugin-").append(tag).append(".jar.sha256\",\"browser_download_url\":\"")
                    .append(base).append("/dl/plugin.jar.sha256\",\"size\":64}");
        }
        String body = bodyChecksumLine == null ? "" : bodyChecksumLine.replace("HASH", hash);
        return "[{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":" + prerelease
                + ",\"body\":\"" + body + "\",\"assets\":[" + assets + "]}]";
    }

    @Test
    void findsAReleaseWithASidecarChecksum() throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    releasesJson(base, "v1.0.0", false, true, null));
            github.route("/dl/plugin.jar.sha256", sha256(JAR) + "  Plugin-v1.0.0.jar");
            github.route("/dl/plugin.jar", JAR);

            GitHubReleases releases = new GitHubReleases("acme", "plugin", github.client(), base + "/repos/");
            Optional<Release> latest = releases.latest();

            assertTrue(latest.isPresent());
            assertEquals("v1.0.0", latest.get().tag());
            assertTrue(latest.get().declaredSha256().isPresent(), "the sidecar checksum was discovered");
            assertEquals(sha256(JAR), latest.get().declaredSha256().get());
        }
    }

    @Test
    void findsAChecksumInTheReleaseBody() throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    releasesJson(base, "v1.0.0", false, false, "HASH  Plugin-v1.0.0.jar"));

            GitHubReleases releases = new GitHubReleases("acme", "plugin", github.client(), base + "/repos/");
            Release release = releases.latest().orElseThrow();

            assertTrue(release.declaredSha256().isPresent(), "the body checksum line was read");
            assertEquals(sha256(JAR), release.declaredSha256().get());
        }
    }

    @Test
    void draftsAreIgnored() throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    "[{\"tag_name\":\"v2.0.0\",\"draft\":true,\"prerelease\":false,\"body\":\"\",\"assets\":[]},"
                            + "{\"tag_name\":\"v1.0.0\",\"draft\":false,\"prerelease\":false,\"body\":\"HASH\","
                            .replace("HASH", sha256(JAR))
                            + "\"assets\":[{\"name\":\"p.jar\",\"browser_download_url\":\"" + base
                            + "/dl\",\"size\":0}]}]");

            GitHubReleases releases = new GitHubReleases("acme", "plugin", github.client(), base + "/repos/");
            Release release = releases.latest().orElseThrow();
            assertEquals("v1.0.0", release.tag(), "the draft v2 is skipped for the published v1");
        }
    }

    @Test
    void unparseableTagsAreSkippedNotFatal() throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    "[{\"tag_name\":\"nightly\",\"draft\":false,\"prerelease\":false,\"body\":\"\",\"assets\":[]},"
                            + "{\"tag_name\":\"v1.0.0\",\"draft\":false,\"prerelease\":false,\"body\":\"" + sha256(JAR)
                            + "\",\"assets\":[{\"name\":\"p.jar\",\"browser_download_url\":\"" + base
                            + "/dl\",\"size\":0}]}]");

            GitHubReleases releases = new GitHubReleases("acme", "plugin", github.client(), base + "/repos/");
            List<Release> all = releases.list();
            assertEquals(1, all.size(), "the nightly tag is skipped, the real release remains");
        }
    }

    @Test
    void updaterReportsUpToDateWhenCurrentIsNewest(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    releasesJson(base, "v1.0.0", false, false, "HASH  Plugin-v1.0.0.jar"));

            Updater updater = new Updater(
                    UpdaterConfig.forRepository("acme", "plugin")
                            .currentVersion("1.0.0")
                            .updateFolder(tmp)
                            .build(),
                    new GitHubReleases("acme", "plugin", github.client(), base + "/repos/"));

            assertInstanceOf(UpdateResult.UpToDate.class, updater.check());
        }
    }

    @Test
    void updaterReportsAvailableButDoesNotStageWhenAutoApplyIsOff(
            @org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    releasesJson(base, "v2.0.0", false, false, "HASH  Plugin-v2.0.0.jar"));

            Updater updater = new Updater(
                    UpdaterConfig.forRepository("acme", "plugin")
                            .currentVersion("1.0.0")
                            .updateFolder(tmp)
                            .build(),
                    new GitHubReleases("acme", "plugin", github.client(), base + "/repos/"));

            UpdateResult result = updater.check();
            assertInstanceOf(UpdateResult.UpdateAvailable.class, result);
            assertTrue(Files.list(tmp).findAny().isEmpty(), "nothing is staged when auto apply is off");
        }
    }

    @Test
    void updaterStagesAVerifiedJarWhenAutoApplyIsOn(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    releasesJson(base, "v2.0.0", false, true, null));
            github.route("/dl/plugin.jar.sha256", sha256(JAR));
            github.route("/dl/plugin.jar", JAR);

            Updater updater = new Updater(
                    UpdaterConfig.forRepository("acme", "plugin")
                            .currentVersion("1.0.0")
                            .updateFolder(tmp)
                            .targetJarName("Plugin.jar")
                            .autoApply(true)
                            .build(),
                    new GitHubReleases("acme", "plugin", github.client(), base + "/repos/"));

            UpdateResult result = updater.check();
            UpdateResult.Staged staged = assertInstanceOf(UpdateResult.Staged.class, result);
            assertEquals(tmp.resolve("Plugin.jar"), staged.stagedAt());
            assertTrue(Files.exists(tmp.resolve("Plugin.jar")), "the verified jar is in place for restart");
            assertEquals(sha256(JAR), sha256(Files.readAllBytes(tmp.resolve("Plugin.jar"))));
        }
    }

    @Test
    void aTamperedDownloadIsRefusedAndNothingIsStaged(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            // The listing advertises the honest checksum, but the download
            // route serves a different jar: the tampering case.
            github.route("/repos/acme/plugin/releases",
                    releasesJson(base, "v2.0.0", false, true, null));
            github.route("/dl/plugin.jar.sha256", sha256(JAR));
            github.route("/dl/plugin.jar", "this is not the jar you verified".getBytes(StandardCharsets.UTF_8));

            Updater updater = new Updater(
                    UpdaterConfig.forRepository("acme", "plugin")
                            .currentVersion("1.0.0")
                            .updateFolder(tmp)
                            .targetJarName("Plugin.jar")
                            .autoApply(true)
                            .build(),
                    new GitHubReleases("acme", "plugin", github.client(), base + "/repos/"));

            UpdateResult result = updater.check();
            assertInstanceOf(UpdateResult.Failed.class, result);
            assertFalse(Files.exists(tmp.resolve("Plugin.jar")),
                    "a jar that fails verification must never be staged");
            assertTrue(Files.list(tmp).filter(p -> p.toString().endsWith(".part")).findAny().isEmpty(),
                    "no partial download is left behind");
        }
    }

    @Test
    void preReleasesAreExcludedUnlessOptedIn(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/acme/plugin/releases",
                    releasesJson(base, "v2.0.0-rc1", true, false, "HASH  Plugin-v2.0.0-rc1.jar"));

            UpdaterConfig.Builder common = UpdaterConfig.forRepository("acme", "plugin")
                    .currentVersion("1.0.0")
                    .updateFolder(tmp);

            Updater excluding = new Updater(common.build(),
                    new GitHubReleases("acme", "plugin", github.client(), base + "/repos/"));
            assertInstanceOf(UpdateResult.UpToDate.class, excluding.check(),
                    "a pre-release is ignored by default");

            Updater including = new Updater(common.includePreReleases(true).build(),
                    new GitHubReleases("acme", "plugin", github.client(), base + "/repos/"));
            assertInstanceOf(UpdateResult.UpdateAvailable.class, including.check(),
                    "opting in surfaces the pre-release");
        }
    }
}
