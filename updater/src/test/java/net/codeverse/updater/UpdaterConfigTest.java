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
