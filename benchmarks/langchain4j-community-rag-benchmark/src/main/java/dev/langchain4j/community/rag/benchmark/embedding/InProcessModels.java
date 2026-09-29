package dev.langchain4j.community.rag.benchmark.embedding;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.AbstractInProcessEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxBertBiEncoder;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.Executor;

/**
 * LangChain4j in-process ONNX models configured for the two ways the benchmark uses them.
 *
 * <p>With one caller, the stock LangChain4j model is used unchanged (ONNX Runtime spreads each call over all
 * cores), which is how a RAG application embeds a query. With several callers, each ONNX session call is limited to
 * one intra-op thread: stock sessions would each spawn a thread per core and oversubscribe the CPU (measured 14 vs
 * 23 passages/s on 7 cores). Tokenisation, pooling and normalisation are LangChain4j's own code either way, and the
 * vectors are bit-identical ({@code rag-bench embed --verify} checks this).
 */
final class InProcessModels {

    private static final String BGE_MODEL = "bge-small-en-v1.5.onnx";
    private static final String BGE_TOKENIZER = "bge-small-en-v1.5-tokenizer.json";

    private static OnnxBertBiEncoder singleThreadedBge;

    private InProcessModels() {}

    static EmbeddingModel bgeSmallEnV15(Executor executor, int callers) {
        if (callers <= 1) {
            return new BgeSmallEnV15EmbeddingModel(executor);
        }
        return new ParallelModel(executor, singleThreadedBge());
    }

    private static synchronized OnnxBertBiEncoder singleThreadedBge() {
        if (singleThreadedBge == null) {
            try (InputStream model = resource(BGE_MODEL);
                    InputStream tokenizer = resource(BGE_TOKENIZER);
                    OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(1);
                options.setInterOpNumThreads(1);
                OrtEnvironment environment = OrtEnvironment.getEnvironment();
                OrtSession session = environment.createSession(model.readAllBytes(), options);
                singleThreadedBge = new OnnxBertBiEncoder(environment, session, tokenizer, PoolingMode.CLS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (OrtException e) {
                throw new IllegalStateException("Cannot create ONNX session for " + BGE_MODEL, e);
            }
        }
        return singleThreadedBge;
    }

    private static InputStream resource(String name) throws IOException {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException(name + " not on the classpath");
        }
        return in;
    }

    private static final class ParallelModel extends AbstractInProcessEmbeddingModel {

        private final OnnxBertBiEncoder encoder;

        ParallelModel(Executor executor, OnnxBertBiEncoder encoder) {
            super(executor);
            this.encoder = encoder;
        }

        @Override
        protected OnnxBertBiEncoder model() {
            return encoder;
        }
    }
}
