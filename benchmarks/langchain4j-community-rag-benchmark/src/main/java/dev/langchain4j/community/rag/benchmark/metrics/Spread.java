package dev.langchain4j.community.rag.benchmark.metrics;

import java.util.Arrays;

/**
 * Median and range of one metric across repetitions (PRD F16). The range is the noise band used to decide whether
 * a difference between runs is significant.
 */
public record Spread(int repetitions, double median, double min, double max) {

    public static Spread of(double... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("No values");
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        double median = n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
        return new Spread(n, median, sorted[0], sorted[n - 1]);
    }

    public double width() {
        return max - min;
    }
}
