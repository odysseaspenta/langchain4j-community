package dev.langchain4j.community.rag.benchmark.embedding;

import dev.langchain4j.community.rag.benchmark.metrics.LatencyStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * State of one cached vector set ({@code passages.json} / {@code queries.json}), rewritten after every committed
 * chunk. The identity fields decide whether the cache may be reused; {@code rows} and {@code idsBytes} say how much
 * of the {@code .f32} / {@code .ids} files is committed (anything beyond is discarded on resume).
 *
 * @param vectorsSha256 SHA-256 of the committed vectors; {@code null} while a run is in progress
 * @param throughput    passages (or queries) per second of the last run
 * @param latency       single-query embedding latency (queries only, PRD M9), otherwise {@code null}
 * @param segments      which backend embedded which rows (PRD A4); {@code null} in caches written before segments were
 *                      recorded, which were embedded entirely in-process ({@link #IN_PROCESS})
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
        LatencyStats latency,
        List<Segment> segments) {

    public static final int FORMAT_VERSION = 1;

    /** Backend id of LangChain4j's in-process ONNX model. */
    public static final String IN_PROCESS = "onnx-in-process";

    /** Backend id of an external embedding server (PRD A4); rows it could not embed are embedded in-process. */
    public static final String HTTP = "http";

    /**
     * Rows {@code [fromRow, toRow)} embedded by one backend in one run.
     *
     * @param details backend settings and checks, e.g. the server's {@code /health} and the number of rows embedded
     *                in-process because the server refused them
     */
    public record Segment(int fromRow, int toRow, String backend, Map<String, Object> details) {}

    /** The backend that embedded {@code row}. */
    public String backendOf(int row) {
        if (segments != null) {
            for (Segment segment : segments) {
                if (row >= segment.fromRow() && row < segment.toRow()) {
                    return segment.backend();
                }
            }
        }
        return IN_PROCESS;
    }

    /**
     * Records that rows {@code [runFrom, toRow)} were embedded by {@code backend}, extending this run's segment if it
     * is the last one.
     */
    CacheState withSegment(int runFrom, int toRow, String backend, Map<String, Object> details) {
        List<Segment> updated = new ArrayList<>(segments == null ? List.of() : segments);
        if (!updated.isEmpty()) {
            Segment last = updated.get(updated.size() - 1);
            if (last.fromRow() == runFrom && last.backend().equals(backend)) {
                updated.remove(updated.size() - 1);
            }
        }
        updated.add(new Segment(runFrom, toRow, backend, details));
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
                latency,
                List.copyOf(updated));
    }

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
                latency,
                segments);
    }

    CacheState completed(
            String vectorsSha256, String idsSha256, Integer threads, Double throughput, LatencyStats latency) {
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
                latency,
                segments);
    }
}
