package dev.langchain4j.community.rag.benchmark.runner;

import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import java.util.List;

/**
 * What a profile runs: for each (tier, mode) one load, then every scenario (PRD F14).
 *
 * @param k           results requested per query (nDCG@10 / MRR@10 use the first 10)
 * @param repetitions timed passes per scenario after the cold pass
 */
public record ProfilePlan(
        Profile profile, List<Tier> tiers, List<TargetMode> modes, List<Scenario> scenarios, int k, int repetitions) {

    public static ProfilePlan of(Profile profile) {
        return switch (profile) {
            case SMOKE ->
                new ProfilePlan(
                        profile,
                        List.of(Tier.SMOKE),
                        List.of(TargetMode.EMBEDDED),
                        List.of(Scenario.DENSE, Scenario.HYBRID_AS_IS),
                        100,
                        1);
            case BASELINE ->
                new ProfilePlan(
                        profile,
                        List.of(Tier.SMOKE, Tier.STANDARD),
                        List.of(TargetMode.EMBEDDED, TargetMode.REMOTE),
                        List.of(Scenario.DENSE, Scenario.HYBRID_AS_IS),
                        100,
                        3);
            case CANONICAL ->
                throw new UnsupportedOperationException(
                        "The canonical profile is not implemented yet; see plans/issues/B16-canonical-profile.md");
            case EXTENDED ->
                throw new UnsupportedOperationException(
                        "The extended profile is not implemented yet; see plans/issues/B20-extended-profile.md");
        };
    }

    /** The same plan with other deployment modes, e.g. {@code smoke} with remote added (B08). */
    public ProfilePlan withModes(List<TargetMode> modes) {
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("At least one mode is required");
        }
        return new ProfilePlan(profile, tiers, List.copyOf(modes), scenarios, k, repetitions);
    }
}
