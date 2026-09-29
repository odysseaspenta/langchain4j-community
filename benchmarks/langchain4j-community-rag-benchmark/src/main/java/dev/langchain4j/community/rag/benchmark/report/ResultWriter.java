package dev.langchain4j.community.rag.benchmark.report;

import dev.langchain4j.community.rag.benchmark.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Writes a run to {@code <resultsDir>/<yyyy-MM-dd>-<arcadedb-version>-<profile>[-n]/}: {@code result.json},
 * {@code report.md} and, when the JVM was started by the {@code rag-bench} script, the GC log (PRD F20, F21).
 */
public final class ResultWriter {

    /** System property set by the {@code rag-bench} script to the JVM's GC log file. */
    public static final String GC_LOG_PROPERTY = "rag.bench.gcLog";

    private ResultWriter() {}

    public static Path write(Path resultsDir, String storeVersion, RunResult result) throws IOException {
        String base = LocalDate.now() + "-" + storeVersion + "-" + result.profile();
        Path dir = resultsDir.resolve(base);
        for (int n = 2; Files.exists(dir); n++) {
            dir = resultsDir.resolve(base + "-" + n);
        }
        Files.createDirectories(dir);
        Json.write(dir.resolve("result.json"), result);
        Files.writeString(dir.resolve("report.md"), MarkdownSummary.render(result), StandardCharsets.UTF_8);
        String gcLog = System.getProperty(GC_LOG_PROPERTY);
        if (gcLog != null && Files.exists(Path.of(gcLog))) {
            Files.copy(Path.of(gcLog), dir.resolve("gc.log"), StandardCopyOption.REPLACE_EXISTING);
        }
        return dir;
    }
}
