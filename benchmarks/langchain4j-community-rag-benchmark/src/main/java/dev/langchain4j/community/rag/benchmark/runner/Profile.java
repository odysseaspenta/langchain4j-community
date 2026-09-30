package dev.langchain4j.community.rag.benchmark.runner;

/**
 * Named run profiles (PRD F14).
 */
public enum Profile {
    /** Smoke tier, embedded, {@code dense} + {@code hybrid-asis}, one repetition. */
    SMOKE,
    /**
     * The "before" for every store fix (B10, PRD R1): smoke and standard tiers, embedded and remote, {@code dense}
     * (store default efSearch) + {@code hybrid-asis}, three repetitions for noise bands. Run with a load time cap.
     */
    BASELINE,
    /** Standard tier (full tier added by B22), embedded and remote, all canonical scenarios. */
    CANONICAL,
    /** {@link #CANONICAL} plus index grid, hybrid variants and optional mixed workload. */
    EXTENDED
}
