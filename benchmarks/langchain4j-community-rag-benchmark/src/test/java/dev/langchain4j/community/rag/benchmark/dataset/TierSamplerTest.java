package dev.langchain4j.community.rag.benchmark.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TierSamplerTest {

    static final List<String> CORPUS =
            IntStream.range(0, 250_000).mapToObj(i -> "doc" + i).toList();
    static final Set<String> RELEVANT = new LinkedHashSet<>(List.of("doc249999", "doc7", "doc123456"));

    @Test
    void should_put_relevant_passages_first_in_corpus_order() {
        List<String> priority = TierSampler.priorityOrder(CORPUS, RELEVANT, 42);

        assertThat(priority).hasSize(CORPUS.size());
        assertThat(new HashSet<>(priority)).hasSameSizeAs(priority);
        assertThat(priority.subList(0, 3)).containsExactly("doc7", "doc123456", "doc249999");
        assertThat(priority.subList(3, 13)).isNotEqualTo(CORPUS.subList(0, 10));
    }

    @Test
    void should_nest_tiers_and_include_relevant_passages() {
        List<String> priority = TierSampler.priorityOrder(CORPUS, RELEVANT, 42);

        Set<String> smoke = new HashSet<>(TierSampler.loadOrder(priority, Tier.SMOKE, 42));
        Set<String> standard = new HashSet<>(TierSampler.loadOrder(priority, Tier.STANDARD, 42));
        List<String> full = TierSampler.loadOrder(priority, Tier.FULL, 42);

        // Plain Set operations: AssertJ's iterable assertions are quadratic on collections this size.
        assertThat(smoke.size()).isEqualTo(100_000);
        assertThat(smoke.containsAll(RELEVANT)).isTrue();
        assertThat(smoke.equals(new HashSet<>(priority.subList(0, 100_000)))).isTrue();
        assertThat(standard.size()).isEqualTo(CORPUS.size());
        assertThat(standard.containsAll(smoke)).isTrue();
        assertThat(new HashSet<>(full).equals(new HashSet<>(CORPUS))).isTrue();
    }

    @Test
    void should_shuffle_load_order_so_relevant_passages_are_not_clustered() {
        List<String> priority = TierSampler.priorityOrder(CORPUS, RELEVANT, 42);

        List<String> smoke = TierSampler.loadOrder(priority, Tier.SMOKE, 42);

        assertThat(smoke.subList(0, 3)).isNotEqualTo(priority.subList(0, 3));
    }

    @Test
    void should_be_deterministic_per_seed() {
        List<String> a = TierSampler.loadOrder(TierSampler.priorityOrder(CORPUS, RELEVANT, 42), Tier.SMOKE, 42);
        List<String> b = TierSampler.loadOrder(TierSampler.priorityOrder(CORPUS, RELEVANT, 42), Tier.SMOKE, 42);
        List<String> c = TierSampler.loadOrder(TierSampler.priorityOrder(CORPUS, RELEVANT, 43), Tier.SMOKE, 43);

        assertThat(a.equals(b)).isTrue();
        assertThat(a.equals(c)).isFalse();
    }

    @Test
    void should_reject_relevant_passage_missing_from_corpus() {
        assertThatThrownBy(() -> TierSampler.priorityOrder(CORPUS, Set.of("nope"), 42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void should_reject_duplicate_corpus_ids() {
        assertThatThrownBy(() -> TierSampler.priorityOrder(List.of("a", "a"), Set.of(), 42))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
