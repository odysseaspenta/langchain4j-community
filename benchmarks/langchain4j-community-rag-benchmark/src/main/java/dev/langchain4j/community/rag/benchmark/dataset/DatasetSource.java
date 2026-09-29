package dev.langchain4j.community.rag.benchmark.dataset;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A BEIR-format dataset pinned to an exact Hugging Face revision and file checksums.
 *
 * @param name                    short name used for directories and results, e.g. {@code nq}
 * @param repository              Hugging Face dataset repository, e.g. {@code mteb/nq}
 * @param revision                git commit of the repository
 * @param files                   files to download; paths follow the BEIR layout (see {@link BeirDataset})
 * @param expectedPassages        corpus size the download must contain
 * @param expectedTestQueries     number of test queries the download must contain
 */
public record DatasetSource(
        String name,
        String repository,
        String revision,
        List<RemoteFile> files,
        int expectedPassages,
        int expectedTestQueries) {

    /**
     * BEIR Natural Questions (PRD D4). CC BY-SA; cite BEIR (Thakur et al., 2021) and NQ (Kwiatkowski et al., 2019).
     */
    public static final DatasetSource NQ = new DatasetSource(
            "nq",
            "mteb/nq",
            "b84726e65fd226125cf7c0cbeeb5c214d49e8187",
            List.of(
                    new RemoteFile(
                            BeirDataset.CORPUS,
                            1_460_859_840L,
                            "309dd1d9e18d80e7703b4c0682d3533463e65645ac27278ceb6c3d6c5aa90f90"),
                    new RemoteFile(
                            BeirDataset.QUERIES,
                            275_763L,
                            "53253977f9213bc84968ffdbf4ed70f5a00b57ee9b2db765352cf894c80d620a"),
                    new RemoteFile(
                            BeirDataset.TEST_QRELS,
                            87_138L,
                            "6df0cd2cbbe88504b64c68f21e946a759b62c4d864225720cec256f0196e2210")),
            2_681_468,
            3_452);

    private static final Map<String, DatasetSource> KNOWN = Map.of(NQ.name(), NQ);

    public static Optional<DatasetSource> byName(String name) {
        return Optional.ofNullable(KNOWN.get(name));
    }

    public URI uri(RemoteFile file) {
        return URI.create("https://huggingface.co/datasets/" + repository + "/resolve/" + revision + "/" + file.path());
    }

    /**
     * @param path   path inside the repository and inside the local dataset directory
     * @param size   size in bytes
     * @param sha256 lowercase hex SHA-256 of the content
     */
    public record RemoteFile(String path, long size, String sha256) {}
}
