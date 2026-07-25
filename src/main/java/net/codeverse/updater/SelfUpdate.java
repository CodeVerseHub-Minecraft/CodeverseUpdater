package net.codeverse.updater;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lets the updater notice when a newer version of itself has been published.
 *
 * A library shaded into a plugin cannot replace itself: there is no separate
 * jar on disk to swap, only classes merged into the host plugin's jar. So
 * self update here does not mean hot swapping. It means the updater knows its
 * own version, checks its own repository, and when a newer library exists it
 * says so, naming the fix, which is for the host plugin to be rebuilt against
 * the newer version and released.
 *
 * This is deliberately honest rather than convenient. Pretending a library
 * could rewrite itself under a running plugin would be a lie that failed at the
 * worst time; telling the operator the truth, that their bundled updater is a
 * version behind and a rebuild is due, is the actionable thing.
 */
public final class SelfUpdate {

    private static final String OWNER = "CodeVerseHub-Minecraft";
    private static final String REPO = "CodeverseUpdater";

    // Kept in step with the project version by hand, and asserted against the
    // build by a test, so a release cannot ship a library that misreports its
    // own version.
    private static final String VERSION = "0.1.4";

    private final GitHubReleases releases;

    public SelfUpdate() {
        this(new GitHubReleases(OWNER, REPO));
    }

    SelfUpdate(GitHubReleases releases) {
        this.releases = releases;
    }

    /** The version of this library, as a string. */
    public static String version() {
        return VERSION;
    }

    /** The version of this library, parsed. */
    public static Version currentVersion() {
        return Version.parse(VERSION).orElseThrow(
                () -> new IllegalStateException("The library's own version " + VERSION + " does not parse"));
    }

    /**
     * The newest published updater, if it is newer than this one.
     *
     * Empty means the bundled library is current. A present value is a signal
     * to the operator that plugins depending on this updater are due a rebuild,
     * not something this library can act on itself.
     */
    public Optional<Version> newerAvailable() {
        try {
            return releases.list().stream()
                    .filter(release -> !release.isPreRelease())
                    .map(Release::version)
                    .filter(version -> version.isNewerThan(currentVersion()))
                    .max(Version::compareTo);
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // A self check that cannot reach GitHub reports "nothing newer",
            // because a staleness warning is a convenience and must never be
            // the thing that surfaces a network error to the host plugin.
            return Optional.empty();
        }
    }

    public CompletableFuture<Optional<Version>> newerAvailableAsync(Executor executor) {
        return CompletableFuture.supplyAsync(this::newerAvailable, executor);
    }
}
