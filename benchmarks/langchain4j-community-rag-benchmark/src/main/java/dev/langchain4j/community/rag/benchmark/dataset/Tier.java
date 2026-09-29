package dev.langchain4j.community.rag.benchmark.dataset;

import java.util.Locale;

/**
 * Corpus size tiers (PRD D5). Smaller tiers are subsets of larger ones.
 */
public enum Tier {
    SMOKE(100_000),
    STANDARD(1_000_000),
    FULL(Integer.MAX_VALUE);

    private final int maxSize;

    Tier(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Number of passages in this tier for a corpus of {@code corpusSize} passages.
     */
    public int size(int corpusSize) {
        return Math.min(maxSize, corpusSize);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
