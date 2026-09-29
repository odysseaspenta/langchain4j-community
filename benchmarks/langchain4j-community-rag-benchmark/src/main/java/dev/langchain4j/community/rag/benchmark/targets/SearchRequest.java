package dev.langchain4j.community.rag.benchmark.targets;

/**
 * @param vector       query embedding
 * @param text         query text, used by {@link SearchMode#HYBRID}
 * @param k            results wanted
 * @param bucketsBelow metadata filter {@code bucket < bucketsBelow}, or {@code null}
 * @param efSearch     search depth, or {@code null} for the store default; a target that cannot honour it says so
 *                     in {@link BenchmarkTarget#capabilities()}
 */
public record SearchRequest(
        SearchMode mode, float[] vector, String text, int k, Integer bucketsBelow, Integer efSearch) {}
