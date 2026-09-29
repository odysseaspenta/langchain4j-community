package dev.langchain4j.community.rag.benchmark.dataset;

import dev.langchain4j.community.rag.benchmark.util.Checksums;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepares a tiny dataset ({@link TestBeir}) under a data directory, as {@code rag-bench prepare} would.
 */
public final class TestDatasets {

    private TestDatasets() {}

    public static PreparedDataset prepare(Path dataDir, int passages, int queries, long seed) throws Exception {
        Path raw = TestBeir.write(PreparedDataset.datasetDir(dataDir, "tiny").resolve("raw"), passages, queries);
        List<DatasetSource.RemoteFile> files = new ArrayList<>();
        for (String path : List.of(BeirDataset.CORPUS, BeirDataset.QUERIES, BeirDataset.TEST_QRELS)) {
            Path file = raw.resolve(path);
            files.add(new DatasetSource.RemoteFile(path, Files.size(file), Checksums.sha256(file)));
        }
        DatasetSource source = new DatasetSource("tiny", "test/tiny", "rev", files, passages, queries);
        return new DatasetPreparer(dataDir, new Downloader()).prepare(source, seed);
    }
}
