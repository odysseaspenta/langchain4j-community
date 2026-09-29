package dev.langchain4j.community.rag.benchmark.cli;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.dataset.DatasetPreparer;
import dev.langchain4j.community.rag.benchmark.dataset.DatasetSource;
import dev.langchain4j.community.rag.benchmark.dataset.Downloader;
import dev.langchain4j.community.rag.benchmark.dataset.IdList;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "prepare", description = "Download and verify the BEIR dataset and build the tier id lists.")
class PrepareCommand implements Callable<Integer> {

    @ParentCommand
    BenchmarkCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--dataset", defaultValue = "nq", paramLabel = "<name>", description = "Dataset (default: nq).")
    String dataset;

    @Override
    public Integer call() throws Exception {
        DatasetSource source = DatasetSource.byName(dataset)
                .orElseThrow(() -> new ParameterException(spec.commandLine(), "Unknown dataset '" + dataset + "'"));
        BenchmarkConfig config = cli.config();

        PreparedDataset prepared =
                new DatasetPreparer(config.dataDir(), new Downloader()).prepare(source, config.seed());

        PrintWriter out = spec.commandLine().getOut();
        out.printf(
                "%s @ %s: %d passages, %d test queries, %d relevant passages%n",
                prepared.manifest().repository(),
                prepared.manifest().revision(),
                prepared.manifest().passages(),
                prepared.manifest().testQueries(),
                prepared.manifest().relevantPassages());
        for (Map.Entry<String, IdList> tier : prepared.tiers().tiers().entrySet()) {
            out.printf(
                    "  %-8s %,10d passages  sha256 %s%n",
                    tier.getKey(), tier.getValue().size(), tier.getValue().sha256());
        }
        out.println("Tier files: " + prepared.tiersDir());
        out.flush();
        return 0;
    }
}
