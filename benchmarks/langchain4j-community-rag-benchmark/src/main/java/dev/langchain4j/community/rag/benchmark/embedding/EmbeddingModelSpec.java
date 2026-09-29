package dev.langchain4j.community.rag.benchmark.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.concurrent.Executor;

/**
 * An embedding model and how the benchmark uses it.
 *
 * @param id            short id used in cache paths and results
 * @param dimension     vector dimension
 * @param resource      classpath resource holding the model weights; its SHA-256 identifies the model revision
 * @param queryPrefix   prepended to queries (not passages) before embedding
 * @param passagePrefix prepended to passages before embedding
 * @param factory       creates the model for a number of parallel callers (see {@link Factory})
 */
public record EmbeddingModelSpec(
        String id, int dimension, String resource, String queryPrefix, String passagePrefix, Factory factory) {

    /**
     * Creates the model. {@code callers} is the size of {@code executor}, which parallelises {@code embedAll};
     * {@code 1} means the model is used the way a RAG application embeds a single query.
     */
    @FunctionalInterface
    public interface Factory {
        EmbeddingModel create(Executor executor, int callers);
    }

    /**
     * {@code bge-small-en-v1.5} in LangChain4j's in-process ONNX runtime, full precision, CLS pooling,
     * L2-normalised (PRD D6, F6). The query instruction is the one BGE recommends for retrieval.
     */
    public static final EmbeddingModelSpec BGE_SMALL_EN_V15 = new EmbeddingModelSpec(
            "bge-small-en-v1.5",
            384,
            "bge-small-en-v1.5.onnx",
            "Represent this sentence for searching relevant passages: ",
            "",
            InProcessModels::bgeSmallEnV15);
}
