// Standalone probe used for S9 (benchmarks/results/2026-10-23-arcadedb-26.10.2-filtered-search-investigation).
// Run with the benchmark module's classpath:
//   java --add-modules jdk.incubator.vector -cp "<classpath>" FilterProbe.java embedded <database dir> <queries> [variants]
//   java --add-modules jdk.incubator.vector -cp "<classpath>" FilterProbe.java remote <port> <queries> [variants]

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

/** Filtered dense search: the store's post-filter against allow-list pre-filters and exact search over the subset. */
public class FilterProbe {
    static final String D = "/sysnet/rag-bench/";
    static final String EMB = D + "embeddings/nq/seed-42/bge-small-en-v1.5/";
    static final String GT = D + "groundtruth/nq/seed-42/bge-small-en-v1.5/k100/smoke-bucket-lt-";
    static final String VEC_IDX = "Passage[embedding]";
    static final int K = 100;

    interface Search { List<String> run(float[] query, int bucketsBelow); }

    public static void main(String[] a) throws Exception {
        java.util.logging.Logger.getLogger("com.arcadedb").setLevel(java.util.logging.Level.SEVERE);
        int n = Integer.parseInt(a[2]);
        Set<String> only = a.length > 3 ? new HashSet<>(Arrays.asList(a[3].split(","))) : null;
        List<float[]> queries = loadQueries(n);
        Map<String, Search> variants = new LinkedHashMap<>();
        if (a[0].equals("embedded")) {
            DatabaseFactory factory = new DatabaseFactory(a[1]);
            Database db = factory.open();
            LSMVectorIndex index = (LSMVectorIndex) ((TypeIndex) db.getSchema().getIndexByName(VEC_IDX)).getIndexesOnBuckets()[0];
            String where = "`bucket` < ?";
            variants.put("post-filter fetch 400 (store)", (q, b) -> {
                List<String> out = new ArrayList<>();
                for (var p : index.findNeighborsFromVector(q, 400)) {
                    var v = p.getFirst().asVertex();
                    if (((Number) v.get("bucket")).intValue() < b) out.add(v.getString("id"));
                    if (out.size() == K) break;
                }
                return out;
            });
            variants.put("allow-list (scan) + ANN", (q, b) -> ids(db, index.findNeighborsFromVector(q, K, allowList(db, where, b))));
            variants.put("allow-list (scan) + ANN fetch 400", (q, b) -> ids(db, index.findNeighborsFromVector(q, 400, allowList(db, where, b))).subList(0, K));
            variants.put("allow-list (scan) only", (q, b) -> { allowList(db, where, b); return List.of(); });
            variants.put("exact over subset (scan)", (q, b) -> exact(db, "SELECT @rid AS rid, id, embedding FROM Passage WHERE " + where, b, q, "id"));
            variants.put("sql neighbors filter subquery", (q, b) -> sql(db, "SELECT id FROM (SELECT expand(vector.neighbors('" + VEC_IDX + "', " + vec(q) + ", " + K + ", { filter: (SELECT @rid FROM Passage WHERE " + where.replace("?", String.valueOf(b)) + ") })))"));
        } else {
            RemoteDatabase db = new RemoteDatabase("localhost", Integer.parseInt(a[1]), "bench", "root", "rag-bench-local");
            String where = "meta_bucket < %d";
            variants.put("post-filter fetch 500 (store)", (q, b) -> {
                List<String> out = new ArrayList<>();
                try (ResultSet rs = db.query("sql", "SELECT *, !record FROM (SELECT expand(`vector.neighbors`('" + VEC_IDX + "', " + vec(q) + ", 500, { efSearch: 1600 })))")) {
                    while (rs.hasNext() && out.size() < K) {
                        Result r = rs.next();
                        r.getProperty("embedding");
                        if (((Number) r.getProperty("meta_bucket")).intValue() < b) out.add(r.getProperty("doc_id"));
                    }
                }
                return out;
            });
            variants.put("neighbors filter subquery", (q, b) -> sqlFull(db, "SELECT *, !record FROM (SELECT expand(`vector.neighbors`('" + VEC_IDX + "', " + vec(q) + ", " + K + ", { efSearch: 800, filter: (SELECT @rid FROM Passage WHERE " + String.format(where, b) + ") })))"));
            variants.put("neighbors filter subquery, ids only", (q, b) -> sqlIds(db, "SELECT doc_id FROM (SELECT expand(`vector.neighbors`('" + VEC_IDX + "', " + vec(q) + ", " + K + ", { efSearch: 800, filter: (SELECT @rid FROM Passage WHERE " + String.format(where, b) + ") })))"));
            variants.put("allow-list subquery only", (q, b) -> { sqlIds(db, "SELECT count(*) AS doc_id FROM Passage WHERE " + String.format(where, b)); return List.of(); });
        }
        if (only != null) variants.keySet().removeIf(k -> only.stream().noneMatch(k::startsWith));
        for (int bucketsBelow : new int[] {1, 10, 50}) {
            List<List<String>> truth = loadTruth("" + bucketsBelow, n);
            for (var v : variants.entrySet()) run(v.getKey(), v.getValue(), queries, truth, bucketsBelow);
        }
        System.exit(0);
    }

    static void run(String name, Search s, List<float[]> queries, List<List<String>> truth, int b) {
        int n = queries.size();
        for (int q = 0; q < Math.min(20, n); q++) s.run(queries.get(q), b); // warm-up
        long[] t = new long[n];
        double r10 = 0, r100 = 0;
        int short_ = 0;
        for (int q = 0; q < n; q++) {
            long start = System.nanoTime();
            List<String> ids = s.run(queries.get(q), b);
            t[q] = System.nanoTime() - start;
            if (ids.size() < K) short_++;
            r10 += recall(ids, truth.get(q), 10);
            r100 += recall(ids, truth.get(q), K);
        }
        long total = Arrays.stream(t).sum();
        Arrays.sort(t);
        System.out.printf(Locale.ROOT, "RESULT bucket<%-2d %-38s mean %8.2f ms  p50 %8.2f ms  recall@10 %.4f  recall@100 %.4f  short %5.1f%%%n",
                b, name, total / 1e6 / n, t[n / 2] / 1e6, r10 / n, r100 / n, 100.0 * short_ / n);
    }

    static double recall(List<String> ids, List<String> truth, int k) {
        Set<String> top = new HashSet<>(truth.subList(0, k));
        top.remove(null);
        return top.isEmpty() ? 1 : ids.stream().limit(k).filter(top::contains).count() / (double) top.size();
    }

    static Set<RID> allowList(Database db, String where, int b) {
        Set<RID> out = new HashSet<>();
        try (ResultSet rs = db.query("sql", "SELECT @rid AS rid FROM Passage WHERE " + where, b)) {
            while (rs.hasNext()) out.add(((com.arcadedb.database.Identifiable) rs.next().getProperty("rid")).getIdentity());
        }
        return out;
    }

    static List<String> exact(Database db, String sql, int b, float[] q, String idProp) {
        PriorityQueue<Object[]> heap = new PriorityQueue<>(Comparator.comparingDouble(o -> (double) o[1]));
        try (ResultSet rs = db.query("sql", sql, b)) {
            while (rs.hasNext()) {
                Result r = rs.next();
                float[] e = r.getProperty("embedding");
                double dot = 0;
                for (int i = 0; i < e.length; i++) dot += e[i] * q[i];
                heap.add(new Object[] {r.getProperty(idProp), dot});
                if (heap.size() > K) heap.poll();
            }
        }
        List<Object[]> l = new ArrayList<>(heap);
        l.sort(Comparator.comparingDouble(o -> -(double) o[1]));
        return l.stream().map(o -> (String) o[0]).toList();
    }

    static List<String> ids(Database db, List<com.arcadedb.utility.Pair<RID, Float>> neighbours) {
        List<String> out = new ArrayList<>();
        for (var p : neighbours) out.add(p.getFirst().asVertex().getString("id"));
        return out;
    }

    static List<String> sql(Database db, String query) {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = db.query("sql", query)) { while (rs.hasNext()) out.add(rs.next().getProperty("id")); }
        return out;
    }

    static List<String> sqlFull(RemoteDatabase db, String query) {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = db.query("sql", query)) { while (rs.hasNext()) { Result r = rs.next(); r.getProperty("embedding"); out.add(r.getProperty("doc_id")); } }
        return out;
    }

    static List<String> sqlIds(RemoteDatabase db, String query) {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = db.query("sql", query)) { while (rs.hasNext()) out.add(String.valueOf((Object) rs.next().getProperty("doc_id"))); }
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

    static List<List<String>> loadTruth(String bucket, int n) throws Exception {
        List<String> passageIds = Files.readAllLines(Path.of(EMB + "passages.ids")).stream().filter(s -> !s.isBlank()).toList();
        ByteBuffer rows = ByteBuffer.wrap(Files.readAllBytes(Path.of(GT + bucket + ".rows"))).order(ByteOrder.LITTLE_ENDIAN);
        List<List<String>> out = new ArrayList<>();
        for (int q = 0; q < n; q++) {
            List<String> ids = new ArrayList<>();
            for (int r = 0; r < K; r++) {
                int row = rows.getInt((q * K + r) * 4);
                ids.add(row < 0 ? null : passageIds.get(row));
            }
            out.add(ids);
        }
        return out;
    }
}
