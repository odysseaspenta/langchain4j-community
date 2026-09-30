package dev.langchain4j.community.rag.benchmark.embedding;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The embedding server's HTTP contract ({@code gpu-embedder/server.py}) on {@link FakeEmbeddingModel} vectors.
 */
class FakeEmbeddingServer implements AutoCloseable {

    static final ObjectMapper JSON = new ObjectMapper();

    final AtomicInteger requests = new AtomicInteger();
    final AtomicInteger embedded = new AtomicInteger();
    String model = EmbeddingServer.HubModel.BGE_SMALL_EN_V15.model();
    String revision = EmbeddingServer.HubModel.BGE_SMALL_EN_V15.revision();
    /** Texts longer than this are refused with 422, like texts over 512 tokens. */
    int maxLength = Integer.MAX_VALUE;
    /** Added to the first component before renormalising: a small value mimics GPU kernels, a large one a wrong model. */
    float noise;
    /** Embedding requests after which the server answers 500. */
    int failAfterRequests = Integer.MAX_VALUE;

    private final HttpServer server;

    FakeEmbeddingServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", this::health);
        server.createContext("/v1/embeddings", this::embeddings);
        server.start();
    }

    EmbeddingServer client() {
        return new EmbeddingServer(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(30));
    }

    private void health(HttpExchange exchange) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("revision", revision);
        body.put("device", "fake");
        respond(exchange, 200, body.toString());
    }

    private void embeddings(HttpExchange exchange) throws IOException {
        if (requests.incrementAndGet() > failAfterRequests) {
            respond(exchange, 500, "{\"detail\":\"simulated failure\"}");
            return;
        }
        JsonNode input = JSON.readTree(exchange.getRequestBody()).path("input");
        List<String> texts = new ArrayList<>();
        input.forEach(text -> texts.add(text.asText()));
        List<Integer> tooLong = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).length() > maxLength) {
                tooLong.add(i);
            }
        }
        if (!tooLong.isEmpty()) {
            ObjectNode body = JSON.createObjectNode();
            ArrayNode indexes = body.putObject("detail").putArray("too_long");
            tooLong.forEach(indexes::add);
            respond(exchange, 422, body.toString());
            return;
        }
        ObjectNode body = JSON.createObjectNode();
        ArrayNode data = body.putArray("data");
        for (int i = 0; i < texts.size(); i++) {
            float[] vector = FakeEmbeddingModel.vector(texts.get(i));
            if (noise != 0) {
                vector[0] += noise;
                double norm = 0;
                for (float v : vector) {
                    norm += v * v;
                }
                for (int d = 0; d < vector.length; d++) {
                    vector[d] = (float) (vector[d] / Math.sqrt(norm));
                }
            }
            ObjectNode item = data.addObject();
            item.put("index", i);
            ArrayNode embedding = item.putArray("embedding");
            for (float v : vector) {
                embedding.add(v);
            }
        }
        embedded.addAndGet(texts.size());
        respond(exchange, 200, body.toString());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
