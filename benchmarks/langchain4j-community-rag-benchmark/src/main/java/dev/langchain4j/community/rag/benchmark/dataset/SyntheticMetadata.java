package dev.langchain4j.community.rag.benchmark.dataset;

import dev.langchain4j.community.rag.benchmark.util.Seeds;

/**
 * Synthetic metadata for filtered-search scenarios (PRD F12).
 */
public final class SyntheticMetadata {

    public static final String BUCKET = "bucket";
    public static final int BUCKETS = 100;

    private SyntheticMetadata() {}

    /**
     * Returns the passage's bucket in {@code [0, 100)}: {@code floorMod(Seeds.hash(seed, passageId), 100)}.
     * Filters {@code bucket < 1}, {@code < 10} and {@code < 50} select about 1%, 10% and 50% of any tier.
     */
    public static int bucket(String passageId, long seed) {
        return (int) Math.floorMod(Seeds.hash(seed, passageId), (long) BUCKETS);
    }
}
