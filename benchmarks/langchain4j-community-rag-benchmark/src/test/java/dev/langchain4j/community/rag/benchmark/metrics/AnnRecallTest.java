package dev.langchain4j.community.rag.benchmark.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnRecallTest {

    @Test
    void should_count_exact_neighbours_found_in_top_k() {
        assertThat(AnnRecall.recall(List.of("a", "x", "c", "b"), List.of("a", "b", "c", "d"), 3))
                .isEqualTo(2.0 / 3);
    }

    @Test
    void should_use_available_candidates_as_denominator_for_padded_truth() {
        assertThat(AnnRecall.recall(List.of("a", "x"), Arrays.asList("a", "b", null, null), 4))
                .isEqualTo(0.5);
        assertThat(AnnRecall.recall(List.of("a"), Arrays.asList(null, null), 2)).isNaN();
    }

    @Test
    void should_average_skipping_undefined_queries() {
        double mean = AnnRecall.mean(
                List.of(List.of("a"), List.of("x"), List.of("z")),
                List.of(List.of("a"), List.of("b"), Arrays.asList((String) null)),
                1);

        assertThat(mean).isEqualTo(0.5);
    }
}
