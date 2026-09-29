package dev.langchain4j.community.rag.benchmark.groundtruth;

import java.util.Objects;

/**
 * Describes one ground-truth set ({@code <name>.rows} + {@code <name>.scores}).
 *
 * @param passages                number of passage rows searched (the tier size)
 * @param bucketsBelow            filter {@code bucket < bucketsBelow}, or {@code null} for the unfiltered set
 * @param passagesIdentitySha256  identifies the embedding cache content (model, prefix, dataset, seed, order);
 *                                the cache is append-only, so this plus {@code passages} fixes the searched vectors
 * @param queriesSha256           SHA-256 of the query vectors
 */
public record GroundTruthManifest(
        int formatVersion,
        String name,
        String tier,
        int passages,
        Integer bucketsBelow,
        long bucketSeed,
        int k,
        int queries,
        String passagesIdentitySha256,
        String queriesSha256,
        String rowsSha256,
        String scoresSha256,
        double seconds,
        int threads) {

    public static final int FORMAT_VERSION = 1;

    boolean sameInputs(GroundTruthManifest other) {
        return formatVersion == other.formatVersion
                && name.equals(other.name)
                && passages == other.passages
                && Objects.equals(bucketsBelow, other.bucketsBelow)
                && bucketSeed == other.bucketSeed
                && k == other.k
                && queries == other.queries
                && passagesIdentitySha256.equals(other.passagesIdentitySha256)
                && queriesSha256.equals(other.queriesSha256);
    }
}
