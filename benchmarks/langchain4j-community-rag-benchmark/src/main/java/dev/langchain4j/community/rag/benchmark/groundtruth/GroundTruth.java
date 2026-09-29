package dev.langchain4j.community.rag.benchmark.groundtruth;

import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.SyntheticMetadata;
import dev.langchain4j.community.rag.benchmark.dataset.Tier;
import dev.langchain4j.community.rag.benchmark.embedding.CacheState;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import dev.langchain4j.community.rag.benchmark.util.Checksums;
import dev.langchain4j.community.rag.benchmark.util.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exact top-k neighbours of every test query per tier, unfiltered and for each {@code bucket} filter (PRD F10,
 * F11), cached in {@code groundtruth/<dataset>/seed-<seed>/<model>/k<k>/}. Sets are recomputed only when the
 * embeddings, queries, tier size or filter they depend on change.
 */
public class GroundTruth {

    private static final Logger log = LoggerFactory.getLogger(GroundTruth.class);

    /** Filters {@code bucket < t} for about 1%, 10% and 50% selectivity (PRD D11). */
    public static final int[] BUCKET_THRESHOLDS = {1, 10, 50};

    private final Path dir;
    private final PreparedDataset dataset;
    private final EmbeddingCache cache;
    private final int k;

    public GroundTruth(Path dataDir, PreparedDataset dataset, EmbeddingCache cache, int k) {
        this.dir = dataDir.resolve("groundtruth")
                .resolve(dataset.manifest().name())
                .resolve("seed-" + dataset.tiers().seed())
                .resolve(cache.dir().getFileName().toString())
                .resolve("k" + k);
        this.dataset = dataset;
        this.cache = cache;
        this.k = k;
    }

    public Path dir() {
        return dir;
    }

    public static String name(Tier tier, Integer bucketsBelow) {
        return bucketsBelow == null ? tier.id() : tier.id() + "-bucket-lt-" + bucketsBelow;
    }

    /**
     * Ensures ground truth exists for {@code tiers} (all filters) and returns the manifests.
     */
    public List<GroundTruthManifest> compute(Set<Tier> tiers, int threads) throws IOException, InterruptedException {
        List<Tier> ordered = new ArrayList<>(tiers);
        ordered.sort(Comparator.comparingInt(tier -> tier.size(corpusSize())));
        CacheState passagesState = cache.readState("passages");
        CacheState queriesState = cache.readState("queries");
        String identity = passagesIdentity(passagesState);

        List<GroundTruthManifest> manifests = new ArrayList<>();
        boolean allCurrent = true;
        for (Tier tier : ordered) {
            for (Integer filter : filters()) {
                GroundTruthManifest current = currentManifest(tier, filter, identity, queriesState);
                allCurrent &= current != null;
                manifests.add(current);
            }
        }
        if (allCurrent) {
            log.info("Ground truth for {} is up to date", ordered);
            return manifests;
        }

        int[] prefixes =
                ordered.stream().mapToInt(tier -> tier.size(corpusSize())).toArray();
        int maxRows = prefixes[prefixes.length - 1];
        if (passagesState.rows() < maxRows) {
            throw new IOException("Embedding cache covers " + passagesState.rows() + " passages but " + maxRows
                    + " are needed; run 'rag-bench embed --tier "
                    + ordered.get(ordered.size() - 1).id() + "' first");
        }
        int[] buckets = buckets(cache.passageIds().subList(0, maxRows));

        long start = System.nanoTime();
        Neighbours[][] result;
        try (VectorMatrix passages = cache.passages();
                VectorMatrix queries = cache.queries()) {
            long total = (long) queries.rows() * maxRows;
            AtomicInteger lastPercent = new AtomicInteger();
            log.info("Brute force: {} queries x {} passages, {} threads", queries.rows(), maxRows, threads);
            result = BruteForce.search(passages, queries, prefixes, buckets, BUCKET_THRESHOLDS, k, threads, done -> {
                int percent = (int) (done * 100 / total);
                if (percent / 10 > lastPercent.get() / 10) {
                    lastPercent.set(percent);
                    log.info("Brute force {}%", percent);
                }
            });
        }
        double seconds = (System.nanoTime() - start) / 1e9;

        manifests.clear();
        List<Integer> filters = filters();
        for (int p = 0; p < ordered.size(); p++) {
            for (int f = 0; f < filters.size(); f++) {
                manifests.add(write(
                        ordered.get(p),
                        prefixes[p],
                        filters.get(f),
                        result[p][f],
                        identity,
                        queriesState,
                        seconds,
                        threads));
            }
        }
        log.info("Ground truth for {} written in {} s", ordered, String.format("%.1f", seconds));
        return manifests;
    }

    /**
     * Returns the manifest of one ground-truth set.
     */
    public GroundTruthManifest manifest(Tier tier, Integer bucketsBelow) throws IOException {
        return Json.read(dir.resolve(name(tier, bucketsBelow) + ".json"), GroundTruthManifest.class);
    }

    /**
     * Loads one ground-truth set; {@code bucketsBelow == null} is the unfiltered set.
     */
    public Neighbours load(Tier tier, Integer bucketsBelow) throws IOException {
        String name = name(tier, bucketsBelow);
        Path manifestFile = dir.resolve(name + ".json");
        if (!Files.exists(manifestFile)) {
            throw new IOException("No ground truth '" + name + "' in " + dir + "; run 'rag-bench ground-truth' first");
        }
        GroundTruthManifest manifest = Json.read(manifestFile, GroundTruthManifest.class);
        int n = manifest.queries() * manifest.k();
        ByteBuffer rowBytes =
                ByteBuffer.wrap(Files.readAllBytes(dir.resolve(name + ".rows"))).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer scoreBytes = ByteBuffer.wrap(Files.readAllBytes(dir.resolve(name + ".scores")))
                .order(ByteOrder.LITTLE_ENDIAN);
        if (rowBytes.remaining() != n * Integer.BYTES || scoreBytes.remaining() != n * Float.BYTES) {
            throw new IOException("Ground truth '" + name + "' files do not match its manifest");
        }
        int[] rows = new int[n];
        float[] scores = new float[n];
        rowBytes.asIntBuffer().get(rows);
        scoreBytes.asFloatBuffer().get(scores);
        return new Neighbours(manifest.queries(), manifest.k(), rows, scores);
    }

    private GroundTruthManifest write(
            Tier tier,
            int passages,
            Integer bucketsBelow,
            Neighbours neighbours,
            String identity,
            CacheState queriesState,
            double seconds,
            int threads)
            throws IOException {
        Files.createDirectories(dir);
        String name = name(tier, bucketsBelow);
        ByteBuffer rows =
                ByteBuffer.allocate(neighbours.rows().length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        rows.asIntBuffer().put(neighbours.rows());
        ByteBuffer scores =
                ByteBuffer.allocate(neighbours.scores().length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        scores.asFloatBuffer().put(neighbours.scores());
        Files.write(dir.resolve(name + ".rows"), rows.array());
        Files.write(dir.resolve(name + ".scores"), scores.array());
        GroundTruthManifest manifest = new GroundTruthManifest(
                GroundTruthManifest.FORMAT_VERSION,
                name,
                tier.id(),
                passages,
                bucketsBelow,
                dataset.tiers().seed(),
                k,
                neighbours.queries(),
                identity,
                queriesState.vectorsSha256(),
                Checksums.sha256(rows.array()),
                Checksums.sha256(scores.array()),
                seconds,
                threads);
        Json.write(dir.resolve(name + ".json"), manifest);
        return manifest;
    }

    private GroundTruthManifest currentManifest(Tier tier, Integer filter, String identity, CacheState queriesState)
            throws IOException {
        String name = name(tier, filter);
        Path manifestFile = dir.resolve(name + ".json");
        if (!Files.exists(manifestFile)) {
            return null;
        }
        GroundTruthManifest existing = Json.read(manifestFile, GroundTruthManifest.class);
        GroundTruthManifest expected = new GroundTruthManifest(
                GroundTruthManifest.FORMAT_VERSION,
                name,
                tier.id(),
                tier.size(corpusSize()),
                filter,
                dataset.tiers().seed(),
                k,
                queriesState.rows(),
                identity,
                queriesState.vectorsSha256(),
                null,
                null,
                0,
                0);
        boolean filesMatch = Files.exists(dir.resolve(name + ".rows"))
                && Files.exists(dir.resolve(name + ".scores"))
                && Checksums.sha256(dir.resolve(name + ".rows")).equals(existing.rowsSha256())
                && Checksums.sha256(dir.resolve(name + ".scores")).equals(existing.scoresSha256());
        return existing.sameInputs(expected) && filesMatch ? existing : null;
    }

    private int[] buckets(List<String> ids) {
        long seed = dataset.tiers().seed();
        int[] buckets = new int[ids.size()];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = SyntheticMetadata.bucket(ids.get(i), seed);
        }
        return buckets;
    }

    private static List<Integer> filters() {
        List<Integer> filters = new ArrayList<>();
        filters.add(null);
        for (int threshold : BUCKET_THRESHOLDS) {
            filters.add(threshold);
        }
        return filters;
    }

    private int corpusSize() {
        return dataset.manifest().passages();
    }

    private static String passagesIdentity(CacheState state) throws IOException {
        return Checksums.sha256(Json.toBytes(new PassagesIdentity(
                state.formatVersion(),
                state.model(),
                state.modelSha256(),
                state.dimension(),
                state.prefix(),
                state.datasetManifestSha256(),
                state.seed(),
                state.orderSha256())));
    }

    private record PassagesIdentity(
            int formatVersion,
            String model,
            String modelSha256,
            int dimension,
            String prefix,
            String datasetManifestSha256,
            long seed,
            String orderSha256) {}
}
