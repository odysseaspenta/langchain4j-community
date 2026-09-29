package dev.langchain4j.community.rag.benchmark.targets;

/**
 * Ingestion measurements (PRD M5).
 *
 * @param requested               documents offered
 * @param loaded                  documents added before the time cap (equal to {@code requested} when not capped)
 * @param capped                  whether the time cap stopped loading
 * @param loadSeconds             wall time of the {@code addAll} calls
 * @param vectorsPerSecond        {@code loaded / loadSeconds}
 * @param extrapolatedLoadSeconds {@code requested / vectorsPerSecond}; equals {@code loadSeconds} when not capped
 * @param timeToSearchableSeconds from the end of loading until the index is fully built and searchable
 * @param diskBytes               on-disk size after loading, or -1 if not applicable
 * @param peakHeapBytes           peak heap of this JVM during loading and index build
 * @param serverPeakMemoryBytes   peak memory of a separate server process (heap plus off-heap), or -1 when the
 *                                store runs in this JVM
 */
public record LoadStats(
        int requested,
        int loaded,
        boolean capped,
        double loadSeconds,
        double vectorsPerSecond,
        double extrapolatedLoadSeconds,
        double timeToSearchableSeconds,
        long diskBytes,
        long peakHeapBytes,
        long serverPeakMemoryBytes) {}
