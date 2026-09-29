package dev.langchain4j.community.rag.benchmark.runner;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.dataset.IdList;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Query;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.CacheState;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import dev.langchain4j.community.rag.benchmark.groundtruth.GroundTruth;
import dev.langchain4j.community.rag.benchmark.groundtruth.GroundTruthManifest;
import dev.langchain4j.community.rag.benchmark.groundtruth.Neighbours;
import dev.langchain4j.community.rag.benchmark.metrics.AnnRecall;
import dev.langchain4j.community.rag.benchmark.metrics.IrMetrics;
import dev.langchain4j.community.rag.benchmark.metrics.LatencyStats;
import dev.langchain4j.community.rag.benchmark.metrics.ResultCounts;
import dev.langchain4j.community.rag.benchmark.metrics.Spread;
import dev.langchain4j.community.rag.benchmark.report.Environment;
import dev.langchain4j.community.rag.benchmark.report.RunResult;
import dev.langchain4j.community.rag.benchmark.targets.BenchmarkTarget;
import dev.langchain4j.community.rag.benchmark.targets.LoadOptions;
import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import dev.langchain4j.community.rag.benchmark.targets.SearchRequest;
import dev.langchain4j.community.rag.benchmark.targets.SearchResult;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a {@link ProfilePlan}: one load per (tier, mode), then per scenario a cold pass over all test queries (which
 * also warms up) followed by the timed repetitions (PRD F14–F16). Accuracy comes from the first timed repetition;
 * every repetition's rankings are compared to detect non-deterministic results.
 */
public final class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    /** Creates the target for one (tier, mode). */
    @FunctionalInterface
    public interface TargetFactory {
        BenchmarkTarget create(Tier tier, TargetMode mode) throws Exception;
    }

    public record Settings(int loadBatchSize, Duration loadTimeCap, String machineClass) {}

    private final BenchmarkConfig config;
    private final PreparedDataset dataset;
    private final EmbeddingCache cache;
    private final GroundTruth groundTruth;
    private final TargetFactory factory;
    private final Settings settings;

    public Runner(
            BenchmarkConfig config,
            PreparedDataset dataset,
            EmbeddingCache cache,
            GroundTruth groundTruth,
            TargetFactory factory,
            Settings settings) {
        this.config = config;
        this.dataset = dataset;
        this.cache = cache;
        this.groundTruth = groundTruth;
        this.factory = factory;
        this.settings = settings;
    }

    public RunResult run(ProfilePlan plan) throws Exception {
        String startedAt = Instant.now().toString();
        List<String> queryIds = cache.queryIds();
        Map<String, String> queryText = new HashMap<>();
        for (Query query : dataset.dataset().testQueries(dataset.dataset().testQrels())) {
            queryText.put(query.id(), query.text());
        }
        Map<String, Map<String, Integer>> qrels = dataset.dataset().testQrels().byQuery();
        List<String> passageIds = cache.passageIds();

        List<RunResult.TargetRun> runs = new ArrayList<>();
        try (VectorMatrix queryVectors = cache.queries()) {
            for (Tier tier : plan.tiers()) {
                for (TargetMode mode : plan.modes()) {
                    log.info("Loading tier {} into {} target", tier.id(), mode.id());
                    try (BenchmarkTarget target = factory.create(tier, mode);
                            TierDocumentSource source =
                                    new TierDocumentSource(dataset, cache, dataset.loadOrder(tier))) {
                        LoadStats load =
                                target.load(source, new LoadOptions(settings.loadBatchSize(), settings.loadTimeCap()));
                        List<RunResult.ScenarioResult> scenarios = new ArrayList<>();
                        for (Scenario scenario : plan.scenarios()) {
                            log.info("Scenario {} on tier {} ({})", scenario.name(), tier.id(), mode.id());
                            scenarios.add(runScenario(
                                    target,
                                    tier,
                                    scenario,
                                    plan,
                                    queryIds,
                                    queryText,
                                    queryVectors,
                                    qrels,
                                    passageIds));
                        }
                        runs.add(new RunResult.TargetRun(
                                target.name(),
                                tier.id(),
                                mode.id(),
                                target.describe(),
                                target.capabilities(),
                                load,
                                scenarios));
                    }
                }
            }
        }

        return new RunResult(
                RunResult.FORMAT_VERSION,
                plan.profile().name().toLowerCase(Locale.ROOT),
                settings.machineClass(),
                startedAt,
                Instant.now().toString(),
                Environment.capture(config.dataDir()),
                gitInfo(),
                new RunResult.RunConfig(
                        dataset.manifest().name(),
                        dataset.tiers().seed(),
                        cache.readState("passages").model(),
                        plan.k(),
                        plan.repetitions(),
                        settings.loadBatchSize(),
                        settings.loadTimeCap() == null
                                ? null
                                : settings.loadTimeCap().toString(),
                        plan.tiers().stream().map(Tier::id).toList(),
                        plan.modes().stream().map(TargetMode::id).toList(),
                        plan.scenarios().stream().map(Scenario::name).toList()),
                inputs(plan),
                runs);
    }

    private RunResult.ScenarioResult runScenario(
            BenchmarkTarget target,
            Tier tier,
            Scenario scenario,
            ProfilePlan plan,
            List<String> queryIds,
            Map<String, String> queryText,
            VectorMatrix queryVectors,
            Map<String, Map<String, Integer>> qrels,
            List<String> passageIds)
            throws IOException {
        boolean efSearchApplied =
                scenario.efSearch() == null || target.capabilities().getOrDefault("efSearch", false);
        Integer efSearch = efSearchApplied ? scenario.efSearch() : null;

        Pass cold = pass(target, scenario, efSearch, plan.k(), queryIds, queryText, queryVectors);
        List<Pass> timed = new ArrayList<>();
        for (int r = 0; r < plan.repetitions(); r++) {
            timed.add(pass(target, scenario, efSearch, plan.k(), queryIds, queryText, queryVectors));
        }
        Pass reference = timed.get(0);
        boolean stable = timed.stream().allMatch(p -> p.rankings().equals(reference.rankings()));

        Map<String, List<String>> rankings = new LinkedHashMap<>();
        for (int q = 0; q < queryIds.size(); q++) {
            rankings.put(queryIds.get(q), reference.rankings().get(q));
        }
        IrMetrics.IrScores ir = IrMetrics.evaluate(rankings, qrels);

        RunResult.AnnRecall annRecall = null;
        if (scenario.mode() == SearchMode.DENSE) {
            Neighbours exact = groundTruth.load(tier, scenario.bucketsBelow());
            List<List<String>> truth = new ArrayList<>();
            for (int q = 0; q < exact.queries(); q++) {
                List<String> ids = new ArrayList<>(exact.k());
                for (int rank = 0; rank < exact.k(); rank++) {
                    int row = exact.row(q, rank);
                    ids.add(row < 0 ? null : passageIds.get(row));
                }
                truth.add(ids);
            }
            annRecall = new RunResult.AnnRecall(
                    AnnRecall.mean(reference.rankings(), truth, 10),
                    AnnRecall.mean(reference.rankings(), truth, Math.min(100, exact.k())));
        }

        int[] counts = reference.rankings().stream().mapToInt(List::size).toArray();
        int failed = 0;
        int empty = 0;
        List<String> examples = new ArrayList<>();
        for (SearchResult result : reference.results()) {
            if (result.failed()) {
                failed++;
                if (examples.size() < 3) {
                    examples.add(result.failure());
                }
            } else if (result.ids().isEmpty()) {
                empty++;
            }
        }

        List<LatencyStats> repetitions =
                timed.stream().map(p -> LatencyStats.ofNanos(p.nanos())).toList();
        RunResult.LatencySpread spread = new RunResult.LatencySpread(
                Spread.of(repetitions.stream().mapToDouble(LatencyStats::p50).toArray()),
                Spread.of(repetitions.stream().mapToDouble(LatencyStats::p95).toArray()),
                Spread.of(repetitions.stream().mapToDouble(LatencyStats::p99).toArray()));

        return new RunResult.ScenarioResult(
                scenario.name(),
                scenario.mode().name().toLowerCase(Locale.ROOT),
                scenario.bucketsBelow(),
                scenario.efSearch(),
                efSearchApplied,
                new RunResult.Accuracy(ir.ndcgAt10(), ir.recallAt10(), ir.recallAt100(), ir.mrrAt10(), ir.queries()),
                annRecall,
                ResultCounts.shortfall(counts, plan.k()),
                new RunResult.Failures(queryIds.size(), failed, empty, examples),
                LatencyStats.ofNanos(cold.nanos()),
                repetitions,
                spread,
                stable);
    }

    private record Pass(List<SearchResult> results, List<List<String>> rankings, long[] nanos) {}

    private static Pass pass(
            BenchmarkTarget target,
            Scenario scenario,
            Integer efSearch,
            int k,
            List<String> queryIds,
            Map<String, String> queryText,
            VectorMatrix queryVectors) {
        int n = queryIds.size();
        List<SearchResult> results = new ArrayList<>(n);
        List<List<String>> rankings = new ArrayList<>(n);
        long[] nanos = new long[n];
        float[] vector = new float[queryVectors.dimension()];
        for (int q = 0; q < n; q++) {
            queryVectors.get(q, vector);
            SearchRequest request = new SearchRequest(
                    scenario.mode(),
                    vector.clone(),
                    queryText.get(queryIds.get(q)),
                    k,
                    scenario.bucketsBelow(),
                    efSearch);
            long start = System.nanoTime();
            SearchResult result = target.search(request);
            nanos[q] = System.nanoTime() - start;
            results.add(result);
            rankings.add(result.ids());
        }
        return new Pass(results, rankings, nanos);
    }

    private RunResult.Inputs inputs(ProfilePlan plan) throws IOException {
        Map<String, String> tierSha = new LinkedHashMap<>();
        Map<String, String> groundTruthSha = new LinkedHashMap<>();
        for (Tier tier : plan.tiers()) {
            IdList list = dataset.tiers().tiers().get(tier.id());
            tierSha.put(tier.id(), list.sha256());
            for (Scenario scenario : plan.scenarios()) {
                if (scenario.mode() == SearchMode.DENSE) {
                    GroundTruthManifest manifest = groundTruth.manifest(tier, scenario.bucketsBelow());
                    groundTruthSha.put(manifest.name(), manifest.rowsSha256());
                }
            }
        }
        CacheState passages = cache.readState("passages");
        CacheState queries = cache.readState("queries");
        return new RunResult.Inputs(
                dataset.manifestSha256(),
                tierSha,
                passages.vectorsSha256(),
                passages.rows(),
                queries.vectorsSha256(),
                queries.latency(),
                groundTruthSha);
    }

    private RunResult.GitInfo gitInfo() {
        Path root = BenchmarkConfig.findRepositoryRoot(Path.of("")).orElse(null);
        if (root == null) {
            return new RunResult.GitInfo(null, false, false);
        }
        String commit = Environment.command("git", "-C", root.toString(), "rev-parse", "HEAD");
        String status = Environment.command("git", "-C", root.toString(), "status", "--porcelain");
        String storeStatus = Environment.command(
                "git",
                "-C",
                root.toString(),
                "status",
                "--porcelain",
                "--",
                "embedding-stores/langchain4j-community-arcadedb");
        return new RunResult.GitInfo(commit, status != null, storeStatus != null);
    }
}
