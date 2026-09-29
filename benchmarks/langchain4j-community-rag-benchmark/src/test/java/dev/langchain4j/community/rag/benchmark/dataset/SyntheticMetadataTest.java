package dev.langchain4j.community.rag.benchmark.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SyntheticMetadataTest {

    static final int N = 100_000;

    @Test
    void should_be_deterministic_and_in_range() {
        for (int i = 0; i < 1_000; i++) {
            int bucket = SyntheticMetadata.bucket("doc" + i, 42);
            assertThat(bucket).isBetween(0, 99).isEqualTo(SyntheticMetadata.bucket("doc" + i, 42));
        }
    }

    @Test
    void should_select_expected_fractions() {
        int[] below = new int[3];
        int[] thresholds = {1, 10, 50};
        for (int i = 0; i < N; i++) {
            int bucket = SyntheticMetadata.bucket("doc" + i, 42);
            for (int t = 0; t < thresholds.length; t++) {
                if (bucket < thresholds[t]) {
                    below[t]++;
                }
            }
        }
        assertThat(below[0] / (double) N).isCloseTo(0.01, within(0.002));
        assertThat(below[1] / (double) N).isCloseTo(0.10, within(0.005));
        assertThat(below[2] / (double) N).isCloseTo(0.50, within(0.01));
    }

    @Test
    void should_depend_on_seed() {
        int differing = 0;
        for (int i = 0; i < 1_000; i++) {
            if (SyntheticMetadata.bucket("doc" + i, 42) != SyntheticMetadata.bucket("doc" + i, 43)) {
                differing++;
            }
        }
        assertThat(differing).isGreaterThan(900);
    }

    @Test
    void should_match_documented_algorithm() {
        // Values cross-checked with an independent implementation (FNV-1a 64 + SplitMix64, floorMod 100).
        // Changing them silently changes every filtered scenario, so this must only change deliberately.
        assertThat(new int[] {
                    SyntheticMetadata.bucket("doc0", 42),
                    SyntheticMetadata.bucket("doc1", 42),
                    SyntheticMetadata.bucket("doc2", 42)
                })
                .containsExactly(48, 56, 33);
    }
}
