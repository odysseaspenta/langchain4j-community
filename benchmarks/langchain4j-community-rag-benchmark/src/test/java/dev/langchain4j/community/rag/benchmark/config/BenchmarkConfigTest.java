package dev.langchain4j.community.rag.benchmark.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig.Setting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkConfigTest {

    @TempDir
    Path tmp;

    Path repo;
    Path home;
    Properties properties;

    @BeforeEach
    void setUp() throws IOException {
        repo = Files.createDirectories(tmp.resolve("repo"));
        Files.createDirectory(repo.resolve(".git"));
        home = Files.createDirectories(tmp.resolve("home"));
        properties = new Properties();
        properties.setProperty("user.home", home.toString());
    }

    @Test
    void should_use_defaults() {
        BenchmarkConfig config = BenchmarkConfig.resolve(Map.of(), properties, Map.of(), repo.resolve("sub"));

        assertThat(config.dataDir()).isEqualTo(home.resolve(".cache/langchain4j-rag-benchmark"));
        assertThat(config.resultsDir()).isEqualTo(repo.resolve("benchmarks/results"));
        assertThat(config.seed()).isEqualTo(BenchmarkConfig.DEFAULT_SEED);
    }

    @Test
    void should_prefer_cli_over_system_property_over_environment() {
        Map<String, String> env = Map.of(
                "RAG_BENCH_DATA_DIR", tmp.resolve("env").toString(),
                "RAG_BENCH_RESULTS_DIR", tmp.resolve("env-results").toString(),
                "RAG_BENCH_SEED", "1");
        properties.setProperty("rag.bench.dataDir", tmp.resolve("prop").toString());
        properties.setProperty("rag.bench.seed", "2");

        BenchmarkConfig config = BenchmarkConfig.resolve(
                Map.of(Setting.DATA_DIR, tmp.resolve("cli").toString()), properties, env, repo);

        assertThat(config.dataDir()).isEqualTo(tmp.resolve("cli"));
        assertThat(config.seed()).isEqualTo(2L);
        assertThat(config.resultsDir()).isEqualTo(tmp.resolve("env-results"));
    }

    @Test
    void should_resolve_relative_paths_against_working_directory() {
        BenchmarkConfig config =
                BenchmarkConfig.resolve(Map.of(Setting.DATA_DIR, "../data"), properties, Map.of(), repo);

        assertThat(config.dataDir()).isEqualTo(tmp.resolve("data"));
    }

    @Test
    void should_reject_data_dir_inside_repository() {
        assertThatThrownBy(() -> BenchmarkConfig.resolve(Map.of(Setting.DATA_DIR, "cache"), properties, Map.of(), repo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inside the git repository");
    }

    @Test
    void should_reject_non_numeric_seed() {
        assertThatThrownBy(() -> BenchmarkConfig.resolve(Map.of(Setting.SEED, "abc"), properties, Map.of(), repo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void should_fall_back_to_working_directory_outside_a_repository() {
        Path outside = tmp.resolve("outside");

        BenchmarkConfig config = BenchmarkConfig.resolve(Map.of(), properties, Map.of(), outside);

        assertThat(BenchmarkConfig.findRepositoryRoot(outside)).isEmpty();
        assertThat(config.resultsDir()).isEqualTo(outside.resolve("benchmarks/results"));
    }
}
