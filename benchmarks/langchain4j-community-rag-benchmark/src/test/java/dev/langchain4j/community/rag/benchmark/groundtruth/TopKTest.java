package dev.langchain4j.community.rag.benchmark.groundtruth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopKTest {

    @Test
    void should_keep_best_k_sorted_with_row_tie_break() {
        TopK top = new TopK(3);
        top.offer(0.5f, 7);
        top.offer(0.9f, 3);
        top.offer(0.5f, 2);
        top.offer(0.1f, 1);
        top.offer(0.9f, 9);

        int[] rows = new int[3];
        float[] scores = new float[3];
        top.writeSorted(rows, scores, 0);

        assertThat(rows).containsExactly(3, 9, 2);
        assertThat(scores).containsExactly(0.9f, 0.9f, 0.5f);
    }

    @Test
    void should_pad_when_fewer_than_k() {
        TopK top = new TopK(3);
        top.offer(0.2f, 4);

        int[] rows = new int[5];
        float[] scores = new float[5];
        top.writeSorted(rows, scores, 2);

        assertThat(rows).containsExactly(0, 0, 4, -1, -1);
        assertThat(scores[3]).isNaN();
    }
}
