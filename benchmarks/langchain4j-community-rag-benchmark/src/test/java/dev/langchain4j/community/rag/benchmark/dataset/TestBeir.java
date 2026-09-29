package dev.langchain4j.community.rag.benchmark.dataset;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a tiny BEIR-format dataset: passages doc0..doc{n-1}, queries q0..q{queries-1}, each query judging
 * doc{3i} and doc{3i+1} relevant and doc{3i+2} non-relevant (score 0).
 */
final class TestBeir {

    private TestBeir() {}

    static Path write(Path dir, int passages, int queries) throws IOException {
        Files.createDirectories(dir.resolve("qrels"));
        List<String> corpus = new ArrayList<>();
        for (int i = 0; i < passages; i++) {
            String title = i % 2 == 0 ? "Title " + i : "";
            corpus.add("{\"_id\": \"doc" + i + "\", \"title\": \"" + title + "\", \"text\": \"Text of passage " + i
                    + "\"}");
        }
        Files.write(dir.resolve(BeirDataset.CORPUS), corpus, UTF_8);

        List<String> queryLines = new ArrayList<>();
        List<String> qrels = new ArrayList<>(List.of("query-id\tcorpus-id\tscore"));
        for (int i = 0; i < queries; i++) {
            queryLines.add("{\"_id\": \"q" + i + "\", \"text\": \"question " + i + "\"}");
            qrels.add("q" + i + "\tdoc" + (3 * i) + "\t1");
            qrels.add("q" + i + "\tdoc" + (3 * i + 1) + "\t2");
            qrels.add("q" + i + "\tdoc" + (3 * i + 2) + "\t0");
        }
        queryLines.add("{\"_id\": \"unjudged\", \"text\": \"not in qrels\"}");
        Files.write(dir.resolve(BeirDataset.QUERIES), queryLines, UTF_8);
        Files.write(dir.resolve(BeirDataset.TEST_QRELS), qrels, UTF_8);
        return dir;
    }
}
