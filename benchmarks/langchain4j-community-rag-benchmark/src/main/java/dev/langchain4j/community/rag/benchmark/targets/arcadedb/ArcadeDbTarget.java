package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.exception.NeedRetryException;
import com.arcadedb.index.Index;
import com.arcadedb.index.IndexInternal;
import com.arcadedb.index.TypeIndex;
import com.arcadedb.index.vector.LSMVectorIndex;
import com.arcadedb.schema.LSMVectorIndexMetadata;
import dev.langchain4j.community.rag.benchmark.targets.BenchmarkTarget;
import dev.langchain4j.community.rag.benchmark.targets.Document;
import dev.langchain4j.community.rag.benchmark.targets.DocumentSource;
import dev.langchain4j.community.rag.benchmark.targets.LoadOptions;
import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import dev.langchain4j.community.rag.benchmark.targets.SearchMode;
import dev.langchain4j.community.rag.benchmark.targets.SearchRequest;
import dev.langchain4j.community.rag.benchmark.targets.SearchResult;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LangChain4j ArcadeDB embedding store in embedded mode (ArcadeDB in this JVM). Every load and query goes
 * through the store's {@code EmbeddingStore} API; ArcadeDB internals are touched only to force and time the vector
 * graph build and to record index settings.
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

    private final Path databaseDir;
    private final ArcadeDbSettings settings;
    private Database database;
    private ArcadeDBEmbeddingStore store;
    private Map<String, Object> effectiveIndex = Map.of();
    private int vectorSubIndexes;

    private ArcadeDbTarget(Path databaseDir, ArcadeDbSettings settings) {
        this.databaseDir = databaseDir;
        this.settings = settings;
    }

    public static ArcadeDbTarget embedded(Path databaseDir, ArcadeDbSettings settings) {
        return new ArcadeDbTarget(databaseDir, settings);
    }

    @Override
    public String name() {
        return "arcadedb-embedded";
    }

    @Override
    public LoadStats load(DocumentSource source, LoadOptions options) throws Exception {
        close();
        deleteRecursively(databaseDir);
        Files.createDirectories(databaseDir.getParent());
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
                segments.add(TextSegment.from(document.text(), new Metadata(document.metadata())));
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
        double loadSeconds = (System.nanoTime() - start) / 1e9;

        long buildStart = System.nanoTime();
        LSMVectorIndex index = buildVectorGraph();
        VECTOR_INDEX_LOG.setLevel(java.util.logging.Level.WARNING);
        double timeToSearchable = (System.nanoTime() - buildStart) / 1e9;
        effectiveIndex = publicFields(index.getMetadata());
        long peakHeap = HeapPeak.peakBytes();

        double rate = loaded / Math.max(loadSeconds, 1e-9);
        log.info(
                "Loaded {} of {} documents in {} s ({} /s{}), graph built in {} s",
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
                directorySize(databaseDir),
                peakHeap);
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
        description.put("similarity", "COSINE (fixed in embedded mode before S6)");
        description.put("vectorSubIndexes", vectorSubIndexes);
        description.put("effectiveIndex", effectiveIndex);
        description.put("versionDefaults", versionDefaults());
        return description;
    }

    @Override
    public void close() {
        if (store != null) {
            store.close(); // also closes the database
            store = null;
        }
        if (database != null && database.isOpen()) {
            database.close();
        }
        database = null;
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
