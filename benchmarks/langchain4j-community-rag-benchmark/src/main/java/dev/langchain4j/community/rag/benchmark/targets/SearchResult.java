package dev.langchain4j.community.rag.benchmark.targets;

import java.util.List;

/**
 * Ids best first, with the store's scores; or the failure that prevented an answer.
 */
public record SearchResult(List<String> ids, List<Double> scores, String failure) {

    public static SearchResult of(List<String> ids, List<Double> scores) {
        return new SearchResult(ids, scores, null);
    }

    public static SearchResult failed(Throwable error) {
        return new SearchResult(List.of(), List.of(), error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    public boolean failed() {
        return failure != null;
    }
}
