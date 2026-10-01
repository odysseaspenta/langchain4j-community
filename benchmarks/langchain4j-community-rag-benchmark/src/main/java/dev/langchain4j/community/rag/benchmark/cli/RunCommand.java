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
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbLogging;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbSettings;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbTarget;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbVersion;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.RemoteSettings;
import dev.langchain4j.community.rag.benchmark.util.CpuSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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

    @Option(
            names = "--label",
            paramLabel = "<label>",
            description = "Appended to the results directory name, e.g. embedded for"
                    + " <date>-arcadedb-<version>-baseline-embedded.")
    String label;

    @Option(
            names = "--mode",
            split = ",",
            paramLabel = "<mode>",
            description = "Deployment modes to run, overriding the profile's: embedded, remote"
                    + " (e.g. --mode embedded,remote). Remote starts arcadedata/arcadedb:<version> in Docker.")
    List<TargetMode> modes;

    @Option(
            names = "--server-cpus",
            paramLabel = "<list>",
            description = "Remote: CPUs to pin the server container to, e.g. 4-7 (default: the CPUs the client is"
                    + " not pinned to, else the upper half).")
    String serverCpus;

    @Option(
            names = "--server-heap",
            defaultValue = RemoteSettings.DEFAULT_HEAP,
            paramLabel = "<size>",
            description = "Remote: server heap, -Xms = -Xmx (default: ${DEFAULT-VALUE}).")
    String serverHeap;

    @Option(
            names = "--query-timeout-seconds",
            paramLabel = "<seconds>",
            description = "Remote: server-side timeout per query, at most 29 (default: 25; the ArcadeDB client's HTTP/2"
                    + " connection is closed after 30 s).")
    Integer queryTimeoutSeconds;

    @Option(
            names = "--failure-streak-limit",
            defaultValue = "" + Runner.Settings.DEFAULT_FAILURE_STREAK_LIMIT,
            paramLabel = "<n>",
            description = "Abort a scenario after this many consecutive failed or empty queries; 0 disables"
                    + " (default: ${DEFAULT-VALUE}).")
    int failureStreakLimit;

    @Option(
            names = "--pass-time-cap-minutes",
            paramLabel = "<minutes>",
            description = "Abort a scenario pass after this many minutes; the remaining queries count as empty.")
    Integer passTimeCapMinutes;

    @Override
    public Integer call() throws Exception {
        ProfilePlan plan;
        try {
            plan = ProfilePlan.of(profile);
        } catch (UnsupportedOperationException e) {
            spec.commandLine().getErr().println(e.getMessage());
            return EXIT_UNSUPPORTED;
        }
        if (modes != null) {
            plan = plan.withModes(modes.stream().distinct().toList());
        }
        BenchmarkConfig config = cli.config();
        // Before any ArcadeDB class logs: keep ArcadeDB's log out of the working directory.
        ArcadeDbLogging.configure(config.dataDir().resolve("logs"));
        RemoteSettings remote = null;
        if (plan.modes().contains(TargetMode.REMOTE)) {
            CpuSet clientCpus = CpuSet.ofThisProcess();
            CpuSet serverCpuSet = serverCpus != null
                    ? CpuSet.parse(serverCpus)
                    : RemoteSettings.defaultServerCpus(clientCpus, CpuSet.online());
            if (serverCpuSet.overlaps(clientCpus)) {
                spec.commandLine()
                        .getErr()
                        .println("Warning: server CPUs " + serverCpuSet + " overlap client CPUs " + clientCpus
                                + "; pin the client with RAG_BENCH_CLIENT_CPUS (PRD F17)");
            }
            remote = new RemoteSettings(
                    ArcadeDbVersion.dockerImage(),
                    serverCpuSet,
                    serverHeap,
                    queryTimeoutSeconds == null
                            ? RemoteSettings.DEFAULT_QUERY_TIMEOUT
                            : Duration.ofSeconds(queryTimeoutSeconds));
        }
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
        ArcadeDbSettings index = ArcadeDbSettings.pinned(EmbeddingModelSpec.BGE_SMALL_EN_V15.dimension());
        RemoteSettings remoteSettings = remote;
        Runner.TargetFactory factory = (Tier tier, TargetMode mode) -> {
            Path dir = databases.resolve(tier.id() + "-" + mode.id());
            created.add(dir);
            return mode == TargetMode.REMOTE
                    ? ArcadeDbTarget.remote(dir, index, remoteSettings)
                    : ArcadeDbTarget.embedded(dir, index);
        };
        Runner.Settings settings = new Runner.Settings(
                loadBatchSize,
                loadTimeCapMinutes == null ? null : Duration.ofMinutes(loadTimeCapMinutes),
                config.machineClass(),
                failureStreakLimit,
                passTimeCapMinutes == null ? null : Duration.ofMinutes(passTimeCapMinutes));

        // Results are rewritten after every load, so a run that dies later keeps what it measured.
        Path runDir = ResultWriter.createRunDir(
                config.resultsDir(), "arcadedb-" + version, plan.profile().name().toLowerCase(Locale.ROOT), label);
        spec.commandLine().getOut().println("Results (updated after every load): " + runDir);
        spec.commandLine().getOut().flush();
        RunResult result;
        try {
            result = new Runner(config, prepared, cache, groundTruth, factory, settings)
                    .run(plan, partial -> ResultWriter.writeTo(runDir, partial));
        } finally {
            if (!keepDatabases) {
                for (Path dir : created) {
                    deleteRecursively(dir);
                }
            }
        }
        ResultWriter.writeTo(runDir, result);

        PrintWriter out = spec.commandLine().getOut();
        out.println("Results: " + runDir + " (" + result.status() + ")");
        out.println(Files.readString(runDir.resolve("report.md")));
        out.flush();
        return RunResult.COMPLETE.equals(result.status()) ? 0 : 1;
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
