package dev.langchain4j.community.rag.benchmark.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.community.rag.benchmark.util.Checksums;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloaderTest {

    @TempDir
    Path tmp;

    final byte[] content = new byte[300_000];
    final List<String> ranges = new CopyOnWriteArrayList<>();
    HttpServer server;
    URI uri;

    @BeforeEach
    void startServer() throws IOException {
        new Random(1).nextBytes(content);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Range");
            ranges.add(String.valueOf(range));
            int from = range == null ? 0 : Integer.parseInt(range.substring("bytes=".length(), range.length() - 1));
            byte[] body = Arrays.copyOfRange(content, from, content.length);
            exchange.sendResponseHeaders(range == null ? 200 : 206, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void should_download_and_verify() throws Exception {
        Path target = tmp.resolve("a/file.bin");

        new Downloader().download(uri, target, content.length, Checksums.sha256(content));

        assertThat(Files.readAllBytes(target)).isEqualTo(content);
        assertThat(tmp.resolve("a/file.bin.part")).doesNotExist();
    }

    @Test
    void should_resume_from_partial_file() throws Exception {
        Path target = tmp.resolve("file.bin");
        Files.write(tmp.resolve("file.bin.part"), Arrays.copyOf(content, 123_456));

        new Downloader().download(uri, target, content.length, Checksums.sha256(content));

        assertThat(ranges).containsExactly("bytes=123456-");
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
    }

    @Test
    void should_skip_verified_existing_file() throws Exception {
        Path target = tmp.resolve("file.bin");
        Files.write(target, content);

        new Downloader().download(uri, target, content.length, Checksums.sha256(content));

        assertThat(ranges).isEmpty();
    }

    @Test
    void should_reject_checksum_mismatch() {
        Path target = tmp.resolve("file.bin");

        assertThatThrownBy(() -> new Downloader().download(uri, target, content.length, "00"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Checksum mismatch");
        assertThat(target).doesNotExist();
        assertThat(tmp.resolve("file.bin.part")).doesNotExist();
    }
}
