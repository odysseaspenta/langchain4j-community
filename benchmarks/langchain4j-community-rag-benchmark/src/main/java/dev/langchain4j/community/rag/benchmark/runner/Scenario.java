package dev.langchain4j.community.rag.benchmark.runner;

import dev.langchain4j.community.rag.benchmark.targets.SearchMode;

/**
 * One way of querying a loaded index (PRD §7.4).
 *
 * @param bucketsBelow metadata filter {@code bucket < bucketsBelow}, or {@code null}
 * @param efSearch     requested search depth, or {@code null} for the store default
 */
public record Scenario(String name, SearchMode mode, Integer bucketsBelow, Integer efSearch) {

    public static final Scenario DENSE = new Scenario("dense", SearchMode.DENSE, null, null);
    public static final Scenario HYBRID_AS_IS = new Scenario("hybrid-asis", SearchMode.HYBRID, null, null);
}
