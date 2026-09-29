package dev.langchain4j.community.rag.benchmark.metrics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieval quality against relevance judgments, with trec_eval / BEIR definitions (PRD M2):
 * <ul>
 *   <li>nDCG@k — {@code ndcg_cut}: gain = judged grade (linear), discount {@code log2(rank + 1)}, ideal DCG from
 *       all judged-relevant passages;</li>
 *   <li>Recall@k — relevant passages in the top k / all relevant passages;</li>
 *   <li>MRR@k — {@code 1 / rank} of the first relevant passage in the top k, else 0.</li>
 * </ul>
 * A passage is relevant when its grade is {@code > 0}. Rankings are taken in the given order (the store's order);
 * repeated ids count once, at their first position.
 *
 * <p>Averaging differs from BEIR in one deliberate way: a query with no results scores 0 instead of being left out
 * (BEIR skips it, which would reward a store for failing). Queries without any relevant judgment are skipped, as in
 * trec_eval.
 */
public final class IrMetrics {

    private IrMetrics() {}

    public static double ndcg(List<String> ranking, Map<String, Integer> judgments, int k) {
        List<String> top = topDistinct(ranking, k);
        double dcg = 0;
        for (int i = 0; i < top.size(); i++) {
            int grade = judgments.getOrDefault(top.get(i), 0);
            if (grade > 0) {
                dcg += grade / log2(i + 2);
            }
        }
        List<Integer> ideal = judgments.values().stream()
                .filter(grade -> grade > 0)
                .sorted((a, b) -> Integer.compare(b, a))
                .limit(k)
                .toList();
        double idcg = 0;
        for (int i = 0; i < ideal.size(); i++) {
            idcg += ideal.get(i) / log2(i + 2);
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    public static double recall(List<String> ranking, Map<String, Integer> judgments, int k) {
        long relevant = judgments.values().stream().filter(grade -> grade > 0).count();
        if (relevant == 0) {
            return 0;
        }
        long found = topDistinct(ranking, k).stream()
                .filter(id -> judgments.getOrDefault(id, 0) > 0)
                .count();
        return (double) found / relevant;
    }

    public static double reciprocalRank(List<String> ranking, Map<String, Integer> judgments, int k) {
        List<String> top = topDistinct(ranking, k);
        for (int i = 0; i < top.size(); i++) {
            if (judgments.getOrDefault(top.get(i), 0) > 0) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    /**
     * Mean nDCG@10, Recall@10, Recall@100 and MRR@10 over every judged query; a query missing from
     * {@code rankings} counts as an empty ranking.
     */
    public static IrScores evaluate(Map<String, List<String>> rankings, Map<String, Map<String, Integer>> qrels) {
        double ndcg10 = 0;
        double recall10 = 0;
        double recall100 = 0;
        double mrr10 = 0;
        int queries = 0;
        for (Map.Entry<String, Map<String, Integer>> entry : qrels.entrySet()) {
            Map<String, Integer> judgments = entry.getValue();
            if (judgments.values().stream().noneMatch(grade -> grade > 0)) {
                continue;
            }
            List<String> ranking = rankings.getOrDefault(entry.getKey(), List.of());
            ndcg10 += ndcg(ranking, judgments, 10);
            recall10 += recall(ranking, judgments, 10);
            recall100 += recall(ranking, judgments, 100);
            mrr10 += reciprocalRank(ranking, judgments, 10);
            queries++;
        }
        return queries == 0
                ? new IrScores(0, 0, 0, 0, 0)
                : new IrScores(ndcg10 / queries, recall10 / queries, recall100 / queries, mrr10 / queries, queries);
    }

    public record IrScores(double ndcgAt10, double recallAt10, double recallAt100, double mrrAt10, int queries) {}

    static List<String> topDistinct(List<String> ranking, int k) {
        List<String> top = new ArrayList<>(Math.min(k, ranking.size()));
        Set<String> seen = new HashSet<>();
        for (String id : ranking) {
            if (top.size() == k) {
                break;
            }
            if (seen.add(id)) {
                top.add(id);
            }
        }
        return top;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
