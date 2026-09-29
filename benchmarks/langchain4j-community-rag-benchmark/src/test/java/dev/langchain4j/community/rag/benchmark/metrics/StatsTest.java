package dev.langchain4j.community.rag.benchmark.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class StatsTest {

    @Test
    void should_compute_nearest_rank_percentiles() {
        // 1..100 ms, shuffled order must not matter.
        long[] nanos = LongStream.rangeClosed(1, 100)
                .map(i -> ((i * 37) % 100 + 1) * 1_000_000L)
                .toArray();

        LatencyStats stats = LatencyStats.ofNanos(nanos);

        assertThat(stats.samples()).isEqualTo(100);
        assertThat(stats.mean()).isCloseTo(50.5, within(1e-9));
        assertThat(stats.p50()).isEqualTo(50.0);
        assertThat(stats.p95()).isEqualTo(95.0);
        assertThat(stats.p99()).isEqualTo(99.0);
        assertThat(stats.max()).isEqualTo(100.0);
    }

    @Test
    void should_handle_skewed_and_single_sample_distributions() {
        long[] nanos = new long[1_000];
        for (int i = 0; i < nanos.length; i++) {
            nanos[i] = i < 990 ? 2_000_000L : 500_000_000L; // 1% outliers
        }
        LatencyStats skewed = LatencyStats.ofNanos(nanos);
        assertThat(skewed.p50()).isEqualTo(2.0);
        assertThat(skewed.p99()).isEqualTo(2.0);
        assertThat(skewed.max()).isEqualTo(500.0);

        LatencyStats single = LatencyStats.ofNanos(new long[] {3_000_000L});
        assertThat(single.p50()).isEqualTo(3.0);
        assertThat(single.p99()).isEqualTo(3.0);
    }

    @Test
    void should_compute_spread_across_repetitions() {
        assertThat(Spread.of(12.0, 10.0, 11.0)).isEqualTo(new Spread(3, 11.0, 10.0, 12.0));
        assertThat(Spread.of(4.0, 1.0).median()).isEqualTo(2.5);
        assertThat(Spread.of(10.0, 12.0, 11.0).width()).isEqualTo(2.0);
    }

    @Test
    void should_compute_shortfall_and_failure_rate() {
        assertThat(ResultCounts.shortfall(new int[] {10, 3, 10, 0}, 10)).isEqualTo(0.5);
        assertThat(new ResultCounts.Failures(10, 1, 1, 2).rate()).isEqualTo(0.3);
    }
}
