package dev.langchain4j.community.rag.benchmark.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ANN recall@k against exact neighbours (PRD M1): the share of the exact top k that the store returned in its top
 * k. When a filter leaves fewer than k candidates, the denominator is the number that exist; queries with none are
 * skipped.
 */
public final class AnnRecall {

    private AnnRecall() {}

    /**
     * @param retrieved ids returned by the store, best first
     * @param exact     exact neighbour ids, best first; {@code null} entries mark padding
     * @return recall in [0, 1], or {@code NaN} when {@code exact} has no entries in its top k
     */
    public static double recall(List<String> retrieved, List<String> exact, int k) {
        Set<String> truth = new HashSet<>();
        for (String id : exact.subList(0, Math.min(k, exact.size()))) {
            if (id != null) {
                truth.add(id);
            }
        }
        if (truth.isEmpty()) {
            return Double.NaN;
        }
        long found = IrMetrics.topDistinct(retrieved, k).stream()
                .filter(Objects::nonNull)
                .filter(truth::contains)
                .count();
        return (double) found / truth.size();
    }

    /**
     * Mean recall@k over queries, skipping queries whose recall is undefined.
     */
    public static double mean(List<List<String>> retrieved, List<List<String>> exact, int k) {
        if (retrieved.size() != exact.size()) {
            throw new IllegalArgumentException("retrieved and exact must cover the same queries");
        }
        double sum = 0;
        int counted = 0;
        for (int q = 0; q < retrieved.size(); q++) {
            double recall = recall(retrieved.get(q), exact.get(q), k);
            if (!Double.isNaN(recall)) {
                sum += recall;
                counted++;
            }
        }
        return counted == 0 ? Double.NaN : sum / counted;
    }
}
