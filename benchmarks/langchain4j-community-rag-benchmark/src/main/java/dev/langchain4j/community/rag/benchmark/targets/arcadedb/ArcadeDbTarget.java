package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.exception.NeedRetryException;
import com.arcadedb.index.Index;
import com.arcadedb.index.IndexInternal;
import com.arcadedb.index.TypeIndex;
import com.arcadedb.index.vector.LSMVectorIndex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import com.arcadedb.schema.LSMVectorIndexMetadata;
import dev.langchain4j.community.rag.benchmark.targets.BenchmarkTarget;
import dev.langchain4j.community.rag.benchmark.targets.Document;
import dev.langchain4j.community.rag.benchmark.targets.DocumentSource;
import dev.langchain4j.community.rag.benchmark.targets.LoadOptions;
import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import dev.langchain4j.community.rag.benchmark.targets.SearchRequest;
import dev.langchain4j.community.rag.benchmark.targets.SearchResult;
import dev.langchain4j.community.rag.benchmark.util.CpuSet;
import dev.langchain4j.community.rag.benchmark.util.HeapPeak;
import dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LangChain4j ArcadeDB embedding store, embedded (ArcadeDB in this JVM) or remote (an ArcadeDB server in Docker,
 * PRD D7). Every load and query goes through the store's {@code EmbeddingStore} API; ArcadeDB is touched directly only
 * to force and time the vector graph build, set the remote query timeout and record index settings.
 */
public final class ArcadeDbTarget implements BenchmarkTarget {

    private static final Logger log = LoggerFactory.getLogger(ArcadeDbTarget.class);

    /**
     * ArcadeDB logs through java.util.logging, one logger per class. 26.7.2 logs an INFO line on every vector search
     * (PRD F18). ArcadeDB (re)configures java.util.logging when it first logs, which resets levels, so the level is
     * applied after the database is open; the strong reference keeps it from being garbage collected.
     */
    private static final java.util.logging.Logger VECTOR_INDEX_LOG =
            java.util.logging.Logger.getLogger("com.arcadedb.index.vector");

    /** Remote database name; one database per server directory. */
    static final String REMOTE_DATABASE = "bench";

    /** Client-side HTTP timeout for loading; generous because 26.7.2 may block inserts during graph rebuilds. */
    private static final Duration REMOTE_LOAD_TIMEOUT = Duration.ofMinutes(10);

    /** Client-side HTTP timeout for a probe query, which may wait for a full graph build. */
    private static final Duration REMOTE_PROBE_TIMEOUT = Duration.ofHours(6);

    /**
     * 26.7.2 rebuilds the graph after 15 s without mutations ({@code arcadedb.vectorIndex.inactivityRebuildTimeoutMs}).
     * The server counts as settled only after this long without inserts, so that rebuild cannot start mid-measurement.
     */
    static final Duration REMOTE_MIN_IDLE_AFTER_LOAD = Duration.ofSeconds(20);

    /** Server CPU below this (percent of one core) counts as idle. */
    static final double REMOTE_IDLE_CPU_PERCENT = 10;

    /** Consecutive idle CPU samples required. */
    static final int REMOTE_IDLE_SAMPLES = 3;

    /** Give up waiting for the server to settle after this long. */
    private static final Duration REMOTE_SETTLE_TIMEOUT = Duration.ofHours(2);

    /** Embedded: database directory. Remote: server directory ({@code databases/}, {@code log/}). */
    private final Path databaseDir;

    private final ArcadeDbSettings settings;
    /** {@code null} in embedded mode. */
    private final RemoteSettings remote;

    private Database database;
    private DockerServer server;
    private ArcadeDBEmbeddingStore store;
    private Map<String, Object> effectiveIndex = Map.of();
    private Map<String, Object> serverDescription = Map.of();
    private Map<String, Object> readiness = Map.of();
    private int vectorSubIndexes;

    private ArcadeDbTarget(Path databaseDir, ArcadeDbSettings settings, RemoteSettings remote) {
        this.databaseDir = databaseDir;
        this.settings = settings;
        this.remote = remote;
    }

    public static ArcadeDbTarget embedded(Path databaseDir, ArcadeDbSettings settings) {
        return new ArcadeDbTarget(databaseDir, settings, null);
    }

    /**
     * Remote mode: {@link #load} starts an ArcadeDB server container keeping its data in {@code serverDir};
     * {@link #close} stops it.
     */
    public static ArcadeDbTarget remote(Path serverDir, ArcadeDbSettings settings, RemoteSettings remote) {
        return new ArcadeDbTarget(serverDir, settings, remote);
    }

    @Override
    public String name() {
        return remote == null ? "arcadedb-embedded" : "arcadedb-remote";
    }

    @Override
    public LoadStats load(DocumentSource source, LoadOptions options) throws Exception {
        close();
        deleteRecursively(databaseDir);
        Files.createDirectories(databaseDir.getParent());
        if (remote == null) {
            openEmbedded();
        } else {
            openRemote();
        }

        HeapPeak.reset();
        long start = System.nanoTime();
        long deadline = options.timeCap() == null
                ? Long.MAX_VALUE
                : start + options.timeCap().toNanos();
        int loaded = 0;
        boolean capped = false;
        List<Document> batch;
        while (!(batch = source.next(options.batchSize())).isEmpty()) {
            List<String> ids = new ArrayList<>(batch.size());
            List<Embedding> embeddings = new ArrayList<>(batch.size());
            List<TextSegment> segments = new ArrayList<>(batch.size());
            for (Document document : batch) {
                ids.add(document.id());
                embeddings.add(Embedding.from(document.vector()));
                String text = remote == null ? document.text() : singleLine(document.text());
                segments.add(TextSegment.from(text, new Metadata(document.metadata())));
            }
            store.addAll(ids, embeddings, segments);
            loaded += batch.size();
            if (loaded % (options.batchSize() * 20) == 0) {
                log.info("Loaded {}/{} documents", loaded, source.size());
            }
            if (loaded < source.size() && System.nanoTime() > deadline) {
                capped = true;
                break;
            }
        }
        long loadEnd = System.nanoTime();
        double loadSeconds = (loadEnd - start) / 1e9;

        if (remote == null) {
            LSMVectorIndex index = buildVectorGraph();
            VECTOR_INDEX_LOG.setLevel(java.util.logging.Level.WARNING);
            effectiveIndex = publicFields(index.getMetadata());
        } else {
            awaitRemoteSearchable(loadEnd);
        }
        double timeToSearchable = (System.nanoTime() - loadEnd) / 1e9;
        long peakHeap = HeapPeak.peakBytes();
        long liveHeap = HeapPeak.liveBytes();
        long serverPeak = server == null ? -1 : server.peakMemoryBytes();
        long diskBytes = directorySize(
                remote == null ? databaseDir : server.databasesDir().resolve(REMOTE_DATABASE));
        if (remote != null) {
            limitRemoteQueryTime();
        }

        double rate = loaded / Math.max(loadSeconds, 1e-9);
        log.info(
                "Loaded {} of {} documents in {} s ({} /s{}), searchable after {} s more",
                loaded,
                source.size(),
                String.format("%.1f", loadSeconds),
                String.format("%.1f", rate),
                capped ? ", time cap reached" : "",
                String.format("%.1f", timeToSearchable));
        return new LoadStats(
                source.size(),
                loaded,
                capped,
                loadSeconds,
                rate,
                capped ? source.size() / rate : loadSeconds,
                timeToSearchable,
                diskBytes,
                peakHeap,
                liveHeap,
                serverPeak);
    }

    /**
     * The unmodified store's remote {@code addAll} inlines text into SQL string literals and escapes only quotes and
     * backslashes, so any line break is a SQL syntax error and every NQ passage ({@code title + "\n" + text}) fails.
     * Remote mode therefore stores line breaks as spaces. Embeddings are unaffected, and the full-text analyzer splits
     * on both alike, so retrieval is unchanged. Remove once the store binds text as a parameter (S11).
     */
    static String singleLine(String text) {
        return text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
    }

    private void openEmbedded() {
        try (DatabaseFactory factory = new DatabaseFactory(databaseDir.toString())) {
            database = factory.create();
        }
        VECTOR_INDEX_LOG.setLevel(java.util.logging.Level.WARNING);
        store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .database(database)
                .typeName(settings.typeName())
                .dimension(settings.dimension())
                .maxConnections(settings.maxConnections())
                .beamWidth(settings.beamWidth())
                .build();
    }

    private void openRemote() throws IOException, InterruptedException {
        server = DockerServer.start(
                new DockerServer.Options(remote.image(), remote.serverCpus(), remote.serverHeap(), databaseDir));
        serverDescription = server.describe();
        // The store creates its RemoteDatabase internally; its HTTP timeout comes from this JVM-wide setting. It must
        // outlast the server-side query timeout so that a slow query ends with the server's error, not the client's.
        long clientTimeout = Math.max(
                REMOTE_LOAD_TIMEOUT.toMillis(), remote.queryTimeout().toMillis() + 30_000);
        GlobalConfiguration.NETWORK_SOCKET_TIMEOUT.setValue(clientTimeout);
        store = ArcadeDBEmbeddingStore.builder()
                .host(server.host())
                .port(server.port())
                .databaseName(REMOTE_DATABASE)
                .username(DockerServer.USER)
                .password(DockerServer.PASSWORD)
                .createDatabase(true)
                .typeName(settings.typeName())
                .dimension(settings.dimension())
                .maxConnections(settings.maxConnections())
                .beamWidth(settings.beamWidth())
                .build();
    }

    /**
     * Remote time-to-searchable (F19). The server exposes neither the graph build state nor
     * {@code buildVectorGraphNow()}, and on 26.7.2 {@code REBUILD INDEX} only reloads the vectors without building the
     * graph (and discards the one built during loading). So the harness waits, from the end of loading, until
     * <ol>
     *   <li>a probe vector query succeeds: on 26.7.2 the first query blocks until the pending graph build is done;</li>
     *   <li>the server has settled: at least {@link #REMOTE_MIN_IDLE_AFTER_LOAD} after the last insert (past the
     *       inactivity rebuild) and {@link #REMOTE_IDLE_SAMPLES} consecutive CPU samples below
     *       {@link #REMOTE_IDLE_CPU_PERCENT}% of a core, so no background build overlaps measured queries.</li>
     * </ol>
     * The CPU criterion does not depend on ArcadeDB internals, so it also covers versions that build in the
     * background without blocking the first query (26.9.1). The probe is harness SQL, not via the store API.
     */
    private void awaitRemoteSearchable(long loadEnd) throws IOException, InterruptedException {
        try (RemoteDatabase admin = adminDatabase()) {
            admin.setTimeout((int) REMOTE_PROBE_TIMEOUT.toMillis());
            vectorSubIndexes = countRemoteVectorSubIndexes(admin);
            if (vectorSubIndexes != 1) {
                throw new IllegalStateException("Expected exactly one vector sub-index for " + settings.typeName()
                        + " but found " + vectorSubIndexes);
            }
            long deadline = System.nanoTime() + REMOTE_SETTLE_TIMEOUT.toNanos();
            double firstProbe = probe(admin, deadline);
            double firstProbeSeconds = (System.nanoTime() - loadEnd) / 1e9;
            log.info("First probe query answered {} s after loading (took {} s)",
                    String.format("%.1f", firstProbeSeconds), String.format("%.1f", firstProbe));

            long earliest = loadEnd + REMOTE_MIN_IDLE_AFTER_LOAD.toNanos();
            int idle = 0;
            List<Double> samples = new ArrayList<>();
            while (idle < REMOTE_IDLE_SAMPLES || System.nanoTime() < earliest) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("Server not idle " + REMOTE_SETTLE_TIMEOUT + " after loading;"
                            + " last CPU samples " + samples.subList(Math.max(0, samples.size() - 5), samples.size()));
                }
                double cpu = server.cpuPercent(); // docker stats takes about 1-2 s per sample
                samples.add(cpu);
                idle = cpu < REMOTE_IDLE_CPU_PERCENT ? idle + 1 : 0;
            }
            double settledSeconds = (System.nanoTime() - loadEnd) / 1e9;
            double finalProbe = probe(admin, deadline);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("firstProbeSeconds", firstProbeSeconds);
            details.put("firstProbeQuerySeconds", firstProbe);
            details.put("settledSeconds", settledSeconds);
            details.put("finalProbeQuerySeconds", finalProbe);
            details.put("cpuSamples", samples.size());
            readiness = details;
            log.info("Server settled {} s after loading ({} CPU samples); final probe {} s",
                    String.format("%.1f", settledSeconds), samples.size(), String.format("%.3f", finalProbe));
        }
    }

    /**
     * One {@code vector.neighbors} query with the vector inlined, retried until it succeeds or {@code deadline}. A
     * probe that waits for a graph build longer than 30 s loses its connection (the 26.7.2 client uses HTTP/2, which
     * the server closes after 30 s; see {@link RemoteSettings#MAX_QUERY_TIMEOUT}); the build carries on regardless, so
     * the probe is simply repeated. Returns the duration of the successful attempt.
     */
    private double probe(RemoteDatabase admin, long deadline) throws InterruptedException {
        float[] vector = new float[settings.dimension()];
        vector[0] = 1;
        String sql = "SELECT count(*) AS n FROM (SELECT expand(vector.neighbors('" + settings.typeName()
                + "[embedding]', " + Arrays.toString(vector) + ", 10)))";
        for (int attempt = 1; ; attempt++) {
            long start = System.nanoTime();
            try (ResultSet result = admin.query("sql", sql)) {
                long n = ((Number) result.next().getProperty("n")).longValue();
                if (n == 0) {
                    throw new IllegalStateException("Probe query returned no neighbours");
                }
                return (System.nanoTime() - start) / 1e9;
            } catch (RuntimeException e) {
                // Connection dropped at 30 s, or a background rebuild holds the index (NeedRetryException).
                if (System.nanoTime() > deadline) {
                    throw e;
                }
                log.info("Probe attempt {} failed after {} s ({}); retrying",
                        attempt, String.format("%.1f", (System.nanoTime() - start) / 1e9), e.getMessage());
                Thread.sleep(1000);
            }
        }
    }

    private int countRemoteVectorSubIndexes(RemoteDatabase admin) {
        int count = 0;
        try (ResultSet indexes = admin.query("sql", "SELECT FROM schema:indexes")) {
            while (indexes.hasNext()) {
                Result index = indexes.next();
                // The type-level index has no file of its own; each bucket's sub-index does.
                if ("LSM_VECTOR".equals(index.getProperty("indexType"))
                        && settings.typeName().equals(index.getProperty("typeName"))
                        && index.hasProperty("fileId")) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Sets the server-side query timeout once the index is searchable, so loading and the graph build are not limited
     * but a runaway query (the unmodified store runs one ANN search per scanned row remotely) is aborted on the server
     * instead of competing with the next queries for CPU.
     */
    private void limitRemoteQueryTime() {
        try (RemoteDatabase admin = adminDatabase()) {
            admin.command(
                            "sql",
                            "ALTER DATABASE `arcadedb.command.timeout` "
                                    + remote.queryTimeout().toMillis())
                    .close();
        }
    }

    private RemoteDatabase adminDatabase() {
        return new RemoteDatabase(
                server.host(), server.port(), REMOTE_DATABASE, DockerServer.USER, DockerServer.PASSWORD);
    }

    @Override
    public SearchResult search(SearchRequest request) {
        try {
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(Embedding.from(request.vector()))
                    .maxResults(request.k());
            if (request.mode() == SearchMode.HYBRID) {
                builder.query(request.text());
            }
            if (request.bucketsBelow() != null) {
                builder.filter(metadataKey("bucket").isLessThan(request.bucketsBelow()));
            }
            List<EmbeddingMatch<TextSegment>> matches =
                    store.search(builder.build()).matches();
            List<String> ids = new ArrayList<>(matches.size());
            List<Double> scores = new ArrayList<>(matches.size());
            for (EmbeddingMatch<TextSegment> match : matches) {
                ids.add(match.embeddingId());
                scores.add(match.score());
            }
            return SearchResult.of(ids, scores);
        } catch (RuntimeException e) {
            return SearchResult.failed(e);
        }
    }

    @Override
    public Map<String, Boolean> capabilities() {
        // The unmodified store (before S4) cannot pass a per-query efSearch.
        return Map.of("efSearch", false, "hybrid", true, "filter", true);
    }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("target", name());
        description.put("arcadedbVersion", ArcadeDbVersion.onClasspath());
        description.put("typeName", settings.typeName());
        description.put("dimension", settings.dimension());
        description.put("maxConnections", settings.maxConnections());
        description.put("beamWidth", settings.beamWidth());
        description.put("quantization", "NONE (not configurable before S6)");
        description.put("similarity", "COSINE (store default before S6)");
        description.put("vectorSubIndexes", vectorSubIndexes);
        if (remote == null) {
            description.put("graphBuild", "LSMVectorIndex.buildVectorGraphNow() after loading");
            description.put("effectiveIndex", effectiveIndex);
        } else {
            description.put(
                    "graphBuild",
                    "none forced; time to searchable = until a probe vector query succeeds and the server is idle"
                            + " (probe is harness SQL, not via store API)");
            description.put("readiness", readiness);
            description.put("effectiveIndex", "not observable over HTTP; created with the settings above");
            description.put(
                    "storedText",
                    "line breaks replaced by spaces (unmodified remote addAll cannot insert them; see S11)");
            description.put("queryTimeoutMillis", remote.queryTimeout().toMillis());
            description.put("queryTimeoutMechanism", "ALTER DATABASE `arcadedb.command.timeout` once searchable");
            CpuSet clientCpus = CpuSet.ofThisProcess();
            description.put("clientCpus", clientCpus.toString());
            description.put("serverCpus", remote.serverCpus().toString());
            description.put("server", serverDescription);
            List<String> warnings = new ArrayList<>();
            if (clientCpus.overlaps(remote.serverCpus())) {
                warnings.add("client CPUs " + clientCpus + " overlap server CPUs " + remote.serverCpus()
                        + " (pin the client with RAG_BENCH_CLIENT_CPUS)");
            }
            description.put("isolationWarnings", warnings);
        }
        description.put("versionDefaults", versionDefaults());
        return description;
    }

    @Override
    public void close() {
        if (store != null) {
            store.close(); // embedded: also closes the database
            store = null;
        }
        if (database != null && database.isOpen()) {
            database.close();
        }
        database = null;
        if (server != null) {
            server.close();
            server = null;
        }
    }

    /** The running server, or {@code null} (embedded mode, or not loaded). */
    DockerServer server() {
        return server;
    }

    /**
     * Forces a full, synchronous graph build (26.7.2: {@code LSMVectorIndex.buildVectorGraphNow()}), retrying while a
     * background rebuild holds the index. When it returns, no pending mutations remain.
     */
    private LSMVectorIndex buildVectorGraph() throws InterruptedException {
        List<LSMVectorIndex> indexes = vectorIndexes();
        vectorSubIndexes = indexes.size();
        if (indexes.size() != 1) {
            // The store searches only the first vector sub-index; more than one bucket would hide documents.
            throw new IllegalStateException("Expected exactly one vector sub-index for " + settings.typeName()
                    + " but found " + indexes.size());
        }
        LSMVectorIndex index = indexes.get(0);
        while (true) {
            try {
                index.buildVectorGraphNow();
                return index;
            } catch (NeedRetryException e) {
                Thread.sleep(200);
            }
        }
    }

    private List<LSMVectorIndex> vectorIndexes() {
        List<LSMVectorIndex> result = new ArrayList<>();
        for (Index index : database.getSchema().getType(settings.typeName()).getAllIndexes(true)) {
            if (index instanceof TypeIndex typeIndex) {
                for (IndexInternal subIndex : typeIndex.getSubIndexes()) {
                    if (subIndex instanceof LSMVectorIndex vectorIndex) {
                        result.add(vectorIndex);
                    }
                }
            } else if (index instanceof LSMVectorIndex vectorIndex) {
                result.add(vectorIndex);
            }
        }
        return result;
    }

    /** Index defaults of the ArcadeDB version on the classpath, read reflectively so any version works. */
    private static Map<String, Object> versionDefaults() {
        try {
            return publicFields(new LSMVectorIndexMetadata("T", new String[] {"embedding"}, 0));
        } catch (RuntimeException e) {
            return Map.of("error", e.toString());
        }
    }

    private static Map<String, Object> publicFields(Object object) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Field field : object.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                Object value = field.get(object);
                fields.put(field.getName(), value == null ? null : String.valueOf(value));
            } catch (IllegalAccessException e) {
                fields.put(field.getName(), "inaccessible");
            }
        }
        return fields;
    }

    private static long directorySize(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(file -> file.toFile().length())
                    .sum();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
