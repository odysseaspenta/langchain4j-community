package dev.langchain4j.community.rag.benchmark.targets;

/**
 * How a query is answered (PRD §7.4).
 */
public enum SearchMode {
    /** Vector search only. */
    DENSE,
    /** The store's own dense + full-text fusion, given the query text as well as its embedding. */
    HYBRID
}
