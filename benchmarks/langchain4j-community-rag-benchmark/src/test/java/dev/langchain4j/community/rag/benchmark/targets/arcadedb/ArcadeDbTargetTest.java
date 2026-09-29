package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.SyntheticMetadata;
import dev.langchain4j.community.rag.benchmark.dataset.TestDatasets;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.FakeEmbeddingModel;
import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import dev.langchain4j.community.rag.benchmark.groundtruth.GroundTruth;
import dev.langchain4j.community.rag.benchmark.groundtruth.Neighbours;
import dev.langchain4j.community.rag.benchmark.metrics.AnnRecall;
import dev.langchain4j.community.rag.benchmark.runner.TierDocumentSource;
import dev.langchain4j.community.rag.benchmark.targets.LoadOptions;
import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import dev.langchain4j.community.rag.benchmark.targets.SearchRequest;
import dev.langchain4j.community.rag.benchmark.targets.SearchResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real embedded ArcadeDB (unmodified store) on a tiny dataset with fake 8-d embeddings.
 */
class ArcadeDbTargetTest {

    static final int PASSAGES = 600;

    @TempDir
    Path dataDir;

    PreparedDataset dataset;
    EmbeddingCache cache;

    @BeforeEach
    void setUp() throws Exception {
        dataset = TestDatasets.prepare(dataDir, PASSAGES, 10, 42);
        cache = new EmbeddingCache(
                dataDir, dataset, FakeEmbeddingModel.spec("q: ", new FakeEmbeddingModel(Integer.MAX_VALUE)));
        cache.embedPassages(PASSAGES, 2);
        cache.embedQueries();
    }

    ArcadeDbTarget target() {
        return ArcadeDbTarget.embedded(dataDir.resolve("db"), ArcadeDbSettings.pinned(FakeEmbeddingModel.DIMENSION));
    }

    LoadStats load(ArcadeDbTarget target, LoadOptions options) throws Exception {
        try (TierDocumentSource source = new TierDocumentSource(dataset, cache, dataset.loadOrder(Tier.SMOKE))) {
            return target.load(source, options);
        }
    }

    @Test
    void should_load_and_answer_dense_queries_close_to_exact() throws Exception {
        try (ArcadeDbTarget target = target()) {
            LoadStats stats = load(target, new LoadOptions(100, null));

            assertThat(stats.loaded()).isEqualTo(PASSAGES);
            assertThat(stats.capped()).isFalse();
            assertThat(stats.diskBytes()).isPositive();
            assertThat(stats.timeToSearchableSeconds()).isNotNegative();
            assertThat(target.describe()).containsEntry("vectorSubIndexes", 1).containsKey("versionDefaults");
            @SuppressWarnings("unchecked")
            Map<String, Object> effective =
                    (Map<String, Object>) target.describe().get("effectiveIndex");
            assertThat(effective).containsEntry("maxConnections", "16").containsEntry("beamWidth", "100");

            GroundTruth groundTruth = new GroundTruth(dataDir, dataset, cache, 10);
            groundTruth.compute(EnumSet.of(Tier.SMOKE), 1);
            Neighbours exact = groundTruth.load(Tier.SMOKE, null);
            List<String> cacheIds = cache.passageIds();
            List<List<String>> retrieved = new ArrayList<>();
            List<List<String>> truth = new ArrayList<>();
            try (VectorMatrix queries = cache.queries()) {
                for (int q = 0; q < queries.rows(); q++) {
                    SearchResult result =
                            target.search(new SearchRequest(SearchMode.DENSE, queries.get(q), null, 10, null, null));
                    assertThat(result.failed()).as(result.failure()).isFalse();
                    retrieved.add(result.ids());
                    List<String> ids = new ArrayList<>();
                    for (int rank = 0; rank < 10; rank++) {
                        ids.add(cacheIds.get(exact.row(q, rank)));
                    }
                    truth.add(ids);
                }
            }
            assertThat(AnnRecall.mean(retrieved, truth, 10)).isGreaterThanOrEqualTo(0.9);
        }
    }

    @Test
    void should_answer_hybrid_and_filtered_queries() throws Exception {
        try (ArcadeDbTarget target = target();
                VectorMatrix queries = cache.queries()) {
            load(target, new LoadOptions(100, null));

            SearchResult hybrid =
                    target.search(new SearchRequest(SearchMode.HYBRID, queries.get(0), "passage", 10, null, null));
            SearchResult filtered =
                    target.search(new SearchRequest(SearchMode.DENSE, queries.get(0), null, 10, 50, null));

            assertThat(hybrid.failed()).as(hybrid.failure()).isFalse();
            assertThat(hybrid.ids()).isNotEmpty();
            assertThat(filtered.ids()).isNotEmpty();
            for (String id : filtered.ids()) {
                assertThat(SyntheticMetadata.bucket(id, 42)).isLessThan(50);
            }
        }
    }

    @Test
    void should_stop_at_time_cap_and_report_partial_stats() throws Exception {
        try (ArcadeDbTarget target = target()) {
            LoadStats stats = load(target, new LoadOptions(50, Duration.ZERO));

            assertThat(stats.capped()).isTrue();
            assertThat(stats.loaded()).isEqualTo(50);
            assertThat(stats.requested()).isEqualTo(PASSAGES);
            assertThat(stats.extrapolatedLoadSeconds()).isGreaterThan(stats.loadSeconds());
        }
    }

    @Test
    void should_report_failures_instead_of_throwing() throws Exception {
        try (ArcadeDbTarget target = target()) {
            load(target, new LoadOptions(100, null));

            SearchResult result =
                    target.search(new SearchRequest(SearchMode.DENSE, new float[] {1, 2}, null, 10, null, null));

            assertThat(result.failed()).isTrue();
        }
    }
}
