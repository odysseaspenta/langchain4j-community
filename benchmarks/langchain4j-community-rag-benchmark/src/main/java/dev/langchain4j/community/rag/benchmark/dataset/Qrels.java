package dev.langchain4j.community.rag.benchmark.dataset;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Relevance judgments: query id → (passage id → graded relevance). Scores above zero mean relevant.
 */
public record Qrels(Map<String, Map<String, Integer>> byQuery) {

    public Qrels {
        byQuery = Collections.unmodifiableMap(byQuery);
    }

    public int pairCount() {
        return byQuery.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Passage ids judged relevant (score &gt; 0) for at least one query, in first-seen order.
     */
    public Set<String> relevantPassageIds() {
        Set<String> ids = new LinkedHashSet<>();
        byQuery.values()
                .forEach(judgments -> judgments.forEach((id, score) -> {
                    if (score > 0) {
                        ids.add(id);
                    }
                }));
        return ids;
    }
}
