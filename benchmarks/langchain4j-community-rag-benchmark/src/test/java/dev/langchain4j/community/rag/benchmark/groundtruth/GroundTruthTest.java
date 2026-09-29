package dev.langchain4j.community.rag.benchmark.groundtruth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.SyntheticMetadata;
import dev.langchain4j.community.rag.benchmark.dataset.TestDatasets;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.FakeEmbeddingModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroundTruthTest {

    @TempDir
    Path dataDir;

    PreparedDataset dataset;
    EmbeddingCache cache;

    @BeforeEach
    void setUp() throws Exception {
        dataset = TestDatasets.prepare(dataDir, 2_000, 12, 42);
        cache = new EmbeddingCache(
                dataDir, dataset, FakeEmbeddingModel.spec("q: ", new FakeEmbeddingModel(Integer.MAX_VALUE)));
        cache.embedQueries();
    }

    @Test
    void should_compute_cache_and_load_all_filters() throws Exception {
        cache.embedPassages(2_000, 2);
        GroundTruth groundTruth = new GroundTruth(dataDir, dataset, cache, 10);

        List<GroundTruthManifest> manifests = groundTruth.compute(EnumSet.of(Tier.SMOKE, Tier.STANDARD), 2);

        assertThat(manifests)
                .extracting(GroundTruthManifest::name)
                .containsExactly(
                        "smoke",
                        "smoke-bucket-lt-1",
                        "smoke-bucket-lt-10",
                        "smoke-bucket-lt-50",
                        "standard",
                        "standard-bucket-lt-1",
                        "standard-bucket-lt-10",
                        "standard-bucket-lt-50");
        Neighbours filtered = groundTruth.load(Tier.SMOKE, 10);
        List<String> ids = cache.passageIds();
        for (int q = 0; q < filtered.queries(); q++) {
            for (int rank = 0; rank < filtered.k(); rank++) {
                int row = filtered.row(q, rank);
                if (row >= 0) {
                    assertThat(SyntheticMetadata.bucket(ids.get(row), 42)).isLessThan(10);
                }
            }
        }
        assertThat(groundTruth.load(Tier.SMOKE, null).rows())
                .isEqualTo(groundTruth.load(Tier.STANDARD, null).rows());
    }

    @Test
    void should_reuse_cached_sets() throws Exception {
        cache.embedPassages(2_000, 2);
        GroundTruth groundTruth = new GroundTruth(dataDir, dataset, cache, 10);
        groundTruth.compute(EnumSet.of(Tier.SMOKE), 2);
        Path rows = groundTruth.dir().resolve("smoke.rows");
        Files.setLastModifiedTime(rows, FileTime.fromMillis(0));

        groundTruth.compute(EnumSet.of(Tier.SMOKE), 2);

        assertThat(Files.getLastModifiedTime(rows).toMillis()).isZero();
    }

    @Test
    void should_recompute_when_files_are_corrupted() throws Exception {
        cache.embedPassages(2_000, 2);
        GroundTruth groundTruth = new GroundTruth(dataDir, dataset, cache, 10);
        groundTruth.compute(EnumSet.of(Tier.SMOKE), 2);
        int[] expected = groundTruth.load(Tier.SMOKE, null).rows();
        Files.write(groundTruth.dir().resolve("smoke.rows"), new byte[expected.length * 4]);

        groundTruth.compute(EnumSet.of(Tier.SMOKE), 2);

        assertThat(groundTruth.load(Tier.SMOKE, null).rows()).isEqualTo(expected);
    }

    @Test
    void should_require_embeddings_for_the_tier() throws Exception {
        cache.embedPassages(500, 2);
        GroundTruth groundTruth = new GroundTruth(dataDir, dataset, cache, 10);

        assertThatThrownBy(() -> groundTruth.compute(EnumSet.of(Tier.SMOKE), 2))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("rag-bench embed --tier smoke");
    }
}
