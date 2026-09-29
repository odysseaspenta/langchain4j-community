package dev.langchain4j.community.rag.benchmark.report;

import dev.langchain4j.community.rag.benchmark.metrics.LatencyStats;
import dev.langchain4j.community.rag.benchmark.metrics.Spread;
import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import java.util.List;
import java.util.Map;

/**
 * Everything one {@code rag-bench run} measured, written as {@code result.json} (PRD F20).
 */
public record RunResult(
        int formatVersion,
        String profile,
        String machineClass,
        String startedAt,
        String finishedAt,
        Environment environment,
        GitInfo git,
        RunConfig config,
        Inputs inputs,
        List<TargetRun> runs) {

    public static final int FORMAT_VERSION = 1;

    public record RunConfig(
            String dataset,
            long seed,
            String embeddingModel,
            int k,
            int repetitions,
            int loadBatchSize,
            String loadTimeCap,
            int failureStreakLimit,
            String passTimeCap,
            List<String> tiers,
            List<String> modes,
            List<String> scenarios) {}

    /**
     * Checksums identifying the data every metric was computed from.
     */
    public record Inputs(
            String datasetManifestSha256,
            Map<String, String> tierSha256,
            String passageEmbeddingsSha256,
            int passageEmbeddingRows,
            String queryEmbeddingsSha256,
            LatencyStats queryEmbeddingLatency,
            Map<String, String> groundTruthSha256) {}

    public record GitInfo(String commit, boolean dirty, boolean storeModuleDirty) {}

    /**
     * One load of one tier into one target, and the scenarios run against it.
     */
    public record TargetRun(
            String target,
            String tier,
            String mode,
            Map<String, Object> targetConfig,
            Map<String, Boolean> capabilities,
            LoadStats load,
            List<ScenarioResult> scenarios) {}

    /**
     * @param efSearchApplied   whether the requested efSearch reached the store (false: store default was used)
     * @param annRecall         ANN recall@10 / @100 vs exact neighbours ({@code null} for hybrid)
     * @param stable            whether every repetition returned exactly the same rankings
     * @param aborted           why a pass was cut short (failure streak or pass time cap), or {@code null}; skipped
     *                          queries count as empty rankings in the accuracy metrics
     */
    public record ScenarioResult(
            String name,
            String mode,
            Integer bucketsBelow,
            Integer efSearchRequested,
            boolean efSearchApplied,
            Accuracy accuracy,
            AnnRecall annRecall,
            double shortfall,
            Failures failures,
            LatencyStats cold,
            List<LatencyStats> repetitions,
            LatencySpread latencySpread,
            boolean stable,
            String aborted) {}

    public record Accuracy(double ndcgAt10, double recallAt10, double recallAt100, double mrrAt10, int queries) {}

    public record AnnRecall(double at10, double at100) {}

    /**
     * @param skipped queries not run because the pass was aborted
     */
    public record Failures(int queries, int failed, int empty, int skipped, List<String> examples) {}

    public record LatencySpread(Spread p50, Spread p95, Spread p99) {}
}
