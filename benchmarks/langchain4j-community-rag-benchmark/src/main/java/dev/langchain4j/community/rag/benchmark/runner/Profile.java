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
    /**
     * The {@code filtered} scenario (B13, the "before" for S9): {@code dense} unfiltered for reference, then
     * {@code dense} and {@code hybrid-asis} with {@code bucket < 1 | 10 | 50}; smoke tier (A5), embedded and remote,
     * three repetitions.
     */
    FILTERED,
    /** Standard tier (full tier added by B22), embedded and remote, all canonical scenarios. */
    CANONICAL,
    /** {@link #CANONICAL} plus index grid, hybrid variants and optional mixed workload. */
    EXTENDED
}
