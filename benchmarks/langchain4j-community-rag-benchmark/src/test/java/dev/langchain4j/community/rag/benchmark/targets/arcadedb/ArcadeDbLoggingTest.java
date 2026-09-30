package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArcadeDbLoggingTest {

    @TempDir
    Path dir;

    String previous;

    @BeforeEach
    void saveProperty() {
        previous = System.getProperty(ArcadeDbLogging.CONFIG_PROPERTY);
        System.clearProperty(ArcadeDbLogging.CONFIG_PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (previous == null) {
            System.clearProperty(ArcadeDbLogging.CONFIG_PROPERTY);
        } else {
            System.setProperty(ArcadeDbLogging.CONFIG_PROPERTY, previous);
        }
    }

    @Test
    void should_write_a_configuration_that_logs_into_the_given_directory() throws Exception {
        Path logs = dir.resolve("logs");

        assertThat(ArcadeDbLogging.configure(logs)).isEqualTo(logs);

        Path config = Path.of(System.getProperty(ArcadeDbLogging.CONFIG_PROPERTY));
        assertThat(config).isEqualTo(logs.resolve("arcadedb-log.properties").toAbsolutePath());
        assertThat(Files.readString(config))
                .contains("java.util.logging.FileHandler.pattern = " + logs.toAbsolutePath() + "/arcadedb.log")
                .contains("com.arcadedb.index.vector.level = WARNING")
                .doesNotContain("./log");
    }

    @Test
    void should_keep_an_explicit_configuration() throws Exception {
        System.setProperty(ArcadeDbLogging.CONFIG_PROPERTY, "/explicit.properties");

        assertThat(ArcadeDbLogging.configure(dir.resolve("logs"))).isNull();

        assertThat(System.getProperty(ArcadeDbLogging.CONFIG_PROPERTY)).isEqualTo("/explicit.properties");
        assertThat(dir.resolve("logs")).doesNotExist();
    }
}
