package dev.langchain4j.community.rag.benchmark.runner;

/**
 * Named run profiles (PRD F14).
 */
public enum Profile {
    /** Smoke tier, embedded, {@code dense} + {@code hybrid-asis}, one repetition. */
    SMOKE,
    /** Standard tier (full tier added by B22), embedded and remote, all canonical scenarios. */
    CANONICAL,
    /** {@link #CANONICAL} plus index grid, hybrid variants and optional mixed workload. */
    EXTENDED
}
