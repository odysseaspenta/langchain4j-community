package dev.langchain4j.community.rag.benchmark.report;

import dev.langchain4j.community.rag.benchmark.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Writes a run to {@code <resultsDir>/<yyyy-MM-dd>-<arcadedb-version>-<profile>[-<label>][-n]/}: {@code result.json},
 * {@code report.md} and, when the JVM was started by the {@code rag-bench} script, the GC log (PRD F20, F21).
 */
public final class ResultWriter {

    /** System property set by the {@code rag-bench} script to the JVM's GC log file. */
    public static final String GC_LOG_PROPERTY = "rag.bench.gcLog";

    private ResultWriter() {}

    public static Path write(Path resultsDir, String storeVersion, RunResult result) throws IOException {
        Path dir = createRunDir(resultsDir, storeVersion, result.profile(), null);
        writeTo(dir, result);
        return dir;
    }

    /** Creates a new, unused run directory. */
    public static Path createRunDir(Path resultsDir, String storeVersion, String profile, String label)
            throws IOException {
        String base = LocalDate.now() + "-" + storeVersion + "-" + profile + (label == null ? "" : "-" + label);
        Path dir = resultsDir.resolve(base);
        for (int n = 2; Files.exists(dir); n++) {
            dir = resultsDir.resolve(base + "-" + n);
        }
        Files.createDirectories(dir);
        return dir;
    }

    /** Writes (or rewrites, for a checkpoint) the run's files into {@code dir}. */
    public static void writeTo(Path dir, RunResult result) throws IOException {
        Json.write(dir.resolve("result.json"), result);
        Files.writeString(dir.resolve("report.md"), MarkdownSummary.render(result), StandardCharsets.UTF_8);
        String gcLog = System.getProperty(GC_LOG_PROPERTY);
        if (gcLog != null && Files.exists(Path.of(gcLog))) {
            Files.copy(Path.of(gcLog), dir.resolve("gc.log"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
