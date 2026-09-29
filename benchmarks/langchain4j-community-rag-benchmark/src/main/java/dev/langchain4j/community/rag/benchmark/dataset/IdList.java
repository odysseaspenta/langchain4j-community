package dev.langchain4j.community.rag.benchmark.dataset;

/**
 * An id file (one UTF-8 id per line) described in a manifest.
 */
public record IdList(String file, int size, String sha256) {}
