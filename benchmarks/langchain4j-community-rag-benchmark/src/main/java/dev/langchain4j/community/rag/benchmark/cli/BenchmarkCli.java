package dev.langchain4j.community.rag.benchmark.cli;

import static picocli.CommandLine.ScopeType.INHERIT;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig.Setting;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "rag-bench",
        mixinStandardHelpOptions = true,
        description = "RAG retrieval benchmark for the LangChain4j ArcadeDB embedding store.",
        subcommands = {
            InfoCommand.class,
            PrepareCommand.class,
            EmbedCommand.class,
            GroundTruthCommand.class,
            RunCommand.class,
            CompareCommand.class,
            HelpCommand.class
        })
public class BenchmarkCli implements Runnable {

    @Spec
    CommandSpec spec;

    @Option(
            names = "--data-dir",
            scope = INHERIT,
            paramLabel = "<dir>",
            description = "Datasets, caches and databases; outside the repo (env RAG_BENCH_DATA_DIR,"
                    + " -Drag.bench.dataDir, default ~/.cache/langchain4j-rag-benchmark).")
    String dataDir;

    @Option(
            names = "--results-dir",
            scope = INHERIT,
            paramLabel = "<dir>",
            description = "Run results (env RAG_BENCH_RESULTS_DIR, -Drag.bench.resultsDir,"
                    + " default <repo>/benchmarks/results).")
    String resultsDir;

    @Option(
            names = "--seed",
            scope = INHERIT,
            paramLabel = "<long>",
            description =
                    "Seed for sampling and synthetic metadata (env RAG_BENCH_SEED, -Drag.bench.seed, default 42).")
    String seed;

    public static void main(String[] args) {
        System.exit(newCommandLine().execute(args));
    }

    public static CommandLine newCommandLine() {
        return new CommandLine(new BenchmarkCli()).setCaseInsensitiveEnumValuesAllowed(true);
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    BenchmarkConfig config() {
        Map<Setting, String> cli = new EnumMap<>(Setting.class);
        putIfPresent(cli, Setting.DATA_DIR, dataDir);
        putIfPresent(cli, Setting.RESULTS_DIR, resultsDir);
        putIfPresent(cli, Setting.SEED, seed);
        return BenchmarkConfig.resolve(cli, System.getProperties(), System.getenv(), Path.of(""));
    }

    private static void putIfPresent(Map<Setting, String> map, Setting setting, String value) {
        if (value != null) {
            map.put(setting, value);
        }
    }
}
