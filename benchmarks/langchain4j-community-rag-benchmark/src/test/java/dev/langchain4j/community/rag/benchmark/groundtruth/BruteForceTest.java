package dev.langchain4j.community.rag.benchmark.groundtruth;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BruteForceTest {

    static final int ROWS = 10_000;
    static final int QUERIES = 25;
    static final int DIMENSION = 32;
    static final int K = 100;

    @TempDir
    Path tmp;

    float[][] passageVectors;
    float[][] queryVectors;
    int[] buckets;
    VectorMatrix passages;
    VectorMatrix queries;

    @BeforeEach
    void setUp() throws IOException {
        passageVectors = Matrices.randomUnitVectors(ROWS, DIMENSION, 1);
        queryVectors = Matrices.randomUnitVectors(QUERIES, DIMENSION, 2);
        buckets = new int[ROWS];
        for (int i = 0; i < ROWS; i++) {
            buckets[i] = (i * 37) % 100;
        }
        passages = Matrices.write(tmp.resolve("p.f32"), passageVectors);
        queries = Matrices.write(tmp.resolve("q.f32"), queryVectors);
    }

    @Test
    void should_match_in_memory_embedding_store() throws Exception {
        // Acceptance 4: independent exact computation with LangChain4j's InMemoryEmbeddingStore.
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        for (int i = 0; i < ROWS; i++) {
            store.add(
                    String.valueOf(i),
                    Embedding.from(passageVectors[i]),
                    TextSegment.from("p" + i, new Metadata().put("bucket", buckets[i])));
        }

        Neighbours[][] result =
                BruteForce.search(passages, queries, new int[] {ROWS}, buckets, new int[] {10}, K, 4, null);

        for (int q = 0; q < QUERIES; q++) {
            assertSameAsStore(store, q, null, result[0][0]);
            assertSameAsStore(store, q, metadataKey("bucket").isLessThan(10), result[0][1]);
        }
    }

    private void assertSameAsStore(InMemoryEmbeddingStore<TextSegment> store, int q, Filter filter, Neighbours n) {
        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(queryVectors[q]))
                        .maxResults(K)
                        .filter(filter)
                        .build())
                .matches();
        List<Integer> expected = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            expected.add(Integer.parseInt(match.embeddingId()));
        }
        List<Integer> actual = new ArrayList<>();
        for (int rank = 0; rank < K; rank++) {
            actual.add(n.row(q, rank));
        }
        assertThat(actual).isEqualTo(expected);
        // The store reports (cos + 1) / 2.
        assertThat((n.score(q, 0) + 1) / 2.0).isCloseTo(matches.get(0).score(), within(1e-5));
    }

    @Test
    void should_give_prefix_results_equal_to_separate_searches() throws Exception {
        Neighbours[][] combined = BruteForce.search(
                passages, queries, new int[] {1_000, 4_000, ROWS}, buckets, new int[] {1, 50}, K, 3, null);

        for (int p = 0; p < 3; p++) {
            int prefix = new int[] {1_000, 4_000, ROWS}[p];
            Neighbours[][] alone =
                    BruteForce.search(passages, queries, new int[] {prefix}, buckets, new int[] {1, 50}, K, 2, null);
            for (int f = 0; f < 3; f++) {
                assertThat(combined[p][f].rows()).isEqualTo(alone[0][f].rows());
                assertThat(combined[p][f].scores()).isEqualTo(alone[0][f].scores());
            }
        }
    }

    @Test
    void should_be_independent_of_thread_count() throws Exception {
        Neighbours[][] one =
                BruteForce.search(passages, queries, new int[] {ROWS}, buckets, new int[] {10}, K, 1, null);
        Neighbours[][] many =
                BruteForce.search(passages, queries, new int[] {ROWS}, buckets, new int[] {10}, K, 7, null);

        assertThat(many[0][0].rows()).isEqualTo(one[0][0].rows());
        assertThat(many[0][1].scores()).isEqualTo(one[0][1].scores());
    }

    @Test
    void should_respect_filters_and_pad_small_candidate_sets() throws Exception {
        Neighbours[][] result =
                BruteForce.search(passages, queries, new int[] {150}, buckets, new int[] {1}, K, 2, null);

        Neighbours filtered = result[0][1];
        int candidates = 0;
        for (int i = 0; i < 150; i++) {
            candidates += buckets[i] < 1 ? 1 : 0;
        }
        for (int rank = 0; rank < K; rank++) {
            int row = filtered.row(0, rank);
            if (rank < candidates) {
                assertThat(buckets[row]).isZero();
            } else {
                assertThat(row).isEqualTo(-1);
            }
        }
    }

    @Test
    void should_snapshot_equal_prefixes() throws Exception {
        Neighbours[][] result =
                BruteForce.search(passages, queries, new int[] {ROWS, ROWS}, buckets, new int[0], K, 2, null);

        assertThat(result[1][0].rows()).isEqualTo(result[0][0].rows());
    }
}
