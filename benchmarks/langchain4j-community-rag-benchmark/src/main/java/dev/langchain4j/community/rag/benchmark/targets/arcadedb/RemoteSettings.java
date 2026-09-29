package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import dev.langchain4j.community.rag.benchmark.util.CpuSet;
import java.time.Duration;

/**
 * Remote (Docker) mode settings (PRD F17).
 *
 * @param image        Docker image; {@link ArcadeDbVersion#dockerImage()} keeps it in step with the jars (F13)
 * @param serverCpus   cpuset of the server container, disjoint from the client's CPUs
 * @param serverHeap   server heap, e.g. {@code 4g}
 * @param queryTimeout server-side limit per query ({@code arcadedb.command.timeout}); a query running longer is
 *                     aborted on the server, so a pathological query cannot keep a CPU busy for later ones
 */
public record RemoteSettings(String image, CpuSet serverCpus, String serverHeap, Duration queryTimeout) {

    public static final String DEFAULT_HEAP = "4g";
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Default server CPUs: the CPUs the client may not use when it is pinned (e.g. by {@code RAG_BENCH_CLIENT_CPUS});
     * otherwise the upper half of the client's CPUs.
     */
    public static CpuSet defaultServerCpus(CpuSet clientCpus, CpuSet onlineCpus) {
        CpuSet rest = onlineCpus.minus(clientCpus);
        return rest.size() > 0 ? rest : clientCpus.upperHalf();
    }
}
