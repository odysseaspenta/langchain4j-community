package dev.langchain4j.community.rag.benchmark.embedding;

import dev.langchain4j.community.rag.benchmark.util.Seeds;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic, normalised pseudo-embeddings derived from the text; counts calls and can fail after a budget.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    static final int DIMENSION = 8;

    public final AtomicInteger embedded = new AtomicInteger();
    final int failAfter;

    public FakeEmbeddingModel(int failAfter) {
        this.failAfter = failAfter;
    }

    public static EmbeddingModelSpec spec(String queryPrefix, FakeEmbeddingModel model) {
        return new EmbeddingModelSpec(
                "fake", DIMENSION, "fake-model.bin", queryPrefix, "", (executor, callers) -> model);
    }

    static float[] vector(String text) {
        float[] v = new float[DIMENSION];
        double norm = 0;
        for (int d = 0; d < DIMENSION; d++) {
            v[d] = (Seeds.hash(d, text) % 1000) / 1000f;
            norm += v[d] * v[d];
        }
        for (int d = 0; d < DIMENSION; d++) {
            v[d] = (float) (v[d] / Math.sqrt(norm));
        }
        return v;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        if (embedded.addAndGet(segments.size()) > failAfter) {
            throw new IllegalStateException("simulated crash");
        }
        return Response.from(
                segments.stream().map(s -> Embedding.from(vector(s.text()))).toList());
    }
}
