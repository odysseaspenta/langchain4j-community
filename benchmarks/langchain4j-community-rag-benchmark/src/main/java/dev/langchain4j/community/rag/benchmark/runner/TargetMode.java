package dev.langchain4j.community.rag.benchmark.runner;

import java.util.Locale;

/**
 * Deployment mode of the store under test (PRD D7).
 */
public enum TargetMode {
    EMBEDDED,
    REMOTE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
