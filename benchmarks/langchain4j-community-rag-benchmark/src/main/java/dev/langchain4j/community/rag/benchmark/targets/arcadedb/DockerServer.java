package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.rag.benchmark.util.CpuSet;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An ArcadeDB server in a Docker container on this host (PRD F17), driven through the {@code docker} CLI.
 *
 * <p>The container is pinned to a cpuset, runs with a fixed heap ({@code -Xms = -Xmx}), ZGC with a GC log, and a
 * logging configuration that silences the per-search INFO line of 26.7.2 (F18). Its databases, logs and GC log live
 * in a host directory so a loaded database outlives the container (F15). The HTTP port is published on loopback
 * only. A shutdown hook removes the container if the benchmark exits without closing it.
 */
public final class DockerServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DockerServer.class);

    static final int HTTP_PORT = 2480;
    static final String USER = "root";
    // Loopback-only throwaway server; not a secret.
    static final String PASSWORD = "rag-bench-local";
    /** The image's default collector, made explicit so it is recorded and cannot drift between images. */
    static final String SERVER_GC = "-XX:+UseZGC -XX:+ZGenerational";

    private static final String CONTAINER_HOME = "/home/arcadedb";
    private static final String LOG_CONFIG = "arcadedb-log.properties";
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration DOCKER_TIMEOUT = Duration.ofMinutes(5);
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /**
     * @param image   Docker image, e.g. {@code arcadedata/arcadedb:26.7.2}
     * @param cpus    cpuset the container is pinned to
     * @param heap    server heap, e.g. {@code 4g} ({@code -Xms = -Xmx})
     * @param hostDir host directory holding {@code databases/}, {@code log/} and the logging configuration
     */
    public record Options(String image, CpuSet cpus, String heap, Path hostDir) {}

    private final Options options;
    private final String name;
    private final Thread shutdownHook;
    private int port;
    private boolean closed;

    private DockerServer(Options options) {
        this.options = options;
        this.name = "rag-bench-arcadedb-" + ProcessHandle.current().pid() + "-" + COUNTER.incrementAndGet();
        this.shutdownHook = new Thread(this::remove, "remove-" + name);
    }

    /**
     * Starts the container and returns once the server answers {@code /api/v1/ready}.
     */
    public static DockerServer start(Options options) throws IOException, InterruptedException {
        DockerServer server = new DockerServer(options);
        server.run();
        return server;
    }

    public String host() {
        return "127.0.0.1";
    }

    public int port() {
        return port;
    }

    public String containerName() {
        return name;
    }

    /** Host directory the container keeps its databases in, one subdirectory per database. */
    public Path databasesDir() {
        return options.hostDir().resolve("databases");
    }

    private void run() throws IOException, InterruptedException {
        Path hostDir = options.hostDir().toAbsolutePath();
        Files.createDirectories(hostDir.resolve("databases"));
        Files.createDirectories(hostDir.resolve("log"));
        Path logConfig = hostDir.resolve(LOG_CONFIG);
        Files.writeString(logConfig, logConfiguration());

        List<String> command = new ArrayList<>(List.of("docker", "run", "--detach", "--name", name));
        command.addAll(List.of("--cpuset-cpus", options.cpus().toString()));
        command.addAll(List.of("--publish", "127.0.0.1::" + HTTP_PORT));
        command.addAll(List.of("--env", "ARCADEDB_OPTS_MEMORY=-Xms" + options.heap() + " -Xmx" + options.heap()));
        command.addAll(List.of(
                "--env",
                "JAVA_OPTS=" + SERVER_GC + " -Xlog:gc*:file=" + CONTAINER_HOME
                        + "/log/gc.log:time,uptime,level,tags"));
        command.addAll(List.of(
                "--env",
                "ARCADEDB_SETTINGS=-Darcadedb.server.rootPassword=" + PASSWORD
                        + " -Djava.util.logging.config.file=" + CONTAINER_HOME + "/bench/" + LOG_CONFIG));
        command.addAll(List.of("--volume", hostDir.resolve("databases") + ":" + CONTAINER_HOME + "/databases"));
        command.addAll(List.of("--volume", hostDir.resolve("log") + ":" + CONTAINER_HOME + "/log"));
        command.addAll(List.of("--volume", logConfig + ":" + CONTAINER_HOME + "/bench/" + LOG_CONFIG + ":ro"));
        command.add(options.image());

        log.info("Starting {} as container {} on CPUs {}", options.image(), name, options.cpus());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            docker(command);
            String mapping = docker(List.of("docker", "port", name, HTTP_PORT + "/tcp"))
                    .lines()
                    .findFirst()
                    .orElseThrow(() -> new IOException("No port mapping for " + name));
            port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1).trim());
            awaitReady();
        } catch (IOException | InterruptedException | RuntimeException e) {
            close();
            throw e;
        }
        log.info("ArcadeDB server {} ready on {}:{}", name, host(), port);
    }

    private void awaitReady() throws IOException, InterruptedException {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host() + ":" + port + "/api/v1/ready"))
                .timeout(Duration.ofSeconds(5))
                .build();
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 204) {
                    return;
                }
            } catch (IOException notYet) {
                // server still starting
            }
            if (!"true".equals(docker(List.of("docker", "inspect", "--format", "{{.State.Running}}", name))
                    .trim())) {
                throw new IOException("Container " + name + " exited: " + logsTail());
            }
            Thread.sleep(500);
        }
        throw new IOException("ArcadeDB in " + name + " not ready after " + READY_TIMEOUT + ": " + logsTail());
    }

    /**
     * Container and image details to record with results: image digest, cpuset, memory / CPU limits and the JVM
     * options the server was started with.
     */
    public Map<String, Object> describe() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("image", options.image());
        description.put("containerName", name);
        description.put("heap", options.heap());
        description.put("gc", SERVER_GC);
        try {
            JsonNode container = new ObjectMapper()
                    .readTree(docker(List.of("docker", "inspect", "--format", "{{json .}}", name)));
            JsonNode hostConfig = container.path("HostConfig");
            description.put("imageId", container.path("Image").asText());
            description.put("imageDigests", repoDigests());
            description.put("cpusetCpus", hostConfig.path("CpusetCpus").asText());
            description.put("memoryLimitBytes", hostConfig.path("Memory").asLong());
            description.put("nanoCpus", hostConfig.path("NanoCpus").asLong());
            description.put("cpuQuota", hostConfig.path("CpuQuota").asLong());
            Map<String, String> env = new LinkedHashMap<>();
            for (JsonNode entry : container.path("Config").path("Env")) {
                String value = entry.asText();
                if (value.startsWith("JAVA_OPTS=")
                        || value.startsWith("ARCADEDB_OPTS_MEMORY=")
                        || value.startsWith("ARCADEDB_SETTINGS=")) {
                    int eq = value.indexOf('=');
                    env.put(value.substring(0, eq), value.substring(eq + 1).replace(PASSWORD, "***"));
                }
            }
            description.put("env", env);
        } catch (IOException | InterruptedException | RuntimeException e) {
            description.put("inspectError", e.toString());
        }
        return description;
    }

    /** CPUs the container is actually pinned to, as reported by {@code docker inspect}. */
    public String inspectCpuset() throws IOException, InterruptedException {
        return docker(List.of("docker", "inspect", "--format", "{{.HostConfig.CpusetCpus}}", name))
                .trim();
    }

    /**
     * Peak memory of the container since it started (cgroup v2 {@code memory.peak}): server heap plus off-heap,
     * or -1 if not available.
     */
    public long peakMemoryBytes() {
        try {
            return Long.parseLong(docker(List.of("docker", "exec", name, "cat", "/sys/fs/cgroup/memory.peak"))
                    .trim());
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.warn("Cannot read peak memory of {}: {}", name, e.toString());
            return -1;
        }
    }

    /** Host directory with the server log and GC log. */
    public Path logDir() {
        return options.hostDir().resolve("log");
    }

    /** Whether any server log file ({@code arcadedb.log*}) contains {@code text}. */
    public boolean logContains(String text) throws IOException {
        Path dir = logDir();
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().startsWith("arcadedb.log")
                            && !f.getFileName().toString().endsWith(".lck"))
                    .toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Current CPU use of the container in percent of one core ({@code docker stats}, one sample of ~1-2 s). */
    public double cpuPercent() throws IOException, InterruptedException {
        String value = docker(List.of("docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}", name))
                .trim();
        return Double.parseDouble(value.replace("%", ""));
    }

    /** Stops the server cleanly (so its databases can be reopened) and removes the container. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            docker(List.of("docker", "stop", "--time", "60", name));
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.warn("Stopping {} failed: {}", name, e.toString());
        }
        remove();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException shuttingDown) {
            // the hook is running or has run
        }
    }

    private void remove() {
        try {
            docker(List.of("docker", "rm", "--force", "--volumes", name));
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.warn("Removing {} failed: {}", name, e.toString());
        }
    }

    private List<String> repoDigests() throws IOException, InterruptedException {
        JsonNode digests = new ObjectMapper()
                .readTree(docker(List.of("docker", "image", "inspect", "--format", "{{json .RepoDigests}}", options.image())));
        List<String> result = new ArrayList<>();
        digests.forEach(digest -> result.add(digest.asText()));
        return result;
    }

    private String logsTail() {
        try {
            return docker(List.of("docker", "logs", "--tail", "30", name));
        } catch (IOException | InterruptedException | RuntimeException e) {
            return "(no logs: " + e + ")";
        }
    }

    /**
     * The image's logging configuration with {@code com.arcadedb.index.vector} at WARNING (PRD F18).
     */
    static String logConfiguration() {
        return """
                handlers = java.util.logging.ConsoleHandler, java.util.logging.FileHandler
                .level = INFO
                com.arcadedb.level = INFO
                com.arcadedb.index.vector.level = WARNING
                org.apache.ratis.grpc.server.GrpcLogAppender.level = SEVERE
                java.util.logging.ConsoleHandler.level = INFO
                java.util.logging.ConsoleHandler.formatter = com.arcadedb.utility.AnsiLogFormatter
                java.util.logging.FileHandler.level = INFO
                java.util.logging.FileHandler.pattern = ${arcadedb.server.logsDirectory}/arcadedb.log
                java.util.logging.FileHandler.formatter = com.arcadedb.log.LogFormatter
                java.util.logging.FileHandler.limit = 100000000
                java.util.logging.FileHandler.count = 10
                """;
    }

    /** Runs a docker command and returns its standard output; fails with its error output on a non-zero exit. */
    static String docker(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        process.getOutputStream().close();
        // Outputs are small (ids, JSON, log tails); read stderr on a separate thread to avoid blocking.
        StringBuilder stderr = new StringBuilder();
        Thread errReader = Thread.ofVirtual().start(() -> {
            try {
                stderr.append(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // process ended
            }
        });
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(DOCKER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out: " + String.join(" ", command));
        }
        errReader.join();
        if (process.exitValue() != 0) {
            throw new IOException("'" + String.join(" ", command.subList(0, Math.min(3, command.size())))
                    + "' failed (exit " + process.exitValue() + "): " + (stderr + stdout).trim());
        }
        return stdout;
    }
}
