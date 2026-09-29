package dev.langchain4j.community.rag.benchmark.dataset;

import java.util.Map;

/**
 * Describes the tier id files in {@code datasets/<name>/tiers/seed-<seed>/}.
 *
 * @param priority priority order (embedding-cache order); tier members are its prefixes
 * @param tiers    per tier id, the members in load order
 */
public record TierManifest(
        int algorithmVersion,
        long seed,
        String datasetManifestSha256,
        int relevantPassages,
        IdList priority,
        Map<String, IdList> tiers) {}
