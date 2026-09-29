package dev.langchain4j.community.rag.benchmark.metrics;

import java.util.Arrays;

/**
 * Latency distribution in milliseconds (PRD M6). Percentiles use the nearest-rank method: the smallest sample with
 * at least {@code p} of the samples at or below it.
 */
public record LatencyStats(int samples, double mean, double p50, double p95, double p99, double max) {

    public static LatencyStats ofNanos(long[] nanos) {
        if (nanos.length == 0) {
            throw new IllegalArgumentException("No latency samples");
        }
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double sum = 0;
        for (long n : sorted) {
            sum += n;
        }
        return new LatencyStats(
                sorted.length,
                sum / sorted.length / 1e6,
                percentile(sorted, 50),
                percentile(sorted, 95),
                percentile(sorted, 99),
                sorted[sorted.length - 1] / 1e6);
    }

    static double percentile(long[] sorted, double p) {
        int rank = (int) Math.ceil(p / 100 * sorted.length);
        return sorted[Math.max(0, rank - 1)] / 1e6;
    }
}
