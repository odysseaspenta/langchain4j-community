package dev.langchain4j.community.rag.benchmark.cli;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.CacheState;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingModelSpec;
import dev.langchain4j.community.rag.benchmark.metrics.LatencyStats;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
        name = "embed",
        description = "Embed passages (in tier priority order) and test queries into the embedding cache."
                + " Interrupted runs resume where they stopped.")
class EmbedCommand implements Callable<Integer> {

    @ParentCommand
    BenchmarkCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--dataset", defaultValue = "nq", paramLabel = "<name>", description = "Dataset (default: nq).")
    String dataset;

    @Option(
            names = "--tier",
            defaultValue = "smoke",
            paramLabel = "<tier>",
            description = "Tier to cover: ${COMPLETION-CANDIDATES} (default: smoke).")
    Tier tier;

    @Option(names = "--rows", paramLabel = "<n>", description = "Cover exactly the first n passages instead of a tier.")
    Integer rows;

    @Option(names = "--threads", paramLabel = "<n>", description = "Embedding threads (default: available processors).")
    Integer threads;

    @Option(
            names = "--verify",
            defaultValue = "20",
            paramLabel = "<n>",
            description = "Re-embed n random cached passages and require exact equality (default: 20; 0 = skip).")
    int verify;

    @Override
    public Integer call() throws Exception {
        BenchmarkConfig config = cli.config();
        PreparedDataset prepared = PreparedDataset.open(config.dataDir(), dataset, config.seed());
        EmbeddingCache cache = new EmbeddingCache(config.dataDir(), prepared, EmbeddingModelSpec.BGE_SMALL_EN_V15);
        int target = rows != null ? rows : tier.size(prepared.manifest().passages());
        int threadCount = threads != null ? threads : Runtime.getRuntime().availableProcessors();

        CacheState passages = cache.embedPassages(target, threadCount);
        CacheState queries = cache.embedQueries();

        PrintWriter out = spec.commandLine().getOut();
        out.printf("Cache: %s%n", cache.dir());
        out.printf(
                "Passages: %,d rows, %.1f passages/s (%d threads), sha256 %s%n",
                passages.rows(), passages.throughput(), passages.threads(), passages.vectorsSha256());
        LatencyStats latency = queries.latency();
        out.printf(
                "Queries:  %,d rows, single-query latency p50 %.2f ms, p95 %.2f ms, p99 %.2f ms, sha256 %s%n",
                queries.rows(), latency.p50(), latency.p95(), latency.p99(), queries.vectorsSha256());
        int exitCode = 0;
        if (verify > 0) {
            EmbeddingCache.VerifyResult result = cache.verifyPassages(verify, config.seed());
            out.printf(
                    "Verify:   %d sampled passages re-embedded, %d mismatches, max |diff| %.3g%n",
                    result.samples(), result.mismatches(), result.maxAbsDiff());
            exitCode = result.mismatches() == 0 ? 0 : 1;
        }
        out.flush();
        return exitCode;
    }
}
