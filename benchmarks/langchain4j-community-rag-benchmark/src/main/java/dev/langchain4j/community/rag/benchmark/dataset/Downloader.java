package dev.langchain4j.community.rag.benchmark.dataset;

import dev.langchain4j.community.rag.benchmark.util.Checksums;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resumable HTTP download with size and SHA-256 verification.
 *
 * <p>Data goes to {@code <target>.part}; an interrupted download continues from the bytes already there
 * (HTTP {@code Range}). The file is moved to {@code target} only after it verifies.
 */
public class Downloader {

    private static final Logger log = LoggerFactory.getLogger(Downloader.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long PROGRESS_THRESHOLD_BYTES = 64L << 20;

    private final HttpClient client;

    public Downloader() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    public Downloader(HttpClient client) {
        this.client = client;
    }

    public void download(URI uri, Path target, long expectedSize, String expectedSha256)
            throws IOException, InterruptedException {
        if (Files.exists(target)) {
            if (Files.size(target) == expectedSize && Checksums.sha256(target).equals(expectedSha256)) {
                log.info("{} already downloaded and verified", target);
                return;
            }
            throw new IOException(
                    target + " exists but does not match the expected size/checksum; delete it and retry");
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && partSize(part) < expectedSize; attempt++) {
            try {
                fetch(uri, part, expectedSize);
            } catch (IOException e) {
                lastFailure = e;
                log.warn("Download of {} failed (attempt {}/{}): {}", uri, attempt, MAX_ATTEMPTS, e.getMessage());
                Thread.sleep(Math.min(30_000L, 1_000L << attempt));
            }
        }

        long size = partSize(part);
        if (size != expectedSize) {
            IOException failure =
                    new IOException("Download of " + uri + " incomplete: " + size + " of " + expectedSize + " bytes");
            if (lastFailure != null) {
                failure.addSuppressed(lastFailure);
            }
            throw failure;
        }
        String sha256 = Checksums.sha256(part);
        if (!sha256.equals(expectedSha256)) {
            Files.delete(part);
            throw new IOException("Checksum mismatch for " + uri + ": expected " + expectedSha256 + ", got " + sha256);
        }
        Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        log.info("Downloaded and verified {}", target);
    }

    private void fetch(URI uri, Path part, long expectedSize) throws IOException, InterruptedException {
        long offset = partSize(part);
        if (offset > expectedSize) {
            Files.delete(part);
            offset = 0;
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
        if (offset > 0) {
            request.header("Range", "bytes=" + offset + "-");
            log.info("Resuming {} at byte {}", uri, offset);
        } else {
            log.info("Downloading {} ({} bytes)", uri, expectedSize);
        }
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        boolean append;
        if (response.statusCode() == 206) {
            append = true;
        } else if (response.statusCode() == 200) {
            append = false; // server ignored the range: start over
            offset = 0;
        } else {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        }

        try (InputStream in = response.body();
                OutputStream out = Files.newOutputStream(
                        part,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[1 << 16];
            long written = offset;
            long nextReport = nextReport(written, expectedSize);
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                written += read;
                if (written >= nextReport && expectedSize >= PROGRESS_THRESHOLD_BYTES) {
                    log.info("{}: {}%", part.getFileName(), written * 100 / expectedSize);
                    nextReport = nextReport(written, expectedSize);
                }
            }
        }
    }

    private static long nextReport(long written, long total) {
        long step = Math.max(1, total / 10);
        return (written / step + 1) * step;
    }

    private static long partSize(Path part) throws IOException {
        return Files.exists(part) ? Files.size(part) : 0;
    }
}
