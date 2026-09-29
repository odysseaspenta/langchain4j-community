package dev.langchain4j.community.rag.benchmark.runner;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.TestDatasets;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.FakeEmbeddingModel;
import dev.langchain4j.community.rag.benchmark.groundtruth.GroundTruth;
import dev.langchain4j.community.rag.benchmark.report.ResultWriter;
import dev.langchain4j.community.rag.benchmark.report.RunResult;
import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbSettings;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbTarget;
import dev.langchain4j.community.rag.benchmark.util.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end runner on a tiny dataset with fake 8-d embeddings and real embedded ArcadeDB.
 */
class RunnerTest {

    static final Scenario FILTERED = new Scenario("dense-bucket-lt-50", SearchMode.DENSE, 50, null);
    static final Scenario DEEP = new Scenario("dense-ef-200", SearchMode.DENSE, null, 200);

    @TempDir
    Path dataDir;

    @TempDir
    Path resultsDir;

    PreparedDataset dataset;
    EmbeddingCache cache;
    GroundTruth groundTruth;
    ProfilePlan plan;

    @BeforeEach
    void setUp() throws Exception {
        dataset = TestDatasets.prepare(dataDir, 600, 10, 42);
        cache = new EmbeddingCache(
                dataDir, dataset, FakeEmbeddingModel.spec("q: ", new FakeEmbeddingModel(Integer.MAX_VALUE)));
        cache.embedPassages(600, 2);
        cache.embedQueries();
        groundTruth = new GroundTruth(dataDir, dataset, cache, 10);
        groundTruth.compute(EnumSet.of(Tier.SMOKE), 2);
        plan = new ProfilePlan(
                Profile.SMOKE,
                List.of(Tier.SMOKE),
                List.of(TargetMode.EMBEDDED),
                List.of(Scenario.DENSE, Scenario.HYBRID_AS_IS, FILTERED, DEEP),
                10,
                2);
    }

    RunResult run() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig(dataDir, resultsDir, 42, BenchmarkConfig.DEV);
        Runner.TargetFactory factory = (tier, mode) ->
                ArcadeDbTarget.embedded(dataDir.resolve("db"), ArcadeDbSettings.pinned(FakeEmbeddingModel.DIMENSION));
        return new Runner(config, dataset, cache, groundTruth, factory, new Runner.Settings(100, null, "dev"))
                .run(plan);
    }

    @Test
    void should_run_all_scenarios_and_record_metrics() throws Exception {
        RunResult result = run();

        assertThat(result.runs()).hasSize(1);
        RunResult.TargetRun targetRun = result.runs().get(0);
        assertThat(targetRun.load().loaded()).isEqualTo(600);
        assertThat(targetRun.scenarios())
                .extracting(RunResult.ScenarioResult::name)
                .containsExactly("dense", "hybrid-asis", "dense-bucket-lt-50", "dense-ef-200");

        RunResult.ScenarioResult dense = targetRun.scenarios().get(0);
        assertThat(dense.accuracy().queries()).isEqualTo(10);
        assertThat(dense.annRecall().at10()).isGreaterThanOrEqualTo(0.9);
        assertThat(dense.failures().failed()).isZero();
        assertThat(dense.repetitions()).hasSize(2);
        assertThat(dense.stable()).isTrue();
        assertThat(dense.efSearchApplied()).isTrue();

        assertThat(targetRun.scenarios().get(1).annRecall()).isNull();
        assertThat(targetRun.scenarios().get(2).annRecall()).isNotNull();
        assertThat(targetRun.scenarios().get(3).efSearchApplied()).isFalse();
        assertThat(result.inputs().groundTruthSha256()).containsOnlyKeys("smoke", "smoke-bucket-lt-50");
        assertThat(result.machineClass()).isEqualTo("dev");
    }

    @Test
    void should_give_identical_accuracy_on_consecutive_runs() throws Exception {
        RunResult first = run();
        RunResult second = run();

        for (int s = 0; s < plan.scenarios().size(); s++) {
            RunResult.ScenarioResult a = first.runs().get(0).scenarios().get(s);
            RunResult.ScenarioResult b = second.runs().get(0).scenarios().get(s);
            assertThat(b.accuracy()).isEqualTo(a.accuracy());
            assertThat(b.annRecall()).isEqualTo(a.annRecall());
        }
    }

    @Test
    void should_write_json_and_markdown() throws Exception {
        RunResult result = run();

        Path dir = ResultWriter.write(resultsDir, "arcadedb-test", result);
        Path again = ResultWriter.write(resultsDir, "arcadedb-test", result);

        assertThat(dir.getFileName().toString()).endsWith("-arcadedb-test-smoke");
        assertThat(again.getFileName().toString()).endsWith("-arcadedb-test-smoke-2");
        RunResult read = Json.read(dir.resolve("result.json"), RunResult.class);
        assertThat(read.runs().get(0).scenarios().get(0).accuracy())
                .isEqualTo(result.runs().get(0).scenarios().get(0).accuracy());
        assertThat(Files.readString(dir.resolve("report.md")))
                .contains("# RAG benchmark: smoke profile")
                .contains("Development machine")
                .contains("| smoke | arcadedb-embedded | dense |")
                .contains("200 (not applied)");
    }
}
