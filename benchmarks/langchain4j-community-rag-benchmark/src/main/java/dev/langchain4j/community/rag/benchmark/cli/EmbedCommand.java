package dev.langchain4j.community.rag.benchmark.cli;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.CacheState;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingModelSpec;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingServer;
import dev.langchain4j.community.rag.benchmark.metrics.LatencyStats;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Duration;
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
            names = "--backend",
            defaultValue = "in-process",
            paramLabel = "<backend>",
            description = "Passage embedding backend: in-process (LangChain4j ONNX on the CPU) or server (an embedding"
                    + " server with the same model, PRD A4; see gpu-embedder/). Queries are always in-process."
                    + " Default: in-process.")
    String backend;

    @Option(
            names = "--endpoint",
            defaultValue = "http://127.0.0.1:8080",
            paramLabel = "<url>",
            description = "Embedding server for --backend server (default: ${DEFAULT-VALUE}).")
    URI endpoint;

    @Option(
            names = "--server-batch",
            defaultValue = "" + EmbeddingCache.DEFAULT_SERVER_BATCH,
            paramLabel = "<n>",
            description = "Passages per server request (default: ${DEFAULT-VALUE}).")
    int serverBatch;

    @Option(
            names = "--verify",
            defaultValue = "20",
            paramLabel = "<n>",
            description = "Re-embed n random cached passages in-process: rows embedded in-process must be identical,"
                    + " rows from an embedding server must have cosine >= 0.999 (default: 20; 0 = skip).")
    int verify;

    @Override
    public Integer call() throws Exception {
        BenchmarkConfig config = cli.config();
        PreparedDataset prepared = PreparedDataset.open(config.dataDir(), dataset, config.seed());
        EmbeddingCache cache = new EmbeddingCache(config.dataDir(), prepared, EmbeddingModelSpec.BGE_SMALL_EN_V15);
        int target = rows != null ? rows : tier.size(prepared.manifest().passages());
        int threadCount = threads != null ? threads : Runtime.getRuntime().availableProcessors();

        CacheState passages;
        switch (backend) {
            case "in-process" -> passages = cache.embedPassages(target, threadCount);
            case "server" ->
                passages = cache.embedPassagesRemote(
                        target,
                        threadCount,
                        new EmbeddingServer(endpoint, Duration.ofMinutes(10)),
                        EmbeddingServer.HubModel.BGE_SMALL_EN_V15,
                        serverBatch,
                        EmbeddingCache.DEFAULT_SERVER_WINDOW);
            default -> {
                spec.commandLine().getErr().println("Unknown backend '" + backend + "': use in-process or server");
                return 2;
            }
        }
        CacheState queries = cache.embedQueries();

        PrintWriter out = spec.commandLine().getOut();
        out.printf("Cache: %s%n", cache.dir());
        out.printf(
                "Passages: %,d rows, %.1f passages/s (%s), sha256 %s%n",
                passages.rows(),
                passages.throughput(),
                lastBackend(passages),
                passages.vectorsSha256());
        LatencyStats latency = queries.latency();
        out.printf(
                "Queries:  %,d rows, single-query latency p50 %.2f ms, p95 %.2f ms, p99 %.2f ms, sha256 %s%n",
                queries.rows(), latency.p50(), latency.p95(), latency.p99(), queries.vectorsSha256());
        int exitCode = 0;
        if (verify > 0) {
            EmbeddingCache.VerifyResult result = cache.verifyPassages(verify, config.seed());
            out.printf(
                    "Verify:   %d sampled passages re-embedded in-process (%d from a server), %d mismatches,"
                            + " max |diff| %.3g, min cosine %.7f%n",
                    result.samples(),
                    result.serverRows(),
                    result.mismatches(),
                    result.maxAbsDiff(),
                    result.minCosine());
            exitCode = result.mismatches() == 0 ? 0 : 1;
        }
        out.flush();
        return exitCode;
    }

    /** Describes the run that last added rows (not this command's --backend: the cache may already be complete). */
    private static String lastBackend(CacheState passages) {
        if (passages.segments() == null || passages.segments().isEmpty()) {
            return CacheState.IN_PROCESS + (passages.threads() == null ? "" : ", " + passages.threads() + " threads");
        }
        CacheState.Segment last = passages.segments().get(passages.segments().size() - 1);
        Object threads = last.details().get("threads");
        return "last run: " + last.backend() + " for rows " + last.fromRow() + ".." + last.toRow()
                + (threads == null ? "" : ", " + threads + " threads");
    }
}
