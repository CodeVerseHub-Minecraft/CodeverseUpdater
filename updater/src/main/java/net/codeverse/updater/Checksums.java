package net.codeverse.updater;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the checksum a release published for its jar, and verifies against it.
 *
 * Two conventions are supported because both are common. A sidecar asset named
 * like the jar with a .sha256 suffix, whose contents are the hash, optionally
 * followed by a filename in the usual sha256sum format. Or a line in the
 * release body pairing a 64 character hex hash with the jar's name, which is
 * how a release written by hand usually records it.
 *
 * A jar with no discoverable checksum is reported as having none, which the
 * rest of the library treats as not updatable. There is deliberately no path
 * that stages a jar this class could not verify: an unverifiable download from
 * the plugin that guards every account is exactly the thing not worth the
 * convenience.
 */
final class Checksums {

    private static final Pattern HEX_64 = Pattern.compile("\\b([0-9a-fA-F]{64})\\b");

    private Checksums() {
    }

    /**
     * The expected hash for a jar, from a sidecar asset if present, otherwise
     * from the release body. Empty when neither names one.
     */
    static Optional<String> forJar(Release.Asset jar,
                                   List<Release.Asset> assets,
                                   String body,
                                   GitHubReleases client) {
        if (jar == null) {
            return Optional.empty();
        }

        Optional<Release.Asset> sidecar = assets.stream()
                .filter(Release.Asset::isChecksum)
                .filter(asset -> namesJar(asset.name(), jar.name()))
                .findFirst();
        if (sidecar.isPresent()) {
            try {
                return firstHash(client.fetchText(sidecar.get().downloadUrl()));
            } catch (IOException | InterruptedException unreachable) {
                if (unreachable instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // Fall through to the body: a sidecar that cannot be fetched
                // is no worse than one that was never published.
            }
        }

        return fromBody(body, jar.name());
    }

    /**
     * Verifies a stream against an expected hash without holding the whole
     * jar in memory. The stream is consumed.
     */
    static boolean matches(InputStream data, String expectedHex) throws IOException {
        String actual = sha256(data);
        return constantTimeEquals(actual, expectedHex.trim().toLowerCase());
    }

    static String sha256(InputStream data) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = data.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is required of every JVM, so this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static Optional<String> fromBody(String body, String jarName) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        for (String line : body.split("\\R")) {
            if (line.contains(jarName)) {
                Matcher matcher = HEX_64.matcher(line);
                if (matcher.find()) {
                    return Optional.of(matcher.group(1).toLowerCase());
                }
            }
        }
        // A body that lists exactly one hash and one jar, on separate lines,
        // is still unambiguous. Fall back to a lone hash only when there is
        // precisely one in the whole body.
        Matcher matcher = HEX_64.matcher(body);
        if (matcher.find()) {
            String only = matcher.group(1);
            if (!matcher.find()) {
                return Optional.of(only.toLowerCase());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstHash(String content) {
        Matcher matcher = HEX_64.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1).toLowerCase()) : Optional.empty();
    }

    private static boolean namesJar(String checksumName, String jarName) {
        String base = checksumName;
        for (String suffix : new String[]{".sha256sum", ".sha256"}) {
            if (base.toLowerCase().endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }
        // A sidecar is either named exactly after the jar or generically, such
        // as checksums.sha256. A generic name pairs with the only jar present,
        // which the caller has already reduced to one.
        return base.isEmpty() || base.equalsIgnoreCase(jarName) || !base.contains(".");
    }

    /**
     * Compares two hex hashes without leaking where they first differ.
     *
     * A hash comparison is not a secret, but constant time comparison here
     * costs nothing and keeps the habit, so no future security relevant
     * comparison in this library is the first to be written the timing leaking
     * way.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) {
            difference |= a.charAt(i) ^ b.charAt(i);
        }
        return difference == 0;
    }
}
