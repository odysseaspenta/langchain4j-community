package dev.langchain4j.community.rag.benchmark.metrics;

/**
 * Result-count shortfall and failure rate (PRD M3, M4).
 */
public final class ResultCounts {

    private ResultCounts() {}

    /**
     * Fraction of queries that returned fewer than {@code k} results.
     */
    public static double shortfall(int[] resultCounts, int k) {
        if (resultCounts.length == 0) {
            return 0;
        }
        int shortCount = 0;
        for (int count : resultCounts) {
            if (count < k) {
                shortCount++;
            }
        }
        return (double) shortCount / resultCounts.length;
    }

    /**
     * Failure counts for one scenario run. Exceptions include full-text parse errors; {@code empty} counts queries
     * that returned no results without an exception.
     */
    public record Failures(int queries, int exceptions, int parseErrors, int empty) {

        public double rate() {
            return queries == 0 ? 0 : (double) (exceptions + empty) / queries;
        }
    }
}
