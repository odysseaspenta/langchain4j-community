package dev.langchain4j.community.rag.benchmark.dataset;

import dev.langchain4j.community.rag.benchmark.util.Seeds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic tier sampling (PRD F3).
 *
 * <p>Algorithm version {@value #ALGORITHM_VERSION}:
 * <ol>
 *   <li><b>Priority order</b>: every passage judged relevant by the test qrels, in corpus order, followed by all
 *       other passages shuffled with the seed. A tier's members are the first {@code size} entries, so every tier
 *       contains all relevant passages and {@code smoke ⊂ standard ⊂ full}. The embedding cache is filled in
 *       this order, so growing to a larger tier only appends.</li>
 *   <li><b>Load order</b>: a tier's members shuffled again with a per-tier seed. This is the order passages are
 *       inserted into a store, so relevant passages are not clustered at the start of the index.</li>
 * </ol>
 * Shuffles are Fisher–Yates driven by {@link Random}, whose algorithm the Java SE specification fixes, so the
 * output is identical on every JDK.
 */
public final class TierSampler {

    public static final int ALGORITHM_VERSION = 1;

    private TierSampler() {}

    public static List<String> priorityOrder(List<String> corpusIds, Set<String> relevantIds, long seed) {
        Set<String> corpus = new HashSet<>(corpusIds);
        if (corpus.size() != corpusIds.size()) {
            throw new IllegalArgumentException("Corpus contains duplicate passage ids");
        }
        List<String> missing =
                relevantIds.stream().filter(id -> !corpus.contains(id)).limit(5).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Relevant passages missing from the corpus, e.g. " + missing);
        }

        List<String> relevant = new ArrayList<>(relevantIds.size());
        List<String> others = new ArrayList<>(corpusIds.size() - relevantIds.size());
        for (String id : corpusIds) {
            (relevantIds.contains(id) ? relevant : others).add(id);
        }
        shuffle(others, Seeds.derive(seed, "tier-priority"));

        List<String> priority = new ArrayList<>(corpusIds.size());
        priority.addAll(relevant);
        priority.addAll(others);
        return priority;
    }

    public static List<String> loadOrder(List<String> priorityOrder, Tier tier, long seed) {
        List<String> members = new ArrayList<>(priorityOrder.subList(0, tier.size(priorityOrder.size())));
        shuffle(members, Seeds.derive(seed, "tier-load-order:" + tier.id()));
        return members;
    }

    static void shuffle(List<String> list, long seed) {
        Random random = new Random(seed);
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            list.set(i, list.set(j, list.get(i)));
        }
    }
}
