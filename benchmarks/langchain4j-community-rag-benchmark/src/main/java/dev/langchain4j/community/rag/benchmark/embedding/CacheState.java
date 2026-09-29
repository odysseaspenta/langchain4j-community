package dev.langchain4j.community.rag.benchmark.embedding;

/**
 * State of one cached vector set ({@code passages.json} / {@code queries.json}), rewritten after every committed
 * chunk. The identity fields decide whether the cache may be reused; {@code rows} and {@code idsBytes} say how much
 * of the {@code .f32} / {@code .ids} files is committed (anything beyond is discarded on resume).
 *
 * @param vectorsSha256 SHA-256 of the committed vectors; {@code null} while a run is in progress
 * @param throughput    passages (or queries) per second of the last run
 * @param latency       single-query embedding latency (queries only, PRD M9), otherwise {@code null}
 */
public record CacheState(
        int formatVersion,
        String model,
        String modelSha256,
        int dimension,
        boolean normalized,
        String prefix,
        String datasetManifestSha256,
        long seed,
        String orderSha256,
        int rows,
        long idsBytes,
        String vectorsSha256,
        String idsSha256,
        Integer threads,
        Double throughput,
        Latency latency) {

    public static final int FORMAT_VERSION = 1;

    public boolean sameIdentity(CacheState other) {
        return formatVersion == other.formatVersion
                && model.equals(other.model)
                && modelSha256.equals(other.modelSha256)
                && dimension == other.dimension
                && normalized == other.normalized
                && prefix.equals(other.prefix)
                && datasetManifestSha256.equals(other.datasetManifestSha256)
                && seed == other.seed
                && orderSha256.equals(other.orderSha256);
    }

    CacheState committed(int rows, long idsBytes) {
        return new CacheState(
                formatVersion,
                model,
                modelSha256,
                dimension,
                normalized,
                prefix,
                datasetManifestSha256,
                seed,
                orderSha256,
                rows,
                idsBytes,
                null,
                null,
                threads,
                throughput,
                latency);
    }

    CacheState completed(String vectorsSha256, String idsSha256, Integer threads, Double throughput, Latency latency) {
        return new CacheState(
                formatVersion,
                model,
                modelSha256,
                dimension,
                normalized,
                prefix,
                datasetManifestSha256,
                seed,
                orderSha256,
                rows,
                idsBytes,
                vectorsSha256,
                idsSha256,
                threads,
                throughput,
                latency);
    }

    /**
     * Latency of embedding one query at a time on one thread, in milliseconds.
     */
    public record Latency(int samples, double mean, double p50, double p95, double p99, double max) {}
}
