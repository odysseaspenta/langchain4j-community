package dev.langchain4j.community.rag.benchmark.runner;

import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import java.util.List;

/**
 * One way of querying a loaded index (PRD §7.4).
 *
 * @param bucketsBelow metadata filter {@code bucket < bucketsBelow}, or {@code null}
 * @param efSearch     requested search depth, or {@code null} for the store default
 */
public record Scenario(String name, SearchMode mode, Integer bucketsBelow, Integer efSearch) {

    public static final Scenario DENSE = new Scenario("dense", SearchMode.DENSE, null, null);
    public static final Scenario HYBRID_AS_IS = new Scenario("hybrid-asis", SearchMode.HYBRID, null, null);

    /** Bucket filters of the {@code filtered} scenario (PRD §7.4): about 1 %, 10 % and 50 % of the passages. */
    public static final List<Integer> FILTER_BUCKETS = List.of(1, 10, 50);

    /** {@code dense} with the metadata filter {@code bucket < bucketsBelow}. */
    public static Scenario denseFiltered(int bucketsBelow) {
        return new Scenario("dense-filtered-" + bucketsBelow, SearchMode.DENSE, bucketsBelow, null);
    }

    /** {@code hybrid-asis} (store defaults) with the metadata filter {@code bucket < bucketsBelow}. */
    public static Scenario hybridFiltered(int bucketsBelow) {
        return new Scenario("hybrid-filtered-" + bucketsBelow, SearchMode.HYBRID, bucketsBelow, null);
    }
}
