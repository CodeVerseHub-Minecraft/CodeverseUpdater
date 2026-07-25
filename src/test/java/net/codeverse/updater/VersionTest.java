package net.codeverse.updater;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionTest {

    private static Version v(String s) {
        return Version.parse(s).orElseThrow();
    }

    @Test
    void parsesWithAndWithoutLeadingV() {
        assertEquals(v("1.2.3"), v("v1.2.3"),
                "a release tag carries a v and plugin metadata does not; they must compare equal");
    }

    @Test
    void rejectsNonVersions() {
        assertEquals(Optional.empty(), Version.parse("nightly"));
        assertEquals(Optional.empty(), Version.parse("1.2"));
        assertEquals(Optional.empty(), Version.parse(""));
        assertEquals(Optional.empty(), Version.parse(null));
    }

    @Test
    void ordersByCoreComponents() {
        assertTrue(v("2.0.0").isNewerThan(v("1.9.9")));
        assertTrue(v("1.10.0").isNewerThan(v("1.9.0")), "minor is numeric, not lexical");
        assertTrue(v("1.2.10").isNewerThan(v("1.2.9")));
        assertFalse(v("1.2.3").isNewerThan(v("1.2.3")));
    }

    @Test
    void aReleaseOutranksItsPreReleases() {
        assertTrue(v("1.2.0").isNewerThan(v("1.2.0-rc1")),
                "the final release is newer than any of its candidates");
        assertTrue(v("1.2.0-rc2").isNewerThan(v("1.2.0-rc1")));
        assertTrue(v("1.2.0").isNewerThan(v("1.2.0-alpha")));
    }

    @Test
    void preReleaseIsRecognised() {
        assertTrue(v("1.0.0-rc1").isPreRelease());
        assertFalse(v("1.0.0").isPreRelease());
    }

    @Test
    void overlargeComponentsAreTreatedAsUnparseable() {
        assertEquals(Optional.empty(), Version.parse("1.2.99999999999999999999"),
                "a number too large for a real version is not a version");
    }

    @Test
    void originalStringIsPreserved() {
        assertEquals("v1.2.3", v("v1.2.3").original());
    }
}
