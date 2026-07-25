package net.codeverse.updater;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The outcome of an update check, as something the host plugin can react to
 * without catching exceptions for the ordinary cases.
 *
 * Sealed so a caller can switch over every outcome and be told by the compiler
 * when a new one appears. The distinction between "up to date", "an update is
 * available but was not staged" and "an update was staged" is the whole point:
 * a plugin logs each differently, and a staged update is the only one that
 * asks the operator to restart.
 */
public sealed interface UpdateResult {

    /** The running version is the newest published. */
    record UpToDate(Version current) implements UpdateResult {
    }

    /**
     * A newer release exists but was not staged, because auto apply is off.
     * The host plugin typically logs this and lets staff stage it on command.
     */
    record UpdateAvailable(Version current, Release release) implements UpdateResult {
    }

    /** A newer release was downloaded, verified and staged for the next restart. */
    record Staged(Version current, Release release, Path stagedAt) implements UpdateResult {
    }

    /**
     * The check could not complete. Carries the cause for logging. This is a
     * result rather than a thrown exception so a failed check is as easy to
     * handle as any other outcome, since a network blip is expected rather
     * than exceptional.
     */
    record Failed(String reason, Throwable cause) implements UpdateResult {
    }

    default Optional<Release> pendingRelease() {
        if (this instanceof UpdateAvailable available) {
            return Optional.of(available.release());
        }
        if (this instanceof Staged staged) {
            return Optional.of(staged.release());
        }
        return Optional.empty();
    }

    default boolean updatePending() {
        return this instanceof UpdateAvailable || this instanceof Staged;
    }
}
