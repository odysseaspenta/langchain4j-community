package dev.langchain4j.community.rag.benchmark.groundtruth;

/**
 * Exact nearest neighbours for every query, best first. {@code rows[q * k + i]} is a row of the embedding cache
 * (priority order); {@code -1} with score {@code NaN} pads queries with fewer than {@code k} candidates.
 */
public record Neighbours(int queries, int k, int[] rows, float[] scores) {

    public int row(int query, int rank) {
        return rows[query * k + rank];
    }

    public float score(int query, int rank) {
        return scores[query * k + rank];
    }
}
