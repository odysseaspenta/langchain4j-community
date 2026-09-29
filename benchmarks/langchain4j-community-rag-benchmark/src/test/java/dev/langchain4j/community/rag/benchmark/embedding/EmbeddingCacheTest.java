package dev.langchain4j.community.rag.benchmark.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.TestDatasets;
import dev.langchain4j.community.rag.benchmark.util.Checksums;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddingCacheTest {

    @TempDir
    Path dataDir;

    @TempDir
    Path otherDir;

    PreparedDataset dataset;

    @BeforeEach
    void prepare() throws Exception {
        dataset = TestDatasets.prepare(dataDir, 1_000, 10, 42);
    }

    EmbeddingCache cache(FakeEmbeddingModel model) {
        return new EmbeddingCache(dataDir, dataset, FakeEmbeddingModel.spec("query: ", model));
    }

    @Test
    void should_embed_passages_in_priority_order() throws Exception {
        EmbeddingCache cache = cache(new FakeEmbeddingModel(Integer.MAX_VALUE));

        CacheState state = cache.embedPassages(600, 3);

        assertThat(state.rows()).isEqualTo(600);
        assertThat(state.vectorsSha256()).isNotNull();
        assertThat(cache.passageIds()).isEqualTo(dataset.priorityOrder().subList(0, 600));
        try (VectorMatrix matrix = cache.passages()) {
            String firstId = cache.passageIds().get(0);
            int n = Integer.parseInt(firstId.substring(3));
            String content = n % 2 == 0 ? "Title " + n + "\nText of passage " + n : "Text of passage " + n;
            assertThat(matrix.get(0)).containsExactly(FakeEmbeddingModel.vector(content));
        }
    }

    @Test
    void should_append_when_extending_without_re_embedding() throws Exception {
        FakeEmbeddingModel model = new FakeEmbeddingModel(Integer.MAX_VALUE);
        EmbeddingCache cache = cache(model);
        cache.embedPassages(300, 2);
        byte[] before = Files.readAllBytes(cache.dir().resolve("passages.f32"));

        cache.embedPassages(1_000, 2);

        byte[] after = Files.readAllBytes(cache.dir().resolve("passages.f32"));
        assertThat(model.embedded).hasValue(1_000);
        assertThat(Arrays.copyOf(after, before.length)).isEqualTo(before);
        assertThat(cache.embedPassages(500, 2).rows()).isEqualTo(1_000);
        assertThat(model.embedded).hasValue(1_000);
    }

    @Test
    void should_resume_after_crash_with_identical_result() throws Exception {
        PreparedDataset reference = TestDatasets.prepare(otherDir, 1_000, 10, 42);
        String expected = new EmbeddingCache(
                        otherDir,
                        reference,
                        FakeEmbeddingModel.spec("query: ", new FakeEmbeddingModel(Integer.MAX_VALUE)))
                .embedPassages(1_000, 2)
                .vectorsSha256();

        FakeEmbeddingModel crashing = new FakeEmbeddingModel(700);
        assertThatThrownBy(() -> cache(crashing).embedPassages(1_000, 1)).hasMessage("simulated crash");
        CacheState partial = cache(crashing).readState("passages");
        assertThat(partial.rows()).isEqualTo(512);
        assertThat(partial.vectorsSha256()).isNull();
        // Simulate a torn write after the last commit.
        Files.write(cache(crashing).dir().resolve("passages.f32"), new byte[123], StandardOpenOption.APPEND);

        FakeEmbeddingModel resumed = new FakeEmbeddingModel(Integer.MAX_VALUE);
        CacheState state = cache(resumed).embedPassages(1_000, 2);

        assertThat(resumed.embedded).hasValue(1_000 - 512);
        assertThat(state.vectorsSha256()).isEqualTo(expected);
        assertThat(Checksums.sha256(cache(resumed).dir().resolve("passages.f32")))
                .isEqualTo(expected);
    }

    @Test
    void should_refuse_cache_built_with_different_prefix() throws Exception {
        cache(new FakeEmbeddingModel(Integer.MAX_VALUE)).embedQueries();

        EmbeddingCache other = new EmbeddingCache(
                dataDir, dataset, FakeEmbeddingModel.spec("other: ", new FakeEmbeddingModel(Integer.MAX_VALUE)));

        assertThatThrownBy(other::embedQueries).isInstanceOf(IOException.class).hasMessageContaining("different");
    }

    @Test
    void should_embed_queries_with_prefix_and_latency() throws Exception {
        EmbeddingCache cache = cache(new FakeEmbeddingModel(Integer.MAX_VALUE));

        CacheState state = cache.embedQueries();

        assertThat(state.rows()).isEqualTo(10);
        assertThat(state.latency().samples()).isEqualTo(10);
        assertThat(cache.queryIds()).startsWith("q0", "q1");
        try (VectorMatrix queries = cache.queries()) {
            assertThat(queries.get(1)).containsExactly(FakeEmbeddingModel.vector("query: question 1"));
        }
    }

    @Test
    void should_verify_cached_passages() throws Exception {
        EmbeddingCache cache = cache(new FakeEmbeddingModel(Integer.MAX_VALUE));
        cache.embedPassages(200, 2);

        EmbeddingCache.VerifyResult result = cache.verifyPassages(20, 1);

        assertThat(result.samples()).isEqualTo(20);
        assertThat(result.mismatches()).isZero();
    }
}
