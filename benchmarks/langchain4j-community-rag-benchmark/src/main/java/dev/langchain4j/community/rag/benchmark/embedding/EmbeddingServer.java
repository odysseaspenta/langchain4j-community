package dev.langchain4j.community.rag.benchmark.embedding;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client of an external embedding server with an OpenAI-compatible {@code POST /v1/embeddings} and a
 * {@code GET /health} that reports the model and its Hub revision (PRD A4; the server is in {@code gpu-embedder/}).
 *
 * <p>The server refuses texts longer than the model's 512 tokens (HTTP 422 with their indexes) instead of truncating
 * them, because LangChain4j's in-process model splits and averages such texts; the caller embeds those in-process.
 * Requests are sent one at a time: concurrent fp32 batches can run the GPU out of memory.
 */
public final class EmbeddingServer {

    /**
     * A model on the Hugging Face Hub at a pinned revision.
     */
    public record HubModel(String model, String revision) {

        /**
         * The revision whose {@code onnx/model.onnx} is byte-identical to LangChain4j's in-process
         * {@code bge-small-en-v1.5.onnx} and whose {@code tokenizer.json} is the same file (B09a).
         */
        public static final HubModel BGE_SMALL_EN_V15 =
                new HubModel("BAAI/bge-small-en-v1.5", "5c38ec7c405ec4b44b94cc5a9bb96e735b38267a");
    }

    /**
     * @param vectors one per input text, {@code null} where the server refused the text
     * @param refused indexes of refused texts
     */
    public record Result(float[][] vectors, List<Integer> refused) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI endpoint;
    private final HttpClient client;
    private final Duration timeout;

    public EmbeddingServer(URI endpoint, Duration timeout) {
        this.endpoint = endpoint;
        this.timeout = timeout;
        // HTTP/1.1: long requests are not cut off, and there is nothing to gain from multiplexing.
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public URI endpoint() {
        return endpoint;
    }

    /** The server's {@code /health} report. */
    public Map<String, Object> health() throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint.resolve("/health"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build());
        if (response.statusCode() != 200) {
            throw new IOException("GET /health returned " + response.statusCode() + ": " + response.body());
        }
        Map<String, Object> health = new LinkedHashMap<>();
        JSON.readTree(response.body()).fields().forEachRemaining(e -> health.put(e.getKey(), e.getValue().asText()));
        return health;
    }

    /**
     * Returns {@link #health()} after checking that the server runs {@code expected}.
     */
    public Map<String, Object> requireModel(HubModel expected) throws IOException, InterruptedException {
        Map<String, Object> health = health();
        if (!expected.model().equals(health.get("model")) || !expected.revision().equals(health.get("revision"))) {
            throw new IOException("Embedding server at " + endpoint + " runs " + health.get("model") + " at revision "
                    + health.get("revision") + ", expected " + expected.model() + " at " + expected.revision());
        }
        return health;
    }

    /**
     * Embeds {@code texts}; refused texts come back as {@code null} vectors.
     */
    public Result embed(List<String> texts) throws IOException, InterruptedException {
        float[][] vectors = new float[texts.size()][];
        List<Integer> refused = post(texts, vectors, allIndexes(texts.size()));
        if (!refused.isEmpty()) {
            // Resend without the refused texts.
            List<Integer> rest = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                if (!refused.contains(i)) {
                    rest.add(i);
                }
            }
            if (!rest.isEmpty()) {
                List<String> restTexts = rest.stream().map(texts::get).toList();
                List<Integer> again = post(restTexts, vectors, rest);
                if (!again.isEmpty()) {
                    throw new IOException("Server refused texts it accepted before: " + again);
                }
            }
        }
        return new Result(vectors, List.copyOf(refused));
    }

    /**
     * Posts {@code texts} and stores vector {@code i} at {@code vectors[positions.get(i)]}. Returns the indexes (into
     * the original {@code vectors}) the server refused, in which case nothing is stored.
     */
    private List<Integer> post(List<String> texts, float[][] vectors, List<Integer> positions)
            throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint.resolve("/v1/embeddings"))
                .timeout(timeout)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), UTF_8))
                .build());
        if (response.statusCode() == 422) {
            List<Integer> refused = new ArrayList<>();
            for (JsonNode index : JSON.readTree(response.body()).path("detail").path("too_long")) {
                refused.add(positions.get(index.asInt()));
            }
            if (refused.isEmpty()) {
                throw new IOException("POST /v1/embeddings returned 422 without too_long: " + response.body());
            }
            return refused;
        }
        if (response.statusCode() != 200) {
            throw new IOException("POST /v1/embeddings returned " + response.statusCode() + ": "
                    + abbreviate(response.body()));
        }
        JsonNode data = JSON.readTree(response.body()).path("data");
        if (data.size() != texts.size()) {
            throw new IOException("Server returned " + data.size() + " embeddings for " + texts.size() + " texts");
        }
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int d = 0; d < vector.length; d++) {
                vector[d] = (float) embedding.get(d).asDouble();
            }
            vectors[positions.get(item.path("index").asInt())] = vector;
        }
        return List.of();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
    }

    private static List<Integer> allIndexes(int n) {
        List<Integer> indexes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            indexes.add(i);
        }
        return indexes;
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}
