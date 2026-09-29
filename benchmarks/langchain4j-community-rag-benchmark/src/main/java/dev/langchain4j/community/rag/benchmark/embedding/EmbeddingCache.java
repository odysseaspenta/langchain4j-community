package dev.langchain4j.community.rag.benchmark.embedding;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.langchain4j.community.rag.benchmark.dataset.BeirDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Passage;
import dev.langchain4j.community.rag.benchmark.dataset.PassageReader;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Qrels;
import dev.langchain4j.community.rag.benchmark.dataset.Query;
import dev.langchain4j.community.rag.benchmark.metrics.LatencyStats;
import dev.langchain4j.community.rag.benchmark.util.Checksums;
import dev.langchain4j.community.rag.benchmark.util.Json;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embeddings of corpus passages and test queries, computed once and reused by every run (PRD F7–F9).
 *
 * <p>Layout in {@code embeddings/<dataset>/seed-<seed>/<model>/}: {@code passages.f32} (little-endian float32
 * rows), {@code passages.ids} (one id per line, same order), {@code passages.json} ({@link CacheState}), and the
 * same three files for {@code queries}.
 *
 * <p>Passage rows follow the tier priority order, so the first {@code n} rows are exactly the {@code n}-passage
 * tier and a larger tier only appends rows. Work is committed in chunks; an interrupted run resumes after the last
 * committed chunk, and its result is byte-identical to an uninterrupted run.
 */
public class EmbeddingCache {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingCache.class);

    static final int CHUNK_SIZE = 256;
    private static final double NORM_TOLERANCE = 1e-3;

    private final Path dir;
    private final PreparedDataset dataset;
    private final EmbeddingModelSpec spec;
    private String modelSha256;

    public EmbeddingCache(Path dataDir, PreparedDataset dataset, EmbeddingModelSpec spec) {
        this.dir = dataDir.resolve("embeddings")
                .resolve(dataset.manifest().name())
                .resolve("seed-" + dataset.tiers().seed())
                .resolve(spec.id());
        this.dataset = dataset;
        this.spec = spec;
    }

    public Path dir() {
        return dir;
    }

    /**
     * Ensures the first {@code targetRows} passages in priority order are embedded.
     */
    public CacheState embedPassages(int targetRows, int threads) throws IOException {
        List<String> order = dataset.priorityOrder();
        if (targetRows > order.size()) {
            throw new IllegalArgumentException(targetRows + " rows requested, corpus has " + order.size());
        }
        CacheState state = resume(
                "passages",
                identity(spec.passagePrefix(), dataset.tiers().priority().sha256()));
        if (state.rows() >= targetRows && state.vectorsSha256() != null) {
            log.info("Passage embeddings already cover {} rows", state.rows());
            return state;
        }

        long start = System.nanoTime();
        int startRows = state.rows();
        if (startRows < targetRows) {
            log.info("Embedding passages {}..{} with {} threads", startRows, targetRows, threads);
            ExecutorService executor = Executors.newFixedThreadPool(threads, daemonThreads());
            try (PassageReader reader = PassageReader.open(
                            dataset.dataset().dir().resolve(BeirDataset.CORPUS), order.subList(startRows, targetRows));
                    FileChannel vectors = append("passages.f32");
                    FileChannel ids = append("passages.ids")) {
                EmbeddingModel model = spec.factory().create(executor, threads);
                for (int from = 0; from < reader.size(); from += CHUNK_SIZE) {
                    int to = Math.min(from + CHUNK_SIZE, reader.size());
                    List<String> chunkIds = new ArrayList<>(to - from);
                    List<TextSegment> segments = new ArrayList<>(to - from);
                    for (int i = from; i < to; i++) {
                        Passage passage = reader.read(i);
                        chunkIds.add(passage.id());
                        segments.add(TextSegment.from(spec.passagePrefix() + passage.content()));
                    }
                    List<Embedding> embeddings = model.embedAll(segments).content();
                    long idsBytes = write(vectors, ids, chunkIds, embeddings);
                    state = state.committed(startRows + to, state.idsBytes() + idsBytes);
                    Json.write(stateFile("passages"), state);
                    logProgress(startRows, state.rows(), targetRows, start);
                }
            } finally {
                executor.shutdownNow();
            }
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        Double throughput = state.rows() > startRows ? (state.rows() - startRows) / seconds : state.throughput();
        state = state.completed(
                sha256Prefix(file("passages.f32"), (long) state.rows() * spec.dimension() * Float.BYTES),
                sha256Prefix(file("passages.ids"), state.idsBytes()),
                threads,
                throughput,
                null);
        Json.write(stateFile("passages"), state);
        log.info("Passage embeddings: {} rows, {} passages/s", state.rows(), String.format("%.1f", throughput));
        return state;
    }

    /**
     * Ensures all test queries are embedded, one at a time on one thread, recording per-query latency (PRD M9).
     */
    public CacheState embedQueries() throws IOException {
        Qrels qrels = dataset.dataset().testQrels();
        List<Query> queries = dataset.dataset().testQueries(qrels);
        String orderSha256 = Checksums.sha256(
                String.join("\n", queries.stream().map(Query::id).toList()).getBytes(UTF_8));
        CacheState state = resume("queries", identity(spec.queryPrefix(), orderSha256));
        if (state.rows() == queries.size() && state.vectorsSha256() != null) {
            log.info("Query embeddings already cover {} queries", state.rows());
            return state;
        }
        state = restart("queries", state);

        log.info("Embedding {} queries one at a time", queries.size());
        ExecutorService executor = Executors.newSingleThreadExecutor(daemonThreads());
        long[] nanos = new long[queries.size()];
        long start = System.nanoTime();
        try (FileChannel vectors = append("queries.f32");
                FileChannel ids = append("queries.ids")) {
            EmbeddingModel model = spec.factory().create(executor, 1);
            List<Embedding> embeddings = new ArrayList<>(queries.size());
            for (int i = 0; i < queries.size(); i++) {
                long t0 = System.nanoTime();
                embeddings.add(
                        model.embed(spec.queryPrefix() + queries.get(i).text()).content());
                nanos[i] = System.nanoTime() - t0;
            }
            long idsBytes = write(vectors, ids, queries.stream().map(Query::id).toList(), embeddings);
            state = state.committed(queries.size(), idsBytes);
        } finally {
            executor.shutdownNow();
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        state = state.completed(
                sha256Prefix(file("queries.f32"), (long) state.rows() * spec.dimension() * Float.BYTES),
                sha256Prefix(file("queries.ids"), state.idsBytes()),
                1,
                queries.size() / seconds,
                LatencyStats.ofNanos(nanos));
        Json.write(stateFile("queries"), state);
        log.info(
                "Query embeddings: {} queries, p50 {} ms",
                state.rows(),
                String.format("%.2f", state.latency().p50()));
        return state;
    }

    /**
     * Re-embeds {@code samples} randomly chosen cached passages and compares them with the cache.
     */
    public VerifyResult verifyPassages(int samples, long seed) throws IOException {
        CacheState state = readState("passages");
        List<String> ids = passageIds();
        Random random = new Random(seed);
        Set<Integer> distinct = new LinkedHashSet<>();
        while (distinct.size() < Math.min(samples, state.rows())) {
            distinct.add(random.nextInt(state.rows()));
        }
        List<Integer> rows = new ArrayList<>(distinct);
        ExecutorService executor = Executors.newSingleThreadExecutor(daemonThreads());
        try (VectorMatrix matrix = passages();
                PassageReader reader = PassageReader.open(
                        dataset.dataset().dir().resolve(BeirDataset.CORPUS),
                        rows.stream().map(ids::get).toList())) {
            EmbeddingModel model = spec.factory().create(executor, 1);
            int mismatches = 0;
            double maxAbsDiff = 0;
            for (int i = 0; i < rows.size(); i++) {
                float[] fresh = model.embed(
                                spec.passagePrefix() + reader.read(i).content())
                        .content()
                        .vector();
                float[] cached = matrix.get(rows.get(i));
                if (!Arrays.equals(fresh, cached)) {
                    mismatches++;
                }
                for (int d = 0; d < fresh.length; d++) {
                    maxAbsDiff = Math.max(maxAbsDiff, Math.abs(fresh[d] - cached[d]));
                }
            }
            return new VerifyResult(rows.size(), mismatches, maxAbsDiff);
        } finally {
            executor.shutdownNow();
        }
    }

    public VectorMatrix passages() throws IOException {
        return VectorMatrix.open(
                file("passages.f32"), spec.dimension(), readState("passages").rows());
    }

    public List<String> passageIds() throws IOException {
        return readIds("passages");
    }

    public VectorMatrix queries() throws IOException {
        return VectorMatrix.open(
                file("queries.f32"), spec.dimension(), readState("queries").rows());
    }

    public List<String> queryIds() throws IOException {
        return readIds("queries");
    }

    public CacheState readState(String name) throws IOException {
        Path file = stateFile(name);
        if (!Files.exists(file)) {
            throw new IOException("No " + name + " embeddings in " + dir + "; run 'rag-bench embed' first");
        }
        return Json.read(file, CacheState.class);
    }

    public record VerifyResult(int samples, int mismatches, double maxAbsDiff) {}

    private List<String> readIds(String name) throws IOException {
        CacheState state = readState(name);
        List<String> ids = Files.readAllLines(file(name + ".ids"), UTF_8);
        return ids.subList(0, state.rows());
    }

    private CacheState identity(String prefix, String orderSha256) throws IOException {
        return new CacheState(
                CacheState.FORMAT_VERSION,
                spec.id(),
                modelSha256(),
                spec.dimension(),
                true,
                prefix,
                dataset.manifestSha256(),
                dataset.tiers().seed(),
                orderSha256,
                0,
                0,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Loads the committed state and discards any uncommitted bytes, or starts empty.
     */
    private CacheState resume(String name, CacheState identity) throws IOException {
        Files.createDirectories(dir);
        Path stateFile = stateFile(name);
        if (!Files.exists(stateFile)) {
            return restart(name, identity);
        }
        CacheState state = Json.read(stateFile, CacheState.class);
        if (!state.sameIdentity(identity)) {
            throw new IOException("The " + name + " cache in " + dir + " was built with a different model, prefix,"
                    + " dataset or order; delete " + dir + " to rebuild it");
        }
        truncate(file(name + ".f32"), (long) state.rows() * spec.dimension() * Float.BYTES);
        truncate(file(name + ".ids"), state.idsBytes());
        return state;
    }

    private CacheState restart(String name, CacheState identity) throws IOException {
        Files.deleteIfExists(file(name + ".f32"));
        Files.deleteIfExists(file(name + ".ids"));
        CacheState empty = identity.committed(0, 0);
        Json.write(stateFile(name), empty);
        return empty;
    }

    private long write(FileChannel vectors, FileChannel ids, List<String> chunkIds, List<Embedding> embeddings)
            throws IOException {
        if (embeddings.size() != chunkIds.size()) {
            throw new IOException("Model returned " + embeddings.size() + " embeddings for " + chunkIds.size());
        }
        ByteBuffer buffer = ByteBuffer.allocate(embeddings.size() * spec.dimension() * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < embeddings.size(); i++) {
            float[] vector = embeddings.get(i).vector();
            if (vector.length != spec.dimension()) {
                throw new IOException("Expected dimension " + spec.dimension() + " but got " + vector.length);
            }
            double norm = 0;
            for (float v : vector) {
                norm += v * v;
                buffer.putFloat(v);
            }
            if (Math.abs(Math.sqrt(norm) - 1) > NORM_TOLERANCE) {
                throw new IOException(
                        "Embedding of " + chunkIds.get(i) + " is not normalised (norm " + Math.sqrt(norm) + ")");
            }
        }
        writeFully(vectors, buffer.flip());
        byte[] idBytes = (String.join("\n", chunkIds) + "\n").getBytes(UTF_8);
        writeFully(ids, ByteBuffer.wrap(idBytes));
        vectors.force(false);
        ids.force(false);
        return idBytes.length;
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private FileChannel append(String fileName) throws IOException {
        return FileChannel.open(
                file(fileName), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    private static void truncate(Path file, long size) throws IOException {
        long actual = Files.exists(file) ? Files.size(file) : 0;
        if (actual < size) {
            throw new IOException(file + " is shorter (" + actual + " bytes) than its committed size " + size);
        }
        if (actual > size) {
            log.info("Discarding {} uncommitted bytes of {}", actual - size, file.getFileName());
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.truncate(size);
            }
        }
    }

    private static String sha256Prefix(Path file, long bytes) throws IOException {
        MessageDigest digest = Checksums.newSha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            long remaining = bytes;
            int read;
            while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                digest.update(buffer, 0, read);
                remaining -= read;
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String modelSha256() throws IOException {
        if (modelSha256 == null) {
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(spec.resource())) {
                if (in == null) {
                    throw new IOException("Model resource " + spec.resource() + " not on the classpath");
                }
                MessageDigest digest = Checksums.newSha256();
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                modelSha256 = HexFormat.of().formatHex(digest.digest());
            }
        }
        return modelSha256;
    }

    private Path file(String name) {
        return dir.resolve(name);
    }

    private Path stateFile(String name) {
        return dir.resolve(name + ".json");
    }

    private static void logProgress(int startRows, int rows, int targetRows, long startNanos) {
        int done = rows - startRows;
        if (done % (CHUNK_SIZE * 40) != 0 && rows != targetRows) {
            return;
        }
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        double rate = done / seconds;
        long etaSeconds = Math.round((targetRows - rows) / rate);
        log.info(
                "Embedded {}/{} passages, {} passages/s, ETA {} min",
                rows,
                targetRows,
                String.format("%.1f", rate),
                etaSeconds / 60);
    }

    private static ThreadFactory daemonThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable, "embedding");
            thread.setDaemon(true);
            return thread;
        };
    }
}
