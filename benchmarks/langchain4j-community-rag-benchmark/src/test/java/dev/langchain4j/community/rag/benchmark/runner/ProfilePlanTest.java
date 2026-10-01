package dev.langchain4j.community.rag.benchmark.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfilePlanTest {

    @Test
    void should_plan_the_baseline_over_both_tiers_and_modes() {
        ProfilePlan plan = ProfilePlan.of(Profile.BASELINE);

        assertThat(plan.tiers()).containsExactly(Tier.SMOKE, Tier.STANDARD);
        assertThat(plan.modes()).containsExactly(TargetMode.EMBEDDED, TargetMode.REMOTE);
        assertThat(plan.scenarios()).containsExactly(Scenario.DENSE, Scenario.HYBRID_AS_IS);
        assertThat(plan.repetitions()).isEqualTo(3);
        assertThat(plan.k()).isEqualTo(100);
    }

    @Test
    void should_override_tiers_to_rerun_one_tier() {
        ProfilePlan plan = ProfilePlan.of(Profile.BASELINE).withTiers(List.of(Tier.STANDARD));

        assertThat(plan.tiers()).containsExactly(Tier.STANDARD);
        assertThat(plan.modes()).containsExactly(TargetMode.EMBEDDED, TargetMode.REMOTE);
        assertThatThrownBy(() -> plan.withTiers(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_keep_smoke_embedded_only_unless_modes_are_overridden() {
        assertThat(ProfilePlan.of(Profile.SMOKE).modes()).containsExactly(TargetMode.EMBEDDED);
        assertThat(ProfilePlan.of(Profile.SMOKE).withModes(List.of(TargetMode.REMOTE)).modes())
                .containsExactly(TargetMode.REMOTE);
        assertThatThrownBy(() -> ProfilePlan.of(Profile.SMOKE).withModes(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
