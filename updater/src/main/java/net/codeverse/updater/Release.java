package net.codeverse.updater;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A published release, reduced to what an update decision needs.
 *
 * The GitHub API returns far more than this. Keeping the model to the version,
 * the downloadable jar and the checksum means the parsing surface is small and
 * the rest of the library never sees a raw API shape it might come to depend
 * on.
 */
public final class Release {

    private final Version version;
    private final String tag;
    private final boolean preRelease;
    private final Asset jar;
    private final String declaredSha256;
    private final String body;

    public Release(Version version, String tag, boolean preRelease, Asset jar, String declaredSha256, String body) {
        this.version = Objects.requireNonNull(version, "version");
        this.tag = Objects.requireNonNull(tag, "tag");
        this.preRelease = preRelease;
        this.jar = jar;
        this.declaredSha256 = declaredSha256;
        this.body = body == null ? "" : body;
    }

    public Version version() {
        return version;
    }

    public String tag() {
        return tag;
    }

    public boolean isPreRelease() {
        return preRelease;
    }

    /** The jar asset to download, empty when the release published none. */
    public Optional<Asset> jar() {
        return Optional.ofNullable(jar);
    }

    /**
     * The expected SHA-256 of the jar, empty when none was published.
     *
     * A release with a jar but no checksum is not treated as updatable, so the
     * absence here is what makes verification mandatory rather than optional:
     * there is no path that downloads a jar this library cannot check.
     */
    public Optional<String> declaredSha256() {
        return Optional.ofNullable(declaredSha256).filter(value -> !value.isBlank());
    }

    public String body() {
        return body;
    }

    /** A single downloadable asset attached to a release. */
    public record Asset(String name, String downloadUrl, long size) {

        public Asset {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(downloadUrl, "downloadUrl");
        }

        public boolean isJar() {
            return name.toLowerCase().endsWith(".jar");
        }

        public boolean isChecksum() {
            String lower = name.toLowerCase();
            return lower.endsWith(".sha256") || lower.endsWith(".sha256sum");
        }
    }

    /** Everything a release exposed, before it was reduced to the fields above. */
    public record Raw(String tag, boolean preRelease, boolean draft, List<Asset> assets, String body) {
    }
}
