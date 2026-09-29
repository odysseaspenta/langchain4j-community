package dev.langchain4j.community.rag.benchmark.targets;

import java.util.Map;

/**
 * A store under test (PRD §8.1). Implementations adapt one store; nothing outside their package knows its types.
 */
public interface BenchmarkTarget extends AutoCloseable {

    /** Short name used in results, e.g. {@code arcadedb-embedded}. */
    String name();

    /**
     * Loads every document from {@code source} into a fresh index and returns once the index is searchable.
     */
    LoadStats load(DocumentSource source, LoadOptions options) throws Exception;

    /**
     * Answers one query. Failures are returned, not thrown, so one bad query does not end a run.
     */
    SearchResult search(SearchRequest request);

    /** What the target supports, e.g. {@code efSearch → false}. */
    Map<String, Boolean> capabilities();

    /** Configuration and version details to record with results. */
    Map<String, Object> describe();

    @Override
    void close() throws Exception;
}
