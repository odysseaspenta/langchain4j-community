package dev.langchain4j.community.rag.benchmark.dataset;

import dev.langchain4j.community.rag.benchmark.util.Checksums;
import dev.langchain4j.community.rag.benchmark.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads and verifies a dataset, then builds its tier files (B02). Re-running is cheap and changes nothing
 * when the files are already in place.
 */
public class DatasetPreparer {

    private static final Logger log = LoggerFactory.getLogger(DatasetPreparer.class);

    private final Path dataDir;
    private final Downloader downloader;

    public DatasetPreparer(Path dataDir, Downloader downloader) {
        this.dataDir = dataDir;
        this.downloader = downloader;
    }

    public PreparedDataset prepare(DatasetSource source, long seed) throws IOException, InterruptedException {
        Path datasetDir = PreparedDataset.datasetDir(dataDir, source.name());
        Path rawDir = datasetDir.resolve("raw");
        for (DatasetSource.RemoteFile file : source.files()) {
            downloader.download(source.uri(file), rawDir.resolve(file.path()), file.size(), file.sha256());
        }

        BeirDataset dataset = new BeirDataset(rawDir);
        log.info("Reading {} corpus ids", source.name());
        List<String> passageIds = dataset.passageIds();
        Qrels qrels = dataset.testQrels();
        List<Query> queries = dataset.testQueries(qrels);
        Set<String> relevant = qrels.relevantPassageIds();
        expect("passages", source.expectedPassages(), passageIds.size());
        expect("test queries", source.expectedTestQueries(), queries.size());
        if (queries.size() != qrels.byQuery().size()) {
            throw new IOException(
                    qrels.byQuery().size() + " queries have judgments but only " + queries.size() + " were found");
        }

        DatasetManifest manifest = new DatasetManifest(
                source.name(),
                source.repository(),
                source.revision(),
                source.files(),
                passageIds.size(),
                queries.size(),
                qrels.pairCount(),
                relevant.size());
        Path manifestFile = datasetDir.resolve(PreparedDataset.MANIFEST);
        Json.write(manifestFile, manifest);
        String manifestSha256 = Checksums.sha256(manifestFile);
        log.info(
                "{}: {} passages, {} test queries, {} judgments, {} relevant passages",
                source.name(),
                manifest.passages(),
                manifest.testQueries(),
                manifest.qrelsPairs(),
                manifest.relevantPassages());

        Path tiersDir = PreparedDataset.tiersDir(dataDir, source.name(), seed);
        TierManifest tiers = existingTiers(tiersDir, seed, manifestSha256);
        if (tiers == null) {
            tiers = writeTiers(tiersDir, passageIds, relevant, seed, manifestSha256);
        } else {
            log.info("Tier files in {} are up to date", tiersDir);
        }
        return new PreparedDataset(dataset, manifest, manifestSha256, tiersDir, tiers);
    }

    private static TierManifest writeTiers(
            Path tiersDir, List<String> passageIds, Set<String> relevant, long seed, String manifestSha256)
            throws IOException {
        if (relevant.size() > Tier.SMOKE.size(passageIds.size())) {
            throw new IOException(relevant.size() + " relevant passages do not fit in the smoke tier");
        }
        Files.createDirectories(tiersDir);
        List<String> priority = TierSampler.priorityOrder(passageIds, relevant, seed);
        IdList priorityList = IdFiles.write(tiersDir.resolve("priority.ids"), priority);
        Map<String, IdList> tierLists = new LinkedHashMap<>();
        for (Tier tier : Tier.values()) {
            List<String> loadOrder = TierSampler.loadOrder(priority, tier, seed);
            tierLists.put(tier.id(), IdFiles.write(tiersDir.resolve(tier.id() + ".ids"), loadOrder));
            log.info("Tier {}: {} passages", tier.id(), loadOrder.size());
        }
        TierManifest tiers = new TierManifest(
                TierSampler.ALGORITHM_VERSION, seed, manifestSha256, relevant.size(), priorityList, tierLists);
        Json.write(tiersDir.resolve(PreparedDataset.MANIFEST), tiers);
        return tiers;
    }

    private static TierManifest existingTiers(Path tiersDir, long seed, String manifestSha256) throws IOException {
        Path file = tiersDir.resolve(PreparedDataset.MANIFEST);
        if (!Files.exists(file)) {
            return null;
        }
        TierManifest tiers = Json.read(file, TierManifest.class);
        boolean current = tiers.algorithmVersion() == TierSampler.ALGORITHM_VERSION
                && tiers.seed() == seed
                && tiers.datasetManifestSha256().equals(manifestSha256)
                && tiers.tiers().keySet().size() == Tier.values().length
                && IdFiles.matches(tiersDir, tiers.priority());
        for (IdList list : tiers.tiers().values()) {
            current = current && IdFiles.matches(tiersDir, list);
        }
        return current ? tiers : null;
    }

    private static void expect(String what, int expected, int actual) throws IOException {
        if (expected > 0 && expected != actual) {
            throw new IOException("Expected " + expected + " " + what + " but found " + actual);
        }
    }
}
