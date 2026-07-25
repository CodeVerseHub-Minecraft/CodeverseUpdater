package net.codeverse.updater;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The one class a plugin touches to keep itself updated.
 *
 * A plugin builds an {@link UpdaterConfig}, constructs this, and calls
 * {@link #check} on whatever scheduler it already has. Everything else,
 * talking to GitHub, comparing versions, verifying and staging, is behind this
 * surface. There is no background thread here on purpose: a plugin has its own
 * scheduler and its own idea of when the server is quiet, and owning a thread
 * pool inside a library is how a plugin ends up leaking one on reload.
 *
 * Typical use, in full:
 * <pre>{@code
 * Updater updater = new Updater(UpdaterConfig
 *         .forRepository("CodeVerseHub-Minecraft", "CodeverseAuth")
 *         .currentVersion(pluginVersion)
 *         .updateFolder(dataFolder.getParent().resolve("update"))
 *         .targetJarName("CodeverseAuth-" + pluginVersion + ".jar")
 *         .build());
 *
 * updater.checkAsync(serverExecutor).thenAccept(result -> {
 *     if (result instanceof UpdateResult.Staged staged) {
 *         logger.info("Update {} staged, restart to apply", staged.release().tag());
 *     }
 * });
 * }</pre>
 */
public final class Updater {

    private final UpdaterConfig config;
    private final GitHubReleases releases;
    private final UpdateStager stager;

    // Guards the rate limit. GitHub allows sixty unauthenticated requests an
    // hour per address, shared across every plugin on the host, so a caller
    // that schedules too tightly or an operator running a check command
    // repeatedly must not be able to spend that budget. The previous answer is
    // returned instead, which is what the caller would have got anyway.
    private volatile Instant lastCheckedAt;
    private volatile UpdateResult lastResult;

    public Updater(UpdaterConfig config) {
        this(config, new GitHubReleases(config.owner(), config.repo()));
    }

    Updater(UpdaterConfig config, GitHubReleases releases) {
        this.config = config;
        this.releases = releases;
        this.stager = new UpdateStager(releases.http(), config.updateFolder(), config.targetJarName());
    }

    /**
     * Checks once, blocking. Meant to be called from a plugin's own async
     * scheduler, never from a server thread: it makes a network request and
     * possibly a download, neither of which belongs on the main thread.
     *
     * Calls made within {@link UpdaterConfig#checkInterval()} of the last one
     * return that answer again without touching the network.
     */
    public UpdateResult check() {
        UpdateResult cached = lastResult;
        Instant last = lastCheckedAt;
        if (cached != null && last != null
                && Duration.between(last, Instant.now()).compareTo(config.checkInterval()) < 0) {
            return cached;
        }
        UpdateResult result = performCheck();
        lastCheckedAt = Instant.now();
        lastResult = result;
        return result;
    }

    /** Checks now, ignoring the interval. For an operator asking directly. */
    public UpdateResult checkNow() {
        UpdateResult result = performCheck();
        lastCheckedAt = Instant.now();
        lastResult = result;
        return result;
    }

    private UpdateResult performCheck() {
        Optional<Release> newer;
        try {
            newer = findNewer();
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new UpdateResult.Failed("Could not reach GitHub for "
                    + config.owner() + "/" + config.repo(), failure);
        }

        if (newer.isEmpty()) {
            return new UpdateResult.UpToDate(config.currentVersion());
        }
        Release release = newer.get();

        if (!config.autoApply()) {
            return new UpdateResult.UpdateAvailable(config.currentVersion(), release);
        }

        try {
            var stagedAt = stager.stage(release);
            return new UpdateResult.Staged(config.currentVersion(), release, stagedAt);
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new UpdateResult.Failed("Found " + release.tag()
                    + " but could not stage it", failure);
        }
    }

    /** {@link #check} on the given executor, so a plugin need not wrap it. */
    public CompletableFuture<UpdateResult> checkAsync(Executor executor) {
        return CompletableFuture.supplyAsync(this::check, executor);
    }

    /** {@link #checkAsync} that also hands the result to a callback. */
    public CompletableFuture<UpdateResult> checkAsync(Executor executor, Consumer<UpdateResult> onResult) {
        return checkAsync(executor).thenApply(result -> {
            onResult.accept(result);
            return result;
        });
    }

    /**
     * Stages a release that a previous check reported as available.
     *
     * The command path calls this after an operator asks to apply an update
     * that auto apply left staged. Verification still happens here, so an
     * operator command cannot stage an unverified jar any more than the
     * automatic path can.
     */
    public UpdateResult stage(Release release) {
        try {
            var stagedAt = stager.stage(release);
            return new UpdateResult.Staged(config.currentVersion(), release, stagedAt);
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new UpdateResult.Failed("Could not stage " + release.tag(), failure);
        }
    }

    /**
     * The newest release worth updating to, or empty when the running version
     * is current. A pre-release is only considered when the config opts into
     * them, and a release is only newer when its version strictly exceeds the
     * running one, so re-running a check after staging does not re-stage.
     */
    private Optional<Release> findNewer() throws IOException, InterruptedException {
        return releases.list().stream()
                .filter(release -> config.includePreReleases() || !release.isPreRelease())
                .filter(release -> release.jar().isPresent())
                .filter(release -> release.declaredSha256().isPresent())
                .filter(release -> release.version().isNewerThan(config.currentVersion()))
                .max((a, b) -> a.version().compareTo(b.version()));
    }

    public UpdaterConfig config() {
        return config;
    }
}
