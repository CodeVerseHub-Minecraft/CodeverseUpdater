package net.codeverse.updater;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Downloads a release jar, verifies it, and stages it for the next restart.
 *
 * Staging rather than applying is the deliberate default. The server platform
 * loads a plugin's jar at startup and holds it open, so replacing the running
 * jar in place is unreliable at best and corrupting at worst. Writing the new
 * jar into the update folder, which the platform swaps in on the next boot,
 * is the mechanism the platforms already provide for exactly this.
 *
 * Every step that could leave a partial or unverified jar in place is ordered
 * so that it cannot. The download lands in a temp file, is verified there, and
 * only a jar that matches its checksum is moved into place, atomically. A
 * failure at any point leaves the previously staged jar, if any, untouched.
 */
public final class UpdateStager {

    private final HttpClient http;
    private final Path updateFolder;
    private final String targetJarName;

    /**
     * @param http          the client to download with, shared with the releases reader
     * @param updateFolder  the platform's update folder, usually plugins/update
     * @param targetJarName the name the running jar has, so the staged jar
     *                      replaces it rather than sitting beside it
     */
    public UpdateStager(HttpClient http, Path updateFolder, String targetJarName) {
        this.http = http;
        this.updateFolder = updateFolder;
        this.targetJarName = targetJarName;
    }

    /**
     * Downloads, verifies and stages the jar from a release.
     *
     * @return the path the verified jar was staged at
     * @throws IOException              on any network or filesystem failure
     * @throws ChecksumMismatchException when the download does not match the
     *                                   release's published checksum, which is
     *                                   treated as a hard failure rather than a
     *                                   warning: a jar that fails verification
     *                                   is never staged
     */
    public Path stage(Release release) throws IOException, InterruptedException {
        Release.Asset jar = release.jar().orElseThrow(
                () -> new IOException("Release " + release.tag() + " has no jar to stage"));
        String expected = release.declaredSha256().orElseThrow(
                () -> new IOException("Release " + release.tag() + " published no checksum, refusing to stage"));

        Files.createDirectories(updateFolder);
        Path temp = Files.createTempFile(updateFolder, ".download-", ".part");

        try {
            download(jar.downloadUrl(), temp);

            try (InputStream verifying = Files.newInputStream(temp)) {
                if (!Checksums.matches(verifying, expected)) {
                    throw new ChecksumMismatchException(release.tag(), expected);
                }
            }

            if (jar.size() > 0 && Files.size(temp) != jar.size()) {
                throw new IOException("Staged jar size " + Files.size(temp)
                        + " does not match the release's declared size " + jar.size());
            }

            Path target = updateFolder.resolve(targetJarName);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {
            // Whatever happened, no half written .part file is left behind to
            // be mistaken for a real jar or to accumulate across attempts.
            Files.deleteIfExists(temp);
        }
    }

    private void download(String url, Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "CodeverseUpdater")
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<Path> response = http.send(request,
                HttpResponse.BodyHandlers.ofFile(destination, java.nio.file.StandardOpenOption.WRITE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING));
        if (response.statusCode() != 200) {
            throw new IOException("Download of " + url + " returned status " + response.statusCode());
        }
    }

    /** Thrown when a downloaded jar does not match its release's published checksum. */
    public static final class ChecksumMismatchException extends IOException {
        private static final long serialVersionUID = 1L;

        ChecksumMismatchException(String tag, String expected) {
            super("The downloaded jar for " + tag + " did not match its published checksum " + expected
                    + ". The release may be mid upload, or the download was tampered with. Nothing was staged.");
        }
    }
}
