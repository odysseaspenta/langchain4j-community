package dev.langchain4j.community.rag.benchmark.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Settings shared by every benchmark command.
 *
 * <p>Each setting is resolved in this order: command-line option, JVM system property,
 * environment variable, built-in default.
 *
 * @param dataDir    where datasets, embedding caches, ground truth and databases live; must be outside the git repository
 * @param resultsDir where run results are written ({@code <resultsDir>/<date>-<arcadedb-version>-<profile>/})
 * @param seed       seed for every random choice (tier sampling, {@code bucket} metadata)
 * @param machineClass {@code reference} for publishable numbers, {@code dev} otherwise (PRD N2a)
 */
public record BenchmarkConfig(Path dataDir, Path resultsDir, long seed, String machineClass) {

    public static final long DEFAULT_SEED = 42L;
    public static final String DEV = "dev";
    public static final String REFERENCE = "reference";

    public enum Setting {
        DATA_DIR("rag.bench.dataDir", "RAG_BENCH_DATA_DIR"),
        RESULTS_DIR("rag.bench.resultsDir", "RAG_BENCH_RESULTS_DIR"),
        SEED("rag.bench.seed", "RAG_BENCH_SEED"),
        MACHINE_CLASS("rag.bench.machineClass", "RAG_BENCH_MACHINE_CLASS");

        private final String systemProperty;
        private final String environmentVariable;

        Setting(String systemProperty, String environmentVariable) {
            this.systemProperty = systemProperty;
            this.environmentVariable = environmentVariable;
        }

        public String systemProperty() {
            return systemProperty;
        }

        public String environmentVariable() {
            return environmentVariable;
        }
    }

    /**
     * Resolves the configuration.
     *
     * @param cli        values given on the command line; absent keys fall through
     * @param properties JVM system properties
     * @param env        environment variables
     * @param workingDir directory relative paths and the git repository are resolved from
     */
    public static BenchmarkConfig resolve(
            Map<Setting, String> cli, Properties properties, Map<String, String> env, Path workingDir) {
        Path cwd = workingDir.toAbsolutePath().normalize();
        Optional<Path> repoRoot = findRepositoryRoot(cwd);

        Path dataDir = lookup(Setting.DATA_DIR, cli, properties, env)
                .map(value -> cwd.resolve(value).normalize())
                .orElseGet(() -> Path.of(properties.getProperty("user.home"), ".cache", "langchain4j-rag-benchmark"));
        Path resultsDir = lookup(Setting.RESULTS_DIR, cli, properties, env)
                .map(value -> cwd.resolve(value).normalize())
                .orElseGet(() -> repoRoot.orElse(cwd).resolve("benchmarks").resolve("results"));
        long seed = lookup(Setting.SEED, cli, properties, env)
                .map(value -> parseSeed(value))
                .orElse(DEFAULT_SEED);

        String machineClass =
                lookup(Setting.MACHINE_CLASS, cli, properties, env).orElse(DEV);
        if (!machineClass.equals(DEV) && !machineClass.equals(REFERENCE)) {
            throw new IllegalArgumentException(
                    "Machine class must be '" + DEV + "' or '" + REFERENCE + "', got '" + machineClass + "'");
        }

        repoRoot.ifPresent(root -> {
            if (dataDir.startsWith(root)) {
                throw new IllegalArgumentException("Data directory " + dataDir + " is inside the git repository " + root
                        + "; point " + Setting.DATA_DIR.environmentVariable() + " or --data-dir elsewhere");
            }
        });
        return new BenchmarkConfig(dataDir, resultsDir, seed, machineClass);
    }

    /**
     * Returns the nearest ancestor of {@code dir} (inclusive) containing {@code .git}, if any.
     */
    public static Optional<Path> findRepositoryRoot(Path dir) {
        for (Path candidate = dir.toAbsolutePath().normalize(); candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve(".git"))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> lookup(
            Setting setting, Map<Setting, String> cli, Properties properties, Map<String, String> env) {
        String value = cli.get(setting);
        if (isBlank(value)) {
            value = properties.getProperty(setting.systemProperty());
        }
        if (isBlank(value)) {
            value = env.get(setting.environmentVariable());
        }
        return isBlank(value) ? Optional.empty() : Optional.of(value.trim());
    }

    private static long parseSeed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Seed must be a long, got '" + value + "'", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
