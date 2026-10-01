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
import dev.langchain4j.community.rag.benchmark.targets.BenchmarkTarget;
import dev.langchain4j.community.rag.benchmark.targets.DocumentSource;
import dev.langchain4j.community.rag.benchmark.targets.LoadOptions;
import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import dev.langchain4j.community.rag.benchmark.targets.SearchRequest;
import dev.langchain4j.community.rag.benchmark.targets.SearchResult;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbRemoteTargetTest;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbSettings;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbTarget;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbVersion;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.RemoteSettings;
import dev.langchain4j.community.rag.benchmark.util.CpuSet;
import dev.langchain4j.community.rag.benchmark.util.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
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
    void should_abort_a_scenario_after_a_failure_streak_and_count_skipped_queries_as_empty() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig(dataDir, resultsDir, 42, BenchmarkConfig.DEV);
        ProfilePlan densePlan = new ProfilePlan(
                Profile.SMOKE, List.of(Tier.SMOKE), List.of(TargetMode.EMBEDDED), List.of(Scenario.DENSE), 10, 2);
        Runner.TargetFactory factory = (tier, mode) -> new NothingTarget();

        RunResult result = new Runner(
                        config, dataset, cache, groundTruth, factory, new Runner.Settings(100, null, "dev", 3, null))
                .run(densePlan);

        RunResult.ScenarioResult dense = result.runs().get(0).scenarios().get(0);
        assertThat(dense.aborted()).isEqualTo("3 consecutive failed or empty queries");
        assertThat(dense.failures().empty()).isEqualTo(3);
        assertThat(dense.failures().skipped()).isEqualTo(7);
        assertThat(dense.accuracy().queries()).isEqualTo(10);
        assertThat(dense.accuracy().ndcgAt10()).isZero();
        assertThat(dense.repetitions()).isEmpty();
        assertThat(dense.cold().samples()).isEqualTo(3);
        assertThat(result.config().failureStreakLimit()).isEqualTo(3);

        Path dir = ResultWriter.write(resultsDir, "arcadedb-test", result);
        assertThat(Files.readString(dir.resolve("report.md")))
                .contains("dense ⚠ aborted")
                .contains("aborted: 3 consecutive failed or empty queries");
    }

    @Test
    void should_not_abort_when_queries_succeed() throws Exception {
        RunResult result = run();

        assertThat(result.runs().get(0).scenarios())
                .allSatisfy(scenario -> {
                    assertThat(scenario.aborted()).isNull();
                    assertThat(scenario.failures().skipped()).isZero();
                });
    }

    @Test
    void should_run_remote_mode_in_docker() throws Exception {
        Assumptions.assumeTrue(ArcadeDbRemoteTargetTest.dockerAvailable(), "Docker not available");
        BenchmarkConfig config = new BenchmarkConfig(dataDir, resultsDir, 42, BenchmarkConfig.DEV);
        ProfilePlan remotePlan = plan.withModes(List.of(TargetMode.REMOTE));
        RemoteSettings remote = new RemoteSettings(
                ArcadeDbVersion.dockerImage(), CpuSet.online().upperHalf(), "1g", Duration.ofSeconds(25));
        Runner.TargetFactory factory = (tier, mode) -> ArcadeDbTarget.remote(
                dataDir.resolve("server"), ArcadeDbSettings.pinned(FakeEmbeddingModel.DIMENSION), remote);

        RunResult result = new Runner(config, dataset, cache, groundTruth, factory, new Runner.Settings(100, null, "dev"))
                .run(remotePlan);

        RunResult.TargetRun run = result.runs().get(0);
        assertThat(run.target()).isEqualTo("arcadedb-remote");
        assertThat(run.mode()).isEqualTo("remote");
        assertThat(run.load().loaded()).isEqualTo(600);
        assertThat(run.scenarios()).allSatisfy(scenario -> {
            assertThat(scenario.aborted()).isNull();
            assertThat(scenario.failures().failed()).isZero();
        });
        Path dir = ResultWriter.write(resultsDir, "arcadedb-test", result);
        assertThat(Files.readString(dir.resolve("report.md")))
                .contains("| smoke | arcadedb-remote | dense |")
                .contains("server CPUs " + CpuSet.online().upperHalf());
    }

    @Test
    void should_record_a_failed_load_checkpoint_after_each_load_and_continue() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig(dataDir, resultsDir, 42, BenchmarkConfig.DEV);
        ProfilePlan twoLoads = plan.withModes(List.of(TargetMode.REMOTE, TargetMode.EMBEDDED));
        Runner.TargetFactory factory = (tier, mode) -> mode == TargetMode.REMOTE
                ? new FailingTarget()
                : ArcadeDbTarget.embedded(dataDir.resolve("db"), ArcadeDbSettings.pinned(FakeEmbeddingModel.DIMENSION));
        List<RunResult> checkpoints = new java.util.ArrayList<>();
        Path dir = ResultWriter.createRunDir(resultsDir, "arcadedb-test", "smoke", "partial");

        RunResult result = new Runner(config, dataset, cache, groundTruth, factory, new Runner.Settings(100, null, "dev"))
                .run(twoLoads, partial -> {
                    checkpoints.add(partial);
                    ResultWriter.writeTo(dir, partial);
                });

        assertThat(checkpoints).hasSize(2);
        assertThat(checkpoints.get(0).status()).isEqualTo(RunResult.RUNNING);
        assertThat(checkpoints.get(0).finishedAt()).isNull();
        assertThat(checkpoints.get(0).runs()).hasSize(1);
        assertThat(result.status()).isEqualTo(RunResult.INCOMPLETE);
        RunResult.TargetRun failed = result.runs().get(0);
        assertThat(failed.error()).contains("simulated load failure");
        assertThat(failed.load()).isNull();
        assertThat(failed.scenarios()).isEmpty();
        RunResult.TargetRun ok = result.runs().get(1);
        assertThat(ok.error()).isNull();
        assertThat(ok.scenarios()).hasSize(4);

        ResultWriter.writeTo(dir, result);
        assertThat(dir.getFileName().toString()).endsWith("-arcadedb-test-smoke-partial");
        assertThat(Json.read(dir.resolve("result.json"), RunResult.class).status()).isEqualTo(RunResult.INCOMPLETE);
        assertThat(Files.readString(dir.resolve("report.md")))
                .contains("Status: incomplete")
                .contains("| failing | smoke | failed |")
                .contains("failing / smoke failed: `java.lang.IllegalStateException: simulated load failure`");
    }

    static final class FailingTarget implements BenchmarkTarget {

        @Override
        public String name() {
            return "failing";
        }

        @Override
        public LoadStats load(DocumentSource source, LoadOptions options) {
            throw new IllegalStateException("simulated load failure");
        }

        @Override
        public SearchResult search(SearchRequest request) {
            throw new AssertionError("not loaded");
        }

        @Override
        public Map<String, Boolean> capabilities() {
            return Map.of();
        }

        @Override
        public Map<String, Object> describe() {
            return Map.of("target", name());
        }

        @Override
        public void close() {}
    }

    /** A target that answers every query with nothing, like remote dense on the unmodified store after a timeout. */
    static final class NothingTarget implements BenchmarkTarget {

        @Override
        public String name() {
            return "nothing";
        }

        @Override
        public LoadStats load(DocumentSource source, LoadOptions options) {
            return new LoadStats(source.size(), 0, false, 0, 0, 0, 0, -1, 0, 0, -1);
        }

        @Override
        public SearchResult search(SearchRequest request) {
            return SearchResult.of(List.of(), List.of());
        }

        @Override
        public Map<String, Boolean> capabilities() {
            return Map.of();
        }

        @Override
        public Map<String, Object> describe() {
            return Map.of("target", name());
        }

        @Override
        public void close() {}
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
