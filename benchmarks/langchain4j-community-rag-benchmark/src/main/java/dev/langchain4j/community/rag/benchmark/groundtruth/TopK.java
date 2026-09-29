package dev.langchain4j.community.rag.benchmark.groundtruth;

import java.util.Arrays;

/**
 * Bounded selection of the {@code k} best (score, row) pairs. Higher score wins; equal scores are broken by the
 * lower row, so results are fully deterministic.
 */
final class TopK {

    private final int k;
    private final float[] scores;
    private final int[] rows;
    private int size;

    TopK(int k) {
        this.k = k;
        this.scores = new float[k];
        this.rows = new int[k];
    }

    void offer(float score, int row) {
        if (size < k) {
            scores[size] = score;
            rows[size] = row;
            siftUp(size++);
        } else if (better(score, row, scores[0], rows[0])) {
            scores[0] = score;
            rows[0] = row;
            siftDown(0);
        }
    }

    /**
     * Writes the selection best-first into {@code outRows}/{@code outScores} at {@code offset}; unused slots get
     * row {@code -1} and score {@code NaN}.
     */
    void writeSorted(int[] outRows, float[] outScores, int offset) {
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> better(scores[a], rows[a], scores[b], rows[b]) ? -1 : a.equals(b) ? 0 : 1);
        for (int i = 0; i < k; i++) {
            outRows[offset + i] = i < size ? rows[order[i]] : -1;
            outScores[offset + i] = i < size ? scores[order[i]] : Float.NaN;
        }
    }

    /** Min-heap on "worse first": the root is the worst retained element. */
    private static boolean better(float s1, int r1, float s2, int r2) {
        return s1 > s2 || (s1 == s2 && r1 < r2);
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (!better(scores[parent], rows[parent], scores[i], rows[i])) {
                return;
            }
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            if (left >= size) {
                return;
            }
            int worst = left;
            int right = left + 1;
            if (right < size && better(scores[left], rows[left], scores[right], rows[right])) {
                worst = right;
            }
            if (!better(scores[i], rows[i], scores[worst], rows[worst])) {
                return;
            }
            swap(i, worst);
            i = worst;
        }
    }

    private void swap(int a, int b) {
        float s = scores[a];
        scores[a] = scores[b];
        scores[b] = s;
        int r = rows[a];
        rows[a] = rows[b];
        rows[b] = r;
    }
}
