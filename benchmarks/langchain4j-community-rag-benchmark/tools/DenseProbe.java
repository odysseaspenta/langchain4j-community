// Standalone probe used for S4 and the remote findings of B11
// (benchmarks/results/2026-10-22-arcadedb-26.10.2-dense-recall-and-remote-overhead).
// Run with the benchmark module's classpath:
//   java --add-modules jdk.incubator.vector -cp "<classpath>" DenseProbe.java embedded <database dir> <queries> [variants]
//   java --add-modules jdk.incubator.vector -cp "<classpath>" DenseProbe.java remote <port> <queries> [variants]

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.RID;
import com.arcadedb.index.TypeIndex;
import com.arcadedb.index.vector.LSMVectorIndex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;

/** Dense ANN recall@10 and per-query time of the store's dense (and hybrid) queries, by candidate count and projection. */
public class DenseProbe {
    static final String D = "/sysnet/rag-bench/";
    static final String EMB = D + "embeddings/nq/seed-42/bge-small-en-v1.5/";
    static final String GT = D + "groundtruth/nq/seed-42/bge-small-en-v1.5/k100/smoke";
    static final String VEC_IDX = "Passage[embedding]", FT_IDX = "Passage[text]";

    public static void main(String[] a) throws Exception {
        java.util.logging.Logger.getLogger("com.arcadedb").setLevel(java.util.logging.Level.WARNING);
        String mode = a[0];
        int n = Integer.parseInt(a[2]);
        Set<String> only = a.length > 3 ? new HashSet<>(Arrays.asList(a[3].split(","))) : null;
        List<float[]> queries = loadQueries(n);
        List<String> queryText = loadQueryText(n);
        List<List<String>> truth = loadTruth(n);

        Map<String, BiFunction<Integer, Object, List<String>>> variants = new LinkedHashMap<>();
        if (mode.equals("embedded")) {
            DatabaseFactory factory = new DatabaseFactory(a[1]);
            Database db = factory.open();
            LSMVectorIndex index = (LSMVectorIndex) ((TypeIndex) db.getSchema().getIndexByName(VEC_IDX)).getIndexesOnBuckets()[0];
            for (int fetch : new int[] {100, 400}) {
                variants.put("api fetch " + fetch, (q, x) -> ids(db, index.findNeighborsFromVector(queries.get(q), fetch)));
            }
            for (int ef : new int[] {100, 200, 400, 800}) {
                variants.put("api fetch 100 ef " + ef, (q, x) -> ids(db, index.findNeighborsFromVector(queries.get(q), 100, ef)));
            }
            for (int fetch : new int[] {100, 400}) {
                variants.put("sql fetch " + fetch, (q, x) -> sql(db, "SELECT * FROM (SELECT expand(vector.neighbors('" + VEC_IDX + "', " + vec(queries.get(q)) + ", " + fetch + ")))", Map.of()));
            }
            run(variants, n, truth);
            db.close();
            factory.close();
        } else {
            RemoteDatabase db = new RemoteDatabase("localhost", Integer.parseInt(a[1]), "bench", "root", "rag-bench-local");
            for (int fetch : new int[] {100, 400}) {
                variants.put("store dense fetch " + fetch, (q, x) -> sql(db, "SELECT expand(`vector.neighbors`('" + VEC_IDX + "', " + vec(queries.get(q)) + ", " + fetch + "))", Map.of()));
                variants.put("doc_id only fetch " + fetch, (q, x) -> sql(db, "SELECT doc_id FROM (SELECT expand(`vector.neighbors`('" + VEC_IDX + "', " + vec(queries.get(q)) + ", " + fetch + ")))", Map.of()));
            }
            for (int ef : new int[] {100, 200, 400, 800}) {
                variants.put("store dense fetch 100 ef " + ef, (q, x) -> sql(db, "SELECT expand(`vector.neighbors`('" + VEC_IDX + "', " + vec(queries.get(q)) + ", 100, { efSearch: " + ef + " }))", Map.of()));
            }
            for (int limit : new int[] {100, 400}) {
                variants.put("store hybrid limit " + limit, (q, x) -> sql(db, hybrid(vec(queries.get(q)), limit, "*"), Map.of("query", queryText.get(q))));
                variants.put("hybrid doc_id only limit " + limit, (q, x) -> sql(db, hybrid(vec(queries.get(q)), limit, "doc_id"), Map.of("query", queryText.get(q))));
            }
            variants.put("fixed dense fetch 100 ef 800 no-dup", (q, x) -> sql(db, "SELECT *, !record FROM (SELECT expand(`vector.neighbors`('" + VEC_IDX + "', " + vec(queries.get(q)) + ", 100, { efSearch: 800 })))", Map.of()));
            variants.put("fixed hybrid limit 100 no-dup", (q, x) -> sql(db, "SELECT *, !record FROM (" + hybrid(vec(queries.get(q)), 100, "*") + ")", Map.of("query", queryText.get(q))));
            if (only != null) variants.keySet().removeIf(k -> only.stream().noneMatch(k::startsWith));
            run(variants, n, truth);
        }
    }

    static String hybrid(String vec, int limit, String projection) {
        String fuse = "SELECT expand(`vector.fuse`(`vector.neighbors`('" + VEC_IDX + "', " + vec + ", 400), "
                + "(SELECT @rid AS rid, $score AS score FROM `Passage` WHERE SEARCH_INDEX('" + FT_IDX + "', :query) = true ORDER BY $score DESC LIMIT 200), "
                + "{ fusion: 'RRF', k: 60, weights: [8, 1], limit: " + limit + " }))";
        return projection.equals("*") ? fuse : "SELECT " + projection + " FROM (" + fuse + ")";
    }

    static void run(Map<String, BiFunction<Integer, Object, List<String>>> variants, int n, List<List<String>> truth) {
        for (var v : variants.entrySet()) {
            for (int q = 0; q < Math.min(50, n); q++) v.getValue().apply(q, null); // warm-up
            long total = 0;
            double recall = 0;
            long rows = 0;
            long[] times = new long[n];
            for (int q = 0; q < n; q++) {
                long t = System.nanoTime();
                List<String> ids = v.getValue().apply(q, null);
                times[q] = System.nanoTime() - t;
                total += times[q];
                rows += ids.size();
                Set<String> top = new HashSet<>(truth.get(q).subList(0, 10));
                recall += ids.stream().limit(10).filter(top::contains).count() / 10.0;
            }
            Arrays.sort(times);
            System.out.printf(Locale.ROOT, "RESULT %-32s mean %7.2f ms  p50 %7.2f ms  rows/query %5d  ANN recall@10 %.4f%n",
                    v.getKey(), total / 1e6 / n, times[n / 2] / 1e6, rows / n, recall / n);
        }
    }

    static List<String> ids(Database db, List<com.arcadedb.utility.Pair<RID, Float>> neighbours) {
        List<String> out = new ArrayList<>();
        for (var p : neighbours) { var vx = p.getFirst().asVertex(); out.add(vx.has("doc_id") ? vx.getString("doc_id") : vx.getString("id")); }
        return out;
    }

    static List<String> sql(Object db, String query, Map<String, Object> params) {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = db instanceof RemoteDatabase r ? r.query("sql", query, params) : ((Database) db).query("sql", query, params)) {
            while (rs.hasNext()) {
                Result r = rs.next();
                Object e = r.getProperty("embedding"); // force the same parsing work as the store
                out.add(r.hasProperty("doc_id") ? r.getProperty("doc_id") : r.getProperty("id"));
            }
        }
        return out;
    }

    static String vec(float[] v) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) b.append(i == 0 ? "" : ",").append(v[i]);
        return b.append(']').toString();
    }

    static List<float[]> loadQueries(int n) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(Path.of(EMB + "queries.f32"))).order(ByteOrder.LITTLE_ENDIAN);
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float[] v = new float[384];
            for (int d = 0; d < 384; d++) v[d] = buf.getFloat((i * 384 + d) * 4);
            out.add(v);
        }
        return out;
    }

    static List<String> loadQueryText(int n) throws Exception {
        List<String> ids = Files.readAllLines(Path.of(EMB + "queries.ids")).stream().filter(s -> !s.isBlank()).toList();
        Map<String, String> text = new HashMap<>();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String line : Files.readAllLines(Path.of(D + "datasets/nq/raw/queries.jsonl"))) {
            var q = json.readTree(line);
            text.put(q.get("_id").asText(), q.get("text").asText());
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(escape(text.get(ids.get(i))));
        return out;
    }

    // As the store's default (S8): escape Lucene syntax.
    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ("+-!(){}[]^\"~*?:\\/&|".indexOf(c) >= 0) b.append('\\');
            b.append(c);
        }
        return b.toString();
    }

    static List<List<String>> loadTruth(int n) throws Exception {
        List<String> passageIds = Files.readAllLines(Path.of(EMB + "passages.ids")).stream().filter(s -> !s.isBlank()).toList();
        ByteBuffer rows = ByteBuffer.wrap(Files.readAllBytes(Path.of(GT + ".rows"))).order(ByteOrder.LITTLE_ENDIAN);
        List<List<String>> out = new ArrayList<>();
        for (int q = 0; q < n; q++) {
            List<String> ids = new ArrayList<>();
            for (int r = 0; r < 100; r++) {
                int row = rows.getInt((q * 100 + r) * 4);
                ids.add(row < 0 ? null : passageIds.get(row));
            }
            out.add(ids);
        }
        return out;
    }
}
