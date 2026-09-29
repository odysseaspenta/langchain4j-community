package dev.langchain4j.community.rag.benchmark.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.rag.benchmark.util.Checksums;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetPreparerTest {

    @TempDir
    Path dataDir;

    DatasetSource source;

    @BeforeEach
    void placeRawFiles() throws IOException {
        // Files already in place and matching their checksums, so nothing is downloaded.
        Path raw = TestBeir.write(PreparedDataset.datasetDir(dataDir, "tiny").resolve("raw"), 50, 5);
        List<DatasetSource.RemoteFile> files = new ArrayList<>();
        for (String path : List.of(BeirDataset.CORPUS, BeirDataset.QUERIES, BeirDataset.TEST_QRELS)) {
            Path file = raw.resolve(path);
            files.add(new DatasetSource.RemoteFile(path, Files.size(file), Checksums.sha256(file)));
        }
        source = new DatasetSource("tiny", "test/tiny", "rev", files, 50, 5);
    }

    @Test
    void should_write_manifests_and_tiers() throws Exception {
        PreparedDataset prepared = new DatasetPreparer(dataDir, new Downloader()).prepare(source, 42);

        assertThat(prepared.manifest().passages()).isEqualTo(50);
        assertThat(prepared.manifest().testQueries()).isEqualTo(5);
        assertThat(prepared.manifest().qrelsPairs()).isEqualTo(15);
        assertThat(prepared.manifest().relevantPassages()).isEqualTo(10);
        assertThat(prepared.tiers().tiers()).containsOnlyKeys("smoke", "standard", "full");
        for (Tier tier : Tier.values()) {
            assertThat(prepared.loadOrder(tier)).hasSize(50);
        }
        assertThat(new HashSet<>(prepared.priorityOrder())).isEqualTo(new HashSet<>(prepared.loadOrder(Tier.FULL)));

        PreparedDataset reopened = PreparedDataset.open(dataDir, "tiny", 42);
        assertThat(reopened.tiers()).isEqualTo(prepared.tiers());
        assertThat(reopened.manifestSha256()).isEqualTo(prepared.manifestSha256());
    }

    @Test
    void should_be_idempotent_and_byte_identical() throws Exception {
        PreparedDataset first = new DatasetPreparer(dataDir, new Downloader()).prepare(source, 42);
        Path smoke = first.tiersDir().resolve("smoke.ids");
        byte[] before = Files.readAllBytes(smoke);

        Files.delete(smoke);
        PreparedDataset second = new DatasetPreparer(dataDir, new Downloader()).prepare(source, 42);

        assertThat(Files.readAllBytes(smoke)).isEqualTo(before);
        assertThat(second.tiers()).isEqualTo(first.tiers());
    }

    @Test
    void should_reject_unexpected_counts() {
        DatasetSource wrong = new DatasetSource("tiny", "test/tiny", "rev", source.files(), 51, 5);

        assertThatThrownBy(() -> new DatasetPreparer(dataDir, new Downloader()).prepare(wrong, 42))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Expected 51 passages");
    }

    @Test
    void should_fail_to_open_unprepared_dataset() {
        assertThatThrownBy(() -> PreparedDataset.open(dataDir, "tiny", 42))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("rag-bench prepare");
    }
}
