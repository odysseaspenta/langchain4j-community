package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.TestDatasets;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.FakeEmbeddingModel;
import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import dev.langchain4j.community.rag.benchmark.runner.TierDocumentSource;
import dev.langchain4j.community.rag.benchmark.targets.LoadOptions;
import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import dev.langchain4j.community.rag.benchmark.targets.SearchRequest;
import dev.langchain4j.community.rag.benchmark.targets.SearchResult;
import dev.langchain4j.community.rag.benchmark.util.CpuSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Remote mode against a real ArcadeDB container (unmodified store) on a tiny dataset with fake 8-d embeddings.
 * Skipped when Docker is not available.
 */
public class ArcadeDbRemoteTargetTest {

    static final int PASSAGES = 600;

    @TempDir
    Path dataDir;

    PreparedDataset dataset;
    EmbeddingCache cache;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        dataset = TestDatasets.prepare(dataDir, PASSAGES, 10, 42);
        cache = new EmbeddingCache(
                dataDir, dataset, FakeEmbeddingModel.spec("q: ", new FakeEmbeddingModel(Integer.MAX_VALUE)));
        cache.embedPassages(PASSAGES, 2);
        cache.embedQueries();
    }

    @Test
    void should_load_pin_the_server_and_answer_dense_and_hybrid_queries() throws Exception {
        CpuSet serverCpus = CpuSet.online().upperHalf();
        RemoteSettings remote =
                new RemoteSettings(ArcadeDbVersion.dockerImage(), serverCpus, "1g", Duration.ofSeconds(30));
        Path serverDir = dataDir.resolve("server");
        String container;

        try (ArcadeDbTarget target = ArcadeDbTarget.remote(
                        serverDir, ArcadeDbSettings.pinned(FakeEmbeddingModel.DIMENSION), remote);
                TierDocumentSource source = new TierDocumentSource(dataset, cache, dataset.loadOrder(Tier.SMOKE));
                VectorMatrix queries = cache.queries()) {
            LoadStats stats = target.load(source, new LoadOptions(100, null));

            assertThat(stats.loaded()).isEqualTo(PASSAGES);
            assertThat(stats.diskBytes()).isPositive();
            assertThat(stats.serverPeakMemoryBytes()).isPositive();
            assertThat(stats.timeToSearchableSeconds()).isNotNegative();

            container = target.server().containerName();
            assertThat(target.server().inspectCpuset()).isEqualTo(serverCpus.toString());
            Map<String, Object> description = target.describe();
            assertThat(description)
                    .containsEntry("target", "arcadedb-remote")
                    .containsEntry("vectorSubIndexes", 1)
                    .containsEntry("serverCpus", serverCpus.toString())
                    .containsEntry("queryTimeoutMillis", 30_000L);
            @SuppressWarnings("unchecked")
            Map<String, Object> server = (Map<String, Object>) description.get("server");
            assertThat(server).containsEntry("cpusetCpus", serverCpus.toString()).containsKey("imageId");

            // Unmodified store: remote dense returns scan-order rows re-sorted by score (S3), so only check it answers.
            SearchResult dense =
                    target.search(new SearchRequest(SearchMode.DENSE, queries.get(0), null, 10, null, null));
            SearchResult hybrid =
                    target.search(new SearchRequest(SearchMode.HYBRID, queries.get(0), "passage", 10, null, null));

            assertThat(dense.failed()).as(dense.failure()).isFalse();
            assertThat(dense.ids()).hasSize(10);
            assertThat(hybrid.failed()).as(hybrid.failure()).isFalse();
            assertThat(hybrid.ids()).isNotEmpty();
        }

        // F18: the per-search INFO line of 26.7.2 is not logged by the server.
        try (Stream<Path> logs = Files.list(serverDir.resolve("log"))) {
            for (Path file : logs.filter(Files::isRegularFile).toList()) {
                assertThat(Files.readString(file)).doesNotContain("Vector search returned");
            }
        }
        assertThat(serverDir.resolve("log").resolve("gc.log")).exists();
        // close() removed the container.
        assertThat(DockerServer.docker(List.of("docker", "ps", "-aq", "--filter", "name=^" + container + "$")))
                .isBlank();
    }

    public static boolean dockerAvailable() {
        try {
            DockerServer.docker(List.of("docker", "info", "--format", "{{.ServerVersion}}"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
