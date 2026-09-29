package dev.langchain4j.community.rag.benchmark.groundtruth;

import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * Exact top-k by inner product (cosine similarity on normalised vectors) over prefixes of a vector matrix.
 *
 * <p>One pass over the largest prefix yields results for every requested prefix (tiers are prefixes of the cache)
 * and for every bucket filter, so each dot product is computed once. Work is split by query; each task streams the
 * passages in blocks. Dot products accumulate in a fixed order, so results are identical across runs and thread
 * counts.
 */
public final class BruteForce {

    static final int QUERY_CHUNK = 32;
    static final int BLOCK_ROWS = 2048;

    private BruteForce() {}

    /**
     * @param prefixes         ascending prefix sizes (number of passage rows)
     * @param buckets          bucket per passage row, or {@code null} when {@code bucketThresholds} is empty
     * @param bucketThresholds filters {@code bucket < t}; results index 0 is always the unfiltered search
     * @param progress         receives the number of completed (query × row) comparisons, may be {@code null}
     * @return {@code result[prefixIndex][filterIndex]}, filter 0 = unfiltered, filter i = {@code bucketThresholds[i-1]}
     */
    public static Neighbours[][] search(
            VectorMatrix passages,
            VectorMatrix queries,
            int[] prefixes,
            int[] buckets,
            int[] bucketThresholds,
            int k,
            int threads,
            LongConsumer progress)
            throws InterruptedException {
        int dimension = passages.dimension();
        if (queries.dimension() != dimension) {
            throw new IllegalArgumentException("Query and passage dimensions differ");
        }
        for (int i = 1; i < prefixes.length; i++) {
            if (prefixes[i] < prefixes[i - 1]) {
                throw new IllegalArgumentException("Prefixes must be ascending");
            }
        }
        int maxRows = prefixes[prefixes.length - 1];
        if (maxRows > passages.rows()) {
            throw new IllegalArgumentException(maxRows + " rows requested, matrix has " + passages.rows());
        }
        int filters = 1 + bucketThresholds.length;
        int queryCount = queries.rows();
        int[][][] rows = new int[prefixes.length][filters][queryCount * k];
        float[][][] scores = new float[prefixes.length][filters][queryCount * k];
        AtomicLong done = new AtomicLong();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int first = 0; first < queryCount; first += QUERY_CHUNK) {
                int from = first;
                int to = Math.min(first + QUERY_CHUNK, queryCount);
                futures.add(executor.submit(() -> {
                    searchChunk(passages, queries, from, to, prefixes, buckets, bucketThresholds, k, rows, scores);
                    if (progress != null) {
                        progress.accept(done.addAndGet((long) (to - from) * maxRows));
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("Brute-force search failed", e.getCause());
        } finally {
            executor.shutdownNow();
        }

        Neighbours[][] result = new Neighbours[prefixes.length][filters];
        for (int p = 0; p < prefixes.length; p++) {
            for (int f = 0; f < filters; f++) {
                result[p][f] = new Neighbours(queryCount, k, rows[p][f], scores[p][f]);
            }
        }
        return result;
    }

    private static void searchChunk(
            VectorMatrix passages,
            VectorMatrix queries,
            int from,
            int to,
            int[] prefixes,
            int[] buckets,
            int[] thresholds,
            int k,
            int[][][] outRows,
            float[][][] outScores) {
        int dimension = passages.dimension();
        int queryCount = to - from;
        int filters = 1 + thresholds.length;
        float[] queryVectors = new float[queryCount * dimension];
        queries.getRows(from, queryCount, queryVectors);
        TopK[][] top = new TopK[queryCount][filters];
        for (int q = 0; q < queryCount; q++) {
            for (int f = 0; f < filters; f++) {
                top[q][f] = new TopK(k);
            }
        }

        float[] block = new float[BLOCK_ROWS * dimension];
        int prefix = 0;
        int row = 0;
        while (prefix < prefixes.length) {
            if (row == prefixes[prefix]) {
                // Equal prefixes (e.g. every tier of a tiny corpus) all snapshot here.
                for (int q = 0; q < queryCount; q++) {
                    for (int f = 0; f < filters; f++) {
                        top[q][f].writeSorted(outRows[prefix][f], outScores[prefix][f], (from + q) * k);
                    }
                }
                prefix++;
                continue;
            }
            int blockRows = Math.min(BLOCK_ROWS, prefixes[prefix] - row);
            passages.getRows(row, blockRows, block);
            for (int b = 0; b < blockRows; b++) {
                int passageRow = row + b;
                int bucket = buckets == null ? 0 : buckets[passageRow];
                int passageOffset = b * dimension;
                for (int q = 0; q < queryCount; q++) {
                    float score = dot(queryVectors, q * dimension, block, passageOffset, dimension);
                    TopK[] queryTop = top[q];
                    queryTop[0].offer(score, passageRow);
                    for (int f = 1; f < filters; f++) {
                        if (bucket < thresholds[f - 1]) {
                            queryTop[f].offer(score, passageRow);
                        }
                    }
                }
            }
            row += blockRows;
        }
    }

    static float dot(float[] a, int aOffset, float[] b, int bOffset, int length) {
        float s0 = 0, s1 = 0, s2 = 0, s3 = 0;
        int i = 0;
        for (; i + 3 < length; i += 4) {
            s0 += a[aOffset + i] * b[bOffset + i];
            s1 += a[aOffset + i + 1] * b[bOffset + i + 1];
            s2 += a[aOffset + i + 2] * b[bOffset + i + 2];
            s3 += a[aOffset + i + 3] * b[bOffset + i + 3];
        }
        for (; i < length; i++) {
            s0 += a[aOffset + i] * b[bOffset + i];
        }
        return (s0 + s1) + (s2 + s3);
    }
}
