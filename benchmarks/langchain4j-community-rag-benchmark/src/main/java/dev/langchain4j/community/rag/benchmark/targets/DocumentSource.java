package dev.langchain4j.community.rag.benchmark.targets;

import java.io.IOException;
import java.util.List;

/**
 * Documents in load order, handed out in batches.
 */
public interface DocumentSource extends AutoCloseable {

    int size();

    /**
     * Returns up to {@code max} further documents; an empty list once every document was returned.
     */
    List<Document> next(int max) throws IOException;

    @Override
    void close() throws IOException;
}
