package dev.langchain4j.community.rag.benchmark.targets;

import java.time.Duration;

/**
 * @param batchSize documents per {@code addAll} call
 * @param timeCap   stop loading after this long (PRD R1); {@code null} for no cap
 */
public record LoadOptions(int batchSize, Duration timeCap) {}
