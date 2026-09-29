package dev.langchain4j.community.rag.benchmark.cli;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingModelSpec;
import dev.langchain4j.community.rag.benchmark.groundtruth.GroundTruth;
import dev.langchain4j.community.rag.benchmark.groundtruth.GroundTruthManifest;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
        name = "ground-truth",
        description = "Compute exact top-k neighbours per query and tier, unfiltered and per bucket filter.")
class GroundTruthCommand implements Callable<Integer> {

    @ParentCommand
    BenchmarkCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--dataset", defaultValue = "nq", paramLabel = "<name>", description = "Dataset (default: nq).")
    String dataset;

    @Option(
            names = "--tier",
            defaultValue = "smoke",
            split = ",",
            paramLabel = "<tier>",
            description = "Tiers, comma-separated or repeated: ${COMPLETION-CANDIDATES} (default: smoke)."
                    + " Several tiers share one pass.")
    List<Tier> tiers;

    @Option(
            names = "--k",
            defaultValue = "100",
            paramLabel = "<k>",
            description = "Neighbours per query (default: 100).")
    int k;

    @Option(names = "--threads", paramLabel = "<n>", description = "Threads (default: available processors).")
    Integer threads;

    @Override
    public Integer call() throws Exception {
        BenchmarkConfig config = cli.config();
        PreparedDataset prepared = PreparedDataset.open(config.dataDir(), dataset, config.seed());
        EmbeddingCache cache = new EmbeddingCache(config.dataDir(), prepared, EmbeddingModelSpec.BGE_SMALL_EN_V15);
        GroundTruth groundTruth = new GroundTruth(config.dataDir(), prepared, cache, k);
        int threadCount = threads != null ? threads : Runtime.getRuntime().availableProcessors();

        List<GroundTruthManifest> manifests = groundTruth.compute(EnumSet.copyOf(tiers), threadCount);

        PrintWriter out = spec.commandLine().getOut();
        out.println("Ground truth: " + groundTruth.dir());
        for (GroundTruthManifest manifest : manifests) {
            out.printf(
                    "  %-28s %,10d passages  %d queries  k=%d  %.1f s%n",
                    manifest.name(), manifest.passages(), manifest.queries(), manifest.k(), manifest.seconds());
        }
        out.flush();
        return 0;
    }
}
