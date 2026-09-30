package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Logging for embedded ArcadeDB. Without a configuration ArcadeDB loads its bundled {@code arcadedb-log.properties},
 * which writes {@code ./log/arcadedb.log} into the working directory (for a run from the repository root: into the
 * repository, marking the run dirty). ArcadeDB reads {@code java.util.logging.config.file} instead when it is set, so
 * the benchmark writes its own configuration: the log goes to the data directory and the per-search INFO line of
 * 26.7.2 ({@code com.arcadedb.index.vector}) is off from the start (PRD F18).
 */
public final class ArcadeDbLogging {

    static final String CONFIG_PROPERTY = "java.util.logging.config.file";

    private ArcadeDbLogging() {}

    /**
     * Must run before ArcadeDB first logs. Keeps a configuration file given explicitly with {@code -D}.
     *
     * @return the log directory used, or {@code null} if an explicit configuration was kept
     */
    public static Path configure(Path logDir) throws IOException {
        if (System.getProperty(CONFIG_PROPERTY) != null) {
            return null;
        }
        Files.createDirectories(logDir);
        Path config = logDir.resolve("arcadedb-log.properties");
        Files.writeString(config, configuration(logDir.toAbsolutePath()));
        System.setProperty(CONFIG_PROPERTY, config.toAbsolutePath().toString());
        return logDir;
    }

    static String configuration(Path logDir) {
        return """
                handlers = java.util.logging.ConsoleHandler, java.util.logging.FileHandler
                .level = INFO
                com.arcadedb.level = INFO
                com.arcadedb.index.vector.level = WARNING
                java.util.logging.ConsoleHandler.level = INFO
                java.util.logging.ConsoleHandler.formatter = com.arcadedb.utility.AnsiLogFormatter
                java.util.logging.FileHandler.level = INFO
                java.util.logging.FileHandler.pattern = %s/arcadedb.log
                java.util.logging.FileHandler.formatter = com.arcadedb.log.LogFormatter
                java.util.logging.FileHandler.limit = 100000000
                java.util.logging.FileHandler.count = 10
                """
                .formatted(logDir.toString().replace("%", "%%").replace("\\", "/"));
    }
}
