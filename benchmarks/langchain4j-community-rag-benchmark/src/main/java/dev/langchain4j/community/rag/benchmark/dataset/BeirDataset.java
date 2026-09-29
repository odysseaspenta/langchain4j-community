package dev.langchain4j.community.rag.benchmark.dataset;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.community.rag.benchmark.util.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A dataset in the BEIR layout: {@code corpus.jsonl} ({@code _id}, {@code title}, {@code text}),
 * {@code queries.jsonl} ({@code _id}, {@code text}) and {@code qrels/test.tsv}
 * ({@code query-id \t corpus-id \t score}, optional header). Nothing here is specific to NQ (PRD F5).
 *
 * <p>The corpus is streamed, never held in memory.
 */
public class BeirDataset {

    public static final String CORPUS = "corpus.jsonl";
    public static final String QUERIES = "queries.jsonl";
    public static final String TEST_QRELS = "qrels/test.tsv";

    private final Path dir;

    public BeirDataset(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    /**
     * Streams every corpus passage in file order.
     */
    public void forEachPassage(Consumer<Passage> consumer) throws IOException {
        forEachJsonLine(
                dir.resolve(CORPUS),
                node -> consumer.accept(new Passage(
                        requiredText(node, "_id"),
                        node.path("title").asText(""),
                        node.path("text").asText(""))));
    }

    /**
     * Returns every corpus passage id in file order.
     */
    public List<String> passageIds() throws IOException {
        List<String> ids = new ArrayList<>();
        forEachJsonLine(dir.resolve(CORPUS), node -> ids.add(requiredText(node, "_id")));
        return ids;
    }

    /**
     * Returns the test relevance judgments.
     */
    public Qrels testQrels() throws IOException {
        Map<String, Map<String, Integer>> byQuery = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(dir.resolve(TEST_QRELS), UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || (lineNumber == 1 && line.startsWith("query-id"))) {
                    continue;
                }
                String[] fields = line.split("\t");
                if (fields.length != 3) {
                    throw new IOException(TEST_QRELS + ":" + lineNumber + ": expected 3 tab-separated fields");
                }
                byQuery.computeIfAbsent(fields[0], k -> new LinkedHashMap<>())
                        .put(fields[1], Integer.parseInt(fields[2].trim()));
            }
        }
        return new Qrels(byQuery);
    }

    /**
     * Returns the queries that have test judgments, in query-file order (PRD F2).
     */
    public List<Query> testQueries(Qrels qrels) throws IOException {
        List<Query> queries = new ArrayList<>();
        forEachJsonLine(dir.resolve(QUERIES), node -> {
            String id = requiredText(node, "_id");
            if (qrels.byQuery().containsKey(id)) {
                queries.add(new Query(id, node.path("text").asText("")));
            }
        });
        return queries;
    }

    private static void forEachJsonLine(Path file, Consumer<JsonNode> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = Json.MAPPER.readTree(line);
                } catch (IOException e) {
                    throw new IOException(file.getFileName() + ":" + lineNumber + ": invalid JSON", e);
                }
                consumer.accept(node);
            }
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing '" + field + "' in " + node);
        }
        return value.asText();
    }
}
