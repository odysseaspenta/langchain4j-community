package dev.langchain4j.community.rag.benchmark.targets;

import java.util.Map;

/**
 * One passage to load: its id, stored text, embedding and metadata.
 */
public record Document(String id, String text, float[] vector, Map<String, Object> metadata) {}
