package net.codeverse.updater;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Everything the updater needs, given once when a plugin sets it up.
 *
 * Built through a builder so a call site reads as a short list of named
 * choices rather than a row of positional arguments whose meaning depends on
 * their order. Only the repository, current version and update folder are
 * required; the rest have defaults chosen to be the safe option, so a plugin
 * that sets nothing else gets staged, verified, opt in behaviour.
 */
public final class UpdaterConfig {

    private final String owner;
    private final String repo;
    private final Version currentVersion;
    private final Path updateFolder;
    private final String targetJarName;
    private final boolean includePreReleases;
    private final boolean autoApply;
    private final Duration checkInterval;

    private UpdaterConfig(Builder builder) {
        this.owner = builder.owner;
        this.repo = builder.repo;
        this.currentVersion = builder.currentVersion;
        this.updateFolder = builder.updateFolder;
        this.targetJarName = builder.targetJarName;
        this.includePreReleases = builder.includePreReleases;
        this.autoApply = builder.autoApply;
        this.checkInterval = builder.checkInterval;
    }

    public static Builder forRepository(String owner, String repo) {
        return new Builder(owner, repo);
    }

    public String owner() {
        return owner;
    }

    public String repo() {
        return repo;
    }

    public Version currentVersion() {
        return currentVersion;
    }

    public Path updateFolder() {
        return updateFolder;
    }

    public String targetJarName() {
        return targetJarName;
    }

    public boolean includePreReleases() {
        return includePreReleases;
    }

    /**
     * Whether a verified update is staged automatically when found.
     *
     * False by default, and the default is a security decision rather than a
     * conservative habit. Auto staging the plugin that guards every account
     * turns a compromised release token into code that runs on the next
     * restart with no human in the loop. A plugin whose compromise is less
     * total, a minigame say, can reasonably turn this on.
     */
    public boolean autoApply() {
        return autoApply;
    }

    public Duration checkInterval() {
        return checkInterval;
    }

    public static final class Builder {

        private final String owner;
        private final String repo;
        private Version currentVersion;
        private Path updateFolder;
        private String targetJarName;
        private boolean includePreReleases = false;
        private boolean autoApply = false;
        private Duration checkInterval = Duration.ofHours(6);

        private Builder(String owner, String repo) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.repo = Objects.requireNonNull(repo, "repo");
        }

        /** The running version, usually the plugin's own reported version string. */
        public Builder currentVersion(String version) {
            this.currentVersion = Version.parse(version).orElseThrow(
                    () -> new IllegalArgumentException("Not a parseable version: " + version));
            return this;
        }

        /** Where verified jars are staged, usually the platform's plugins/update folder. */
        public Builder updateFolder(Path updateFolder) {
            this.updateFolder = Objects.requireNonNull(updateFolder, "updateFolder");
            return this;
        }

        /**
         * The name the staged jar must take to replace the running one. When
         * unset it defaults to the repository name with a .jar suffix, which
         * is only correct if that matches the installed jar, so a plugin whose
         * jar is named differently should set this explicitly.
         */
        public Builder targetJarName(String targetJarName) {
            this.targetJarName = targetJarName;
            return this;
        }

        public Builder includePreReleases(boolean includePreReleases) {
            this.includePreReleases = includePreReleases;
            return this;
        }

        public Builder autoApply(boolean autoApply) {
            this.autoApply = autoApply;
            return this;
        }

        /**
         * How often the background checker polls. Floored at fifteen minutes,
         * because unauthenticated GitHub allows sixty requests an hour per
         * address and a shorter interval across several plugins on one host
         * would spend that budget on nothing a human could act on faster.
         */
        public Builder checkInterval(Duration checkInterval) {
            if (checkInterval != null && checkInterval.compareTo(Duration.ofMinutes(15)) >= 0) {
                this.checkInterval = checkInterval;
            } else {
                this.checkInterval = Duration.ofMinutes(15);
            }
            return this;
        }

        public UpdaterConfig build() {
            Objects.requireNonNull(currentVersion, "currentVersion is required");
            Objects.requireNonNull(updateFolder, "updateFolder is required");
            if (targetJarName == null || targetJarName.isBlank()) {
                targetJarName = repo + ".jar";
            }
            return new UpdaterConfig(this);
        }
    }
}
