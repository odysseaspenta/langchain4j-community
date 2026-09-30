package dev.langchain4j.community.rag.benchmark.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.TestDatasets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Passage embedding through an embedding server (PRD A4, B09a) against {@link FakeEmbeddingServer}.
 */
class EmbeddingServerCacheTest {

    static final int WINDOW = 128;
    static final int BATCH = 16;

    @TempDir
    Path dataDir;

    @TempDir
    Path otherDir;

    PreparedDataset dataset;
    FakeEmbeddingServer server;

    @BeforeEach
    void setUp() throws Exception {
        dataset = TestDatasets.prepare(dataDir, 1_000, 10, 42);
        server = new FakeEmbeddingServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    EmbeddingCache cache(Path dir, PreparedDataset data) {
        return new EmbeddingCache(
                dir, data, FakeEmbeddingModel.spec("query: ", new FakeEmbeddingModel(Integer.MAX_VALUE)));
    }

    CacheState embedRemote(EmbeddingCache cache, int rows) throws Exception {
        return cache.embedPassagesRemote(
                rows, 2, server.client(), EmbeddingServer.HubModel.BGE_SMALL_EN_V15, BATCH, WINDOW);
    }

    @Test
    void should_write_the_same_rows_as_in_process_and_record_the_backend() throws Exception {
        EmbeddingCache remote = cache(dataDir, dataset);
        PreparedDataset other = TestDatasets.prepare(otherDir, 1_000, 10, 42);
        EmbeddingCache local = cache(otherDir, other);

        CacheState state = embedRemote(remote, 600);
        CacheState reference = local.embedPassages(600, 2);

        // Same vectors (the fake server serves the in-process fake model), same priority order.
        assertThat(state.vectorsSha256()).isEqualTo(reference.vectorsSha256());
        assertThat(state.idsSha256()).isEqualTo(reference.idsSha256());
        assertThat(state.segments()).singleElement().satisfies(segment -> {
            assertThat(segment.fromRow()).isZero();
            assertThat(segment.toRow()).isEqualTo(600);
            assertThat(segment.backend()).isEqualTo(CacheState.HTTP);
            assertThat(segment.details())
                    .containsEntry("inProcessRows", 0)
                    .containsEntry("window", WINDOW)
                    .containsKeys("health", "startupCheckMinCosine");
        });
        assertThat(state.backendOf(599)).isEqualTo(CacheState.HTTP);
        assertThat(reference.backendOf(0)).isEqualTo(CacheState.IN_PROCESS);
    }

    @Test
    void should_embed_refused_texts_in_process() throws Exception {
        // Passages with a title ("Title n\nText of passage n") are the long ones here.
        server.maxLength = 25;
        EmbeddingCache remote = cache(dataDir, dataset);
        PreparedDataset other = TestDatasets.prepare(otherDir, 1_000, 10, 42);

        CacheState state = embedRemote(remote, 300);

        assertThat(state.vectorsSha256())
                .isEqualTo(cache(otherDir, other).embedPassages(300, 2).vectorsSha256());
        int inProcess = (Integer) state.segments().get(0).details().get("inProcessRows");
        assertThat(inProcess).isPositive().isLessThan(300);
        assertThat(server.embedded.get()).isGreaterThanOrEqualTo(300 - inProcess);
    }

    @Test
    void should_extend_an_in_process_cache_and_verify_each_row_by_its_backend() throws Exception {
        EmbeddingCache cache = cache(dataDir, dataset);
        cache.embedPassages(200, 2);
        byte[] before = Files.readAllBytes(cache.dir().resolve("passages.f32"));
        // Server vectors differ slightly from the in-process ones, as GPU kernels do.
        server.noise = 1e-4f;

        CacheState state = embedRemote(cache, 500);

        assertThat(state.rows()).isEqualTo(500);
        assertThat(state.segments())
                .extracting(CacheState.Segment::backend)
                .containsExactly(CacheState.IN_PROCESS, CacheState.HTTP);
        assertThat(state.segments().get(1).fromRow()).isEqualTo(200);
        byte[] after = Files.readAllBytes(cache.dir().resolve("passages.f32"));
        assertThat(java.util.Arrays.copyOf(after, before.length)).isEqualTo(before);

        EmbeddingCache.VerifyResult verify = cache.verifyPassages(100, 7);
        assertThat(verify.serverRows()).isPositive();
        assertThat(verify.mismatches()).isZero();
        assertThat(verify.minCosine()).isLessThan(1).isGreaterThan(0.999);
    }

    @Test
    void should_resume_at_a_window_boundary_with_the_same_result() throws Exception {
        EmbeddingCache uninterrupted = cache(otherDir, TestDatasets.prepare(otherDir, 1_000, 10, 42));
        CacheState reference = embedRemote(uninterrupted, 600);

        EmbeddingCache cache = cache(dataDir, dataset);
        // Start-up check (1 request) + two windows of 128 rows at 16 rows per request (8 each), then failure.
        server.failAfterRequests = server.requests.get() + 1 + 8 * 2 + 3;
        assertThatThrownBy(() -> embedRemote(cache, 600)).isInstanceOf(IOException.class);
        assertThat(cache.readState("passages").rows()).isEqualTo(2 * WINDOW);

        server.failAfterRequests = Integer.MAX_VALUE;
        CacheState resumed = embedRemote(cache, 600);

        assertThat(resumed.vectorsSha256()).isEqualTo(reference.vectorsSha256());
        assertThat(resumed.segments())
                .extracting(CacheState.Segment::fromRow)
                .containsExactly(0, 2 * WINDOW);
    }

    @Test
    void should_refuse_a_server_running_another_revision() throws Exception {
        server.revision = "0000000000000000000000000000000000000000";
        EmbeddingCache cache = cache(dataDir, dataset);

        assertThatThrownBy(() -> embedRemote(cache, 300))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("expected BAAI/bge-small-en-v1.5");
        assertThat(cache.readState("passages").rows()).isZero();
    }

    @Test
    void should_refuse_a_server_whose_vectors_differ_before_writing_anything() throws Exception {
        server.noise = 0.5f;
        EmbeddingCache cache = cache(dataDir, dataset);

        assertThatThrownBy(() -> embedRemote(cache, 300))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("do not match the in-process model");
        assertThat(cache.readState("passages").rows()).isZero();
        assertThat(Files.size(cache.dir().resolve("passages.f32"))).isZero();
    }
}
