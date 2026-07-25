package net.codeverse.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterConfigTest {

    @Test
    void requiresVersionAndFolder(@TempDir Path tmp) {
        assertThrows(NullPointerException.class,
                () -> UpdaterConfig.forRepository("a", "b").updateFolder(tmp).build());
        assertThrows(NullPointerException.class,
                () -> UpdaterConfig.forRepository("a", "b").currentVersion("1.0.0").build());
    }

    @Test
    void rejectsAnUnparseableCurrentVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdaterConfig.forRepository("a", "b").currentVersion("nightly"));
    }

    @Test
    void defaultsAreTheSafeChoices(@TempDir Path tmp) {
        UpdaterConfig config = UpdaterConfig.forRepository("a", "plugin")
                .currentVersion("1.0.0")
                .updateFolder(tmp)
                .build();
        assertFalse(config.autoApply(), "staging, not auto applying, is the default");
        assertFalse(config.includePreReleases(), "stable releases only by default");
        assertEquals("plugin.jar", config.targetJarName(), "the jar name defaults to the repo name");
    }

    @Test
    void checkIntervalIsFlooredToProtectTheRateLimit(@TempDir Path tmp) {
        UpdaterConfig config = UpdaterConfig.forRepository("a", "b")
                .currentVersion("1.0.0")
                .updateFolder(tmp)
                .checkInterval(Duration.ofSeconds(30))
                .build();
        assertEquals(Duration.ofMinutes(15), config.checkInterval(),
                "too short an interval is raised to the floor rather than accepted");
    }

    @Test
    void aReasonableIntervalIsKept(@TempDir Path tmp) {
        UpdaterConfig config = UpdaterConfig.forRepository("a", "b")
                .currentVersion("1.0.0")
                .updateFolder(tmp)
                .checkInterval(Duration.ofHours(12))
                .build();
        assertEquals(Duration.ofHours(12), config.checkInterval());
    }
}

class SelfUpdateTest {

    @Test
    void reportsItsOwnVersion() {
        assertTrue(Version.parse(SelfUpdate.version()).isPresent(),
                "the library's own version must be a parseable version");
        assertEquals(SelfUpdate.version(), SelfUpdate.currentVersion().original());
    }
}

class UpdaterThrottleTest {

    private static final byte[] JAR = "jar".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /**
     * The interval has to actually prevent network calls, or it is decoration.
     * Sixty unauthenticated GitHub requests an hour are shared by every plugin
     * on the host, so a caller that schedules tightly must not be able to spend
     * that budget.
     */
    @Test
    void repeatedChecksInsideTheIntervalDoNotHitTheNetwork(@TempDir Path tmp) throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            String hash = Checksums.sha256(new java.io.ByteArrayInputStream(JAR));
            github.route("/repos/a/b/releases",
                    "[{\"tag_name\":\"v9.0.0\",\"draft\":false,\"prerelease\":false,\"body\":\"" + hash
                            + "  p.jar\",\"assets\":[{\"name\":\"p.jar\",\"browser_download_url\":\""
                            + base + "/dl\",\"size\":0}]}]");

            CountingGitHub counting = new CountingGitHub("a", "b", github.client(), base + "/repos/");
            Updater updater = new Updater(
                    UpdaterConfig.forRepository("a", "b")
                            .currentVersion("1.0.0")
                            .updateFolder(tmp)
                            .checkInterval(Duration.ofHours(6))
                            .build(),
                    counting);

            updater.check();
            updater.check();
            updater.check();

            assertEquals(1, counting.calls, "only the first check may reach GitHub");
        }
    }

    @Test
    void checkNowBypassesTheInterval(@TempDir Path tmp) throws Exception {
        try (FakeGitHub github = new FakeGitHub()) {
            String base = github.base();
            github.route("/repos/a/b/releases", "[]");
            CountingGitHub counting = new CountingGitHub("a", "b", github.client(), base + "/repos/");
            Updater updater = new Updater(
                    UpdaterConfig.forRepository("a", "b")
                            .currentVersion("1.0.0")
                            .updateFolder(tmp)
                            .checkInterval(Duration.ofHours(6))
                            .build(),
                    counting);

            updater.check();
            updater.checkNow();

            assertEquals(2, counting.calls, "an operator asking directly is answered directly");
        }
    }

    private static final class CountingGitHub extends GitHubReleases {
        int calls;

        CountingGitHub(String owner, String repo, java.net.http.HttpClient http, String apiRoot) {
            super(owner, repo, http, apiRoot);
        }

        @Override
        public java.util.List<Release> list() throws java.io.IOException, InterruptedException {
            calls++;
            return super.list();
        }
    }
}
