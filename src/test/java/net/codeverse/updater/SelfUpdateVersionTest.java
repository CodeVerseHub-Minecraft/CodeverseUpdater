package net.codeverse.updater;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The version SelfUpdate reports is hardcoded, so it can drift from the build.
 * This reads the real project version, written to a resource at build time, and
 * fails the build when the two disagree. A release cannot then ship a library
 * that misreports its own version.
 */
class SelfUpdateVersionTest {

    @Test
    void hardcodedVersionMatchesTheBuild() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/build-version.txt")) {
            assertNotNull(in, "the build did not write the version resource");
            String buildVersion = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(buildVersion, SelfUpdate.version(),
                    "SelfUpdate.VERSION has drifted from the project version; update the constant");
        }
    }
}
