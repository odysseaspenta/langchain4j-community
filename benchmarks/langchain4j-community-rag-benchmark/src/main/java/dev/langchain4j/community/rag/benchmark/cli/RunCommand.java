package dev.langchain4j.community.rag.benchmark.cli;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.dataset.DatasetPreparer;
import dev.langchain4j.community.rag.benchmark.dataset.DatasetSource;
import dev.langchain4j.community.rag.benchmark.dataset.Downloader;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingModelSpec;
import dev.langchain4j.community.rag.benchmark.groundtruth.GroundTruth;
import dev.langchain4j.community.rag.benchmark.report.ResultWriter;
import dev.langchain4j.community.rag.benchmark.report.RunResult;
import dev.langchain4j.community.rag.benchmark.runner.Profile;
import dev.langchain4j.community.rag.benchmark.runner.ProfilePlan;
import dev.langchain4j.community.rag.benchmark.runner.Runner;
import dev.langchain4j.community.rag.benchmark.runner.TargetMode;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbSettings;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbTarget;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbVersion;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
        name = "run",
        description = "Run a benchmark profile: prepares the dataset, embeddings and ground truth it needs"
                + " (cached steps are skipped), then writes result.json and report.md.")
class RunCommand implements Callable<Integer> {

    static final int EXIT_UNSUPPORTED = 2;

    @ParentCommand
    BenchmarkCli cli;

    @Spec
    CommandSpec spec;

    @Option(
            names = "--profile",
            required = true,
            paramLabel = "<profile>",
            description = "One of: ${COMPLETION-CANDIDATES} (case-insensitive).")
    Profile profile;

    @Option(names = "--dataset", defaultValue = "nq", paramLabel = "<name>", description = "Dataset (default: nq).")
    String dataset;

    @Option(
            names = "--load-batch-size",
            defaultValue = "1000",
            paramLabel = "<n>",
            description = "Documents per addAll call (default: 1000).")
    int loadBatchSize;

    @Option(
            names = "--load-time-cap-minutes",
            paramLabel = "<minutes>",
            description = "Stop loading after this many minutes and report partial numbers (baseline runs, PRD R1).")
    Integer loadTimeCapMinutes;

    @Option(
            names = "--threads",
            paramLabel = "<n>",
            description = "Threads for embedding and ground truth (default: available processors).")
    Integer threads;

    @Option(names = "--keep-databases", description = "Keep the loaded databases instead of deleting them.")
    boolean keepDatabases;

    @Override
    public Integer call() throws Exception {
        ProfilePlan plan;
        try {
            plan = ProfilePlan.of(profile);
        } catch (UnsupportedOperationException e) {
            spec.commandLine().getErr().println(e.getMessage());
            return EXIT_UNSUPPORTED;
        }
        BenchmarkConfig config = cli.config();
        int threadCount = threads != null ? threads : Runtime.getRuntime().availableProcessors();
        DatasetSource source = DatasetSource.byName(dataset)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dataset '" + dataset + "'"));

        PreparedDataset prepared =
                new DatasetPreparer(config.dataDir(), new Downloader()).prepare(source, config.seed());
        EmbeddingCache cache = new EmbeddingCache(config.dataDir(), prepared, EmbeddingModelSpec.BGE_SMALL_EN_V15);
        int maxTier = plan.tiers().stream()
                .mapToInt(tier -> tier.size(prepared.manifest().passages()))
                .max()
                .orElseThrow();
        cache.embedPassages(maxTier, threadCount);
        cache.embedQueries();
        GroundTruth groundTruth = new GroundTruth(config.dataDir(), prepared, cache, plan.k());
        groundTruth.compute(EnumSet.copyOf(plan.tiers()), threadCount);

        String version = ArcadeDbVersion.onClasspath();
        Path databases = config.dataDir().resolve("databases").resolve("arcadedb-" + version);
        List<Path> created = new ArrayList<>();
        Runner.TargetFactory factory = (Tier tier, TargetMode mode) -> {
            if (mode != TargetMode.EMBEDDED) {
                throw new UnsupportedOperationException(
                        "Remote mode is not implemented yet; see plans/issues/B08-remote-target.md");
            }
            Path dir = databases.resolve(tier.id() + "-" + mode.id());
            created.add(dir);
            return ArcadeDbTarget.embedded(
                    dir, ArcadeDbSettings.pinned(EmbeddingModelSpec.BGE_SMALL_EN_V15.dimension()));
        };
        Runner.Settings settings = new Runner.Settings(
                loadBatchSize,
                loadTimeCapMinutes == null ? null : Duration.ofMinutes(loadTimeCapMinutes),
                config.machineClass());

        RunResult result;
        try {
            result = new Runner(config, prepared, cache, groundTruth, factory, settings).run(plan);
        } finally {
            if (!keepDatabases) {
                for (Path dir : created) {
                    deleteRecursively(dir);
                }
            }
        }
        Path runDir = ResultWriter.write(config.resultsDir(), "arcadedb-" + version, result);

        PrintWriter out = spec.commandLine().getOut();
        out.println("Results: " + runDir);
        out.println(Files.readString(runDir.resolve("report.md")));
        out.flush();
        return 0;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
