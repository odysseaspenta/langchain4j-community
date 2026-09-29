package dev.langchain4j.community.rag.benchmark.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BeirDatasetTest {

    @TempDir
    Path tmp;

    @Test
    void should_read_corpus_queries_and_qrels() throws IOException {
        BeirDataset dataset = new BeirDataset(TestBeir.write(tmp, 10, 2));

        List<Passage> passages = new ArrayList<>();
        dataset.forEachPassage(passages::add);
        Qrels qrels = dataset.testQrels();

        assertThat(dataset.passageIds()).hasSize(10).startsWith("doc0", "doc1");
        assertThat(passages.get(0).content()).isEqualTo("Title 0\nText of passage 0");
        assertThat(passages.get(1).content()).isEqualTo("Text of passage 1");
        assertThat(qrels.pairCount()).isEqualTo(6);
        assertThat(qrels.byQuery().get("q0")).containsEntry("doc1", 2).containsEntry("doc2", 0);
        assertThat(qrels.relevantPassageIds()).containsExactly("doc0", "doc1", "doc3", "doc4");
        assertThat(dataset.testQueries(qrels)).extracting(Query::id).containsExactly("q0", "q1");
    }
}
