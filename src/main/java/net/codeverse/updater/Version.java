package net.codeverse.updater;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version that can be compared to another to decide which is newer.
 *
 * Deliberately small. It understands the shape plugin versions actually take,
 * a major.minor.patch core with an optional pre-release suffix, and nothing
 * more, because a full semantic version parser would be more surface than the
 * problem needs. A leading "v" is tolerated because release tags carry one and
 * plugin metadata does not, and the two must compare equal.
 *
 * Pre-release ordering follows the one rule that matters here: a pre-release is
 * older than the release it precedes, so 1.2.0-rc1 is older than 1.2.0. Beyond
 * that pre-release identifiers compare lexically, which is enough to order
 * rc1 before rc2 without pretending to implement the full specification.
 */
public final class Version implements Comparable<Version> {

    private static final Pattern PATTERN = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+]([0-9A-Za-z.-]+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String original;

    private Version(int major, int minor, int patch, String preRelease, String original) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.original = original;
    }

    /**
     * Parses a version string, or empty when it is not one this understands.
     *
     * Returns empty rather than throwing so a malformed tag on a release,
     * which is outside this code's control, degrades to "cannot compare" and
     * is skipped rather than taking an update check down.
     */
    public static Optional<Version> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(4),
                    raw.trim()));
        } catch (NumberFormatException overflow) {
            // A version component larger than an int is not a real plugin
            // version; treat it as unparseable rather than crashing.
            return Optional.empty();
        }
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    public boolean isPreRelease() {
        return preRelease != null;
    }

    public String original() {
        return original;
    }

    @Override
    public int compareTo(Version other) {
        int core = Integer.compare(major, other.major);
        if (core != 0) {
            return core;
        }
        core = Integer.compare(minor, other.minor);
        if (core != 0) {
            return core;
        }
        core = Integer.compare(patch, other.patch);
        if (core != 0) {
            return core;
        }
        // Cores equal. A release outranks any pre-release of the same core, so
        // the absence of a pre-release suffix sorts highest.
        if (preRelease == null && other.preRelease == null) {
            return 0;
        }
        if (preRelease == null) {
            return 1;
        }
        if (other.preRelease == null) {
            return -1;
        }
        return preRelease.compareTo(other.preRelease);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Version other)) {
            return false;
        }
        return major == other.major && minor == other.minor && patch == other.patch
                && Objects.equals(preRelease, other.preRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        return original;
    }
}
