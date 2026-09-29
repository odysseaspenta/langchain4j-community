package dev.langchain4j.community.rag.benchmark.dataset;

import dev.langchain4j.community.rag.benchmark.util.Checksums;
import dev.langchain4j.community.rag.benchmark.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A downloaded, verified dataset with its tier files, as laid out under the data directory:
 *
 * <pre>
 * datasets/&lt;name&gt;/raw/                 BEIR files
 * datasets/&lt;name&gt;/manifest.json        {@link DatasetManifest}
 * datasets/&lt;name&gt;/tiers/seed-&lt;seed&gt;/  priority.ids, smoke.ids, standard.ids, full.ids, manifest.json
 * </pre>
 */
public record PreparedDataset(
        BeirDataset dataset, DatasetManifest manifest, String manifestSha256, Path tiersDir, TierManifest tiers) {

    static final String MANIFEST = "manifest.json";

    public static Path datasetDir(Path dataDir, String name) {
        return dataDir.resolve("datasets").resolve(name);
    }

    public static Path tiersDir(Path dataDir, String name, long seed) {
        return datasetDir(dataDir, name).resolve("tiers").resolve("seed-" + seed);
    }

    /**
     * Opens a dataset previously prepared by {@link DatasetPreparer} without re-verifying the raw files.
     */
    public static PreparedDataset open(Path dataDir, String name, long seed) throws IOException {
        Path datasetDir = datasetDir(dataDir, name);
        Path tiersDir = tiersDir(dataDir, name, seed);
        Path manifestFile = datasetDir.resolve(MANIFEST);
        Path tierManifestFile = tiersDir.resolve(MANIFEST);
        if (!Files.exists(manifestFile) || !Files.exists(tierManifestFile)) {
            throw new IOException("Dataset '" + name + "' (seed " + seed + ") is not prepared in " + dataDir
                    + "; run 'rag-bench prepare' first");
        }
        return new PreparedDataset(
                new BeirDataset(datasetDir.resolve("raw")),
                Json.read(manifestFile, DatasetManifest.class),
                Checksums.sha256(manifestFile),
                tiersDir,
                Json.read(tierManifestFile, TierManifest.class));
    }

    /**
     * Returns the priority order; each tier's members are a prefix of it (see {@link TierSampler}).
     */
    public List<String> priorityOrder() throws IOException {
        return IdFiles.read(tiersDir.resolve(tiers.priority().file()));
    }

    /**
     * Returns the tier's passage ids in load order.
     */
    public List<String> loadOrder(Tier tier) throws IOException {
        return IdFiles.read(tiersDir.resolve(tiers.tiers().get(tier.id()).file()));
    }
}
