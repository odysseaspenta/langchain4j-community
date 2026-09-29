package dev.langchain4j.community.rag.benchmark.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Expected values computed with pytrec_eval 0.5 (pytrec-eval-terrier) on the same fixture: {@code ndcg_cut_10} and
 * {@code recall_10} on the full run, {@code recall_100} on the top 100, {@code recip_rank} on the top 10. q4 has no
 * results; pytrec_eval omits it, and this benchmark scores it 0.
 */
class IrMetricsTest {

    static final Map<String, Map<String, Integer>> QRELS = new LinkedHashMap<>();
    static final Map<String, List<String>> RUNS = new LinkedHashMap<>();

    static {
        QRELS.put("q1", Map.of("d1", 2, "d3", 1, "d99", 1));
        QRELS.put("q2", Map.of("d15", 1));
        QRELS.put("q3", Map.of("n1", 0, "d2", 1));
        QRELS.put("q4", Map.of("d1", 1));
        Map<String, Integer> q5 = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            q5.put("r" + i, i % 3 == 0 ? 2 : 1);
        }
        QRELS.put("q5", q5);

        List<String> q1 = new ArrayList<>(List.of("d1", "x1", "d3"));
        for (int i = 2; i < 20; i++) {
            q1.add("x" + i);
        }
        List<String> q2 = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            q2.add("y" + i);
        }
        q2.add("d15");
        for (int i = 14; i < 30; i++) {
            q2.add("y" + i);
        }
        List<String> q5run =
                new ArrayList<>(List.of("r5", "w1", "r0", "r11", "w2", "r3", "w3", "w4", "r7", "w5", "r1", "r2"));
        for (int i = 6; i < 100; i++) {
            q5run.add("w" + i);
        }
        q5run.add("r4");
        RUNS.put("q1", q1);
        RUNS.put("q2", q2);
        RUNS.put("q3", List.of("n1", "d2", "z1", "z2"));
        RUNS.put("q4", List.of());
        RUNS.put("q5", q5run);
    }

    // query -> {ndcg@10, recall@10, recall@100, mrr@10}
    static final Map<String, double[]> EXPECTED = Map.of(
            "q1", new double[] {0.7984848580994974, 0.6666666666666666, 0.6666666666666666, 1.0},
            "q2", new double[] {0.0, 0.0, 1.0, 0.0},
            "q3", new double[] {0.6309297535714575, 1.0, 1.0, 0.5},
            "q4", new double[] {0.0, 0.0, 0.0, 0.0},
            "q5", new double[] {0.484734782796056, 0.4166666666666667, 0.5833333333333334, 1.0});

    @Test
    void should_match_pytrec_eval_per_query() {
        for (String q : QRELS.keySet()) {
            double[] expected = EXPECTED.get(q);
            List<String> run = RUNS.get(q);
            Map<String, Integer> judgments = QRELS.get(q);
            assertThat(IrMetrics.ndcg(run, judgments, 10)).as(q + " ndcg@10").isCloseTo(expected[0], within(1e-12));
            assertThat(IrMetrics.recall(run, judgments, 10))
                    .as(q + " recall@10")
                    .isCloseTo(expected[1], within(1e-12));
            assertThat(IrMetrics.recall(run, judgments, 100))
                    .as(q + " recall@100")
                    .isCloseTo(expected[2], within(1e-12));
            assertThat(IrMetrics.reciprocalRank(run, judgments, 10))
                    .as(q + " mrr@10")
                    .isCloseTo(expected[3], within(1e-12));
        }
    }

    @Test
    void should_average_over_judged_queries_counting_empty_results_as_zero() {
        Map<String, List<String>> runs = new LinkedHashMap<>(RUNS);
        runs.remove("q4"); // a query missing from the run counts like an empty ranking

        IrMetrics.IrScores scores = IrMetrics.evaluate(runs, QRELS);

        assertThat(scores.queries()).isEqualTo(5);
        assertThat(scores.ndcgAt10()).isCloseTo(mean(0), within(1e-12));
        assertThat(scores.recallAt10()).isCloseTo(mean(1), within(1e-12));
        assertThat(scores.recallAt100()).isCloseTo(mean(2), within(1e-12));
        assertThat(scores.mrrAt10()).isCloseTo(mean(3), within(1e-12));
    }

    @Test
    void should_skip_queries_without_relevant_judgments() {
        IrMetrics.IrScores scores =
                IrMetrics.evaluate(Map.of("q", List.of("a")), Map.of("q", Map.of("a", 0), "p", Map.of("b", 1)));

        assertThat(scores.queries()).isEqualTo(1);
        assertThat(scores.ndcgAt10()).isZero();
    }

    @Test
    void should_count_repeated_ids_once() {
        assertThat(IrMetrics.recall(List.of("a", "a", "b"), Map.of("a", 1, "b", 1), 2))
                .isEqualTo(1.0);
    }

    static double mean(int metric) {
        return EXPECTED.values().stream().mapToDouble(v -> v[metric]).sum() / EXPECTED.size();
    }
}
