// Standalone probe used for the hybrid investigation (benchmarks/results/2026-10-17-arcadedb-26.10.2-hybrid-investigation).
// Run with the ArcadeDB engine classpath of the version under test:
//   java --add-modules jdk.incubator.vector -cp "<classpath>" HybridProbe.java <database dir> <queries> [variant prefixes]

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.Identifiable;
import com.arcadedb.database.RID;
import com.arcadedb.graph.Vertex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Decomposes the store's hybrid query on a kept benchmark database (embedded). */
public class HybridProbe {
    static final String D = "/sysnet/rag-bench/";
    static final String EMB = D + "embeddings/nq/seed-42/bge-small-en-v1.5/";
    static final String VEC_IDX = "Passage[embedding]", FT_IDX = "Passage[text]";
    static final String NOT_DELETED = "(deleted IS NULL OR deleted != true)";

    record Q(String id, String text, float[] vec) {}
    record Outcome(List<String> ids, long rows) {}

    public static void main(String[] a) throws Exception {
        String dbPath = a[0];
        int n = Integer.parseInt(a[1]);
        Set<String> only = a.length > 2 ? new HashSet<>(Arrays.asList(a[2].split(","))) : null;
        java.util.logging.Logger.getLogger("com.arcadedb").setLevel(java.util.logging.Level.WARNING);
        List<Q> queries = loadQueries(n);
        Map<String, Set<String>> qrels = loadQrels();
        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("A store hybrid (as is)",
                "SELECT expand(vector.fuse(vector.neighbors('" + VEC_IDX + "', %VEC%, 200), (SELECT @rid AS rid FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true AND " + NOT_DELETED + "), { fusion: 'RRF', limit: 200 }))");
        variants.put("B dense only (k=100)", "SELECT expand(vector.neighbors('" + VEC_IDX + "', %VEC%, 100))");
        variants.put("C full-text count", "SELECT count(*) AS n FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true");
        variants.put("D full-text count + deleted filter", "SELECT count(*) AS n FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true AND " + NOT_DELETED);
        variants.put("E full-text top-200 by $score", "SELECT @rid AS rid, $score AS score FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true ORDER BY $score DESC LIMIT 200");
        variants.put("F full-text only top-100 (lexical)", "SELECT id, $score AS score FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true ORDER BY $score DESC LIMIT 100");
        variants.put("G fuse with bounded full-text (200 by $score)",
                "SELECT expand(vector.fuse(vector.neighbors('" + VEC_IDX + "', %VEC%, 200), (SELECT @rid AS rid FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true ORDER BY $score DESC LIMIT 200), { fusion: 'RRF', limit: 200 }))");
        variants.put("H fuse with full-text, no deleted filter (unbounded)",
                "SELECT expand(vector.fuse(vector.neighbors('" + VEC_IDX + "', %VEC%, 200), (SELECT @rid AS rid FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true), { fusion: 'RRF', limit: 200 }))");

        String ftTop = "(SELECT @rid AS rid FROM (SELECT @rid, deleted FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true ORDER BY $score DESC LIMIT %N%) WHERE " + NOT_DELETED + ")";
        String dense = "vector.neighbors('" + VEC_IDX + "', %VEC%, 200)";
        for (String top : new String[] {"50", "100", "200", "500"}) {
            variants.put("I" + top + " fuse RRF, full-text top-" + top + " then deleted filter",
                    "SELECT expand(vector.fuse(" + dense + ", " + ftTop.replace("%N%", top) + ", { fusion: 'RRF', limit: 200 }))");
        }
        for (String w : new String[] {"[2, 1]", "[3, 1]", "[5, 1]", "[8, 1]", "[10, 1]"}) {
            variants.put("J" + w + " fuse RRF weights " + w + ", full-text top-200",
                    "SELECT expand(vector.fuse(" + dense + ", " + ftTop.replace("%N%", "200") + ", { fusion: 'RRF', weights: " + w + ", limit: 200 }))");
        }
        String ftScored = "(SELECT @rid AS rid, score FROM (SELECT @rid, deleted, $score AS score FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true ORDER BY $score DESC LIMIT 200) WHERE " + NOT_DELETED + ")";
        variants.put("K fuse DBSF, full-text top-200 with score",
                "SELECT expand(vector.fuse(" + dense + ", " + ftScored + ", { fusion: 'DBSF', limit: 200 }))");
        variants.put("L fuse LINEAR, full-text top-200 with score",
                "SELECT expand(vector.fuse(" + dense + ", " + ftScored + ", { fusion: 'LINEAR', limit: 200 }))");
        variants.put("M fuse RRF k=10, full-text top-200",
                "SELECT expand(vector.fuse(" + dense + ", " + ftTop.replace("%N%", "200") + ", { fusion: 'RRF', k: 10, limit: 200 }))");
        for (String stop : new String[] {"STOP1", "STOP2"}) {
            String label = stop.equals("STOP1") ? "Lucene English stop words" : "English + question stop words";
            variants.put("N" + stop.substring(4) + " full-text count, " + label,
                    "#" + stop + "#SELECT count(*) AS n FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true");
            variants.put("O" + stop.substring(4) + " fuse RRF top-200, " + label,
                    "#" + stop + "#SELECT expand(vector.fuse(" + dense + ", " + ftTop.replace("%N%", "200") + ", { fusion: 'RRF', limit: 200 }))");
            variants.put("P" + stop.substring(4) + " fuse LINEAR top-200, " + label,
                    "#" + stop + "#SELECT expand(vector.fuse(" + dense + ", " + ftScored + ", { fusion: 'LINEAR', limit: 200 }))");
            variants.put("Q" + stop.substring(4) + " full-text only top-100, " + label,
                    "#" + stop + "#SELECT id, $score AS score FROM Passage WHERE SEARCH_INDEX('" + FT_IDX + "', :q) = true ORDER BY $score DESC LIMIT 100");
        }
        try (DatabaseFactory factory = new DatabaseFactory(dbPath); Database db = factory.open()) {
            java.util.logging.Logger.getLogger("com.arcadedb").setLevel(java.util.logging.Level.WARNING);
            // Warm the vector graph and caches.
            for (int i = 0; i < Math.min(20, queries.size()); i++) {
                run(db, variants.get("B dense only (k=100)"), queries.get(i));
            }
            for (Map.Entry<String, String> v : variants.entrySet()) {
                if (only != null && only.stream().noneMatch(o -> v.getKey().startsWith(o))) continue;
                for (int i = 0; i < Math.min(10, queries.size()); i++) run(db, v.getValue(), queries.get(i)); // warm-up
                long[] nanos = new long[queries.size()];
                double ndcg = 0; long rows = 0; int failed = 0; boolean ranked = false;
                for (int i = 0; i < queries.size(); i++) {
                    Q q = queries.get(i);
                    long t0 = System.nanoTime();
                    Outcome o;
                    try { o = run(db, v.getValue(), q); } catch (Exception e) { failed++; o = new Outcome(List.of(), 0); }
                    nanos[i] = System.nanoTime() - t0;
                    rows += o.rows();
                    if (!o.ids().isEmpty()) { ranked = true; ndcg += ndcg10(o.ids(), qrels.getOrDefault(q.id(), Set.of())); }
                }
                long[] s = nanos.clone(); Arrays.sort(s);
                System.out.printf("%-52s mean %8.1f ms  p50 %8.1f ms  rows/query %9.0f  nDCG@10 %s  failed %d%n",
                        v.getKey(), Arrays.stream(nanos).average().orElse(0) / 1e6, s[s.length / 2] / 1e6,
                        (double) rows / queries.size(), ranked ? String.format("%.4f", ndcg / queries.size()) : "  -   ", failed);
            }
        }
    }

    static Outcome run(Database db, String sqlTemplate, Q q) {
        String text = q.text();
        String template = sqlTemplate;
        if (template.startsWith("#STOP")) {
            Set<String> stop = template.startsWith("#STOP1#") ? STOP1 : STOP2;
            template = template.substring(7);
            text = Arrays.stream(text.split("\\s+")).filter(w -> !stop.contains(w.toLowerCase(Locale.ROOT))).reduce((x, y) -> x + " " + y).orElse(text);
        }
        String sql = template.replace("%VEC%", Arrays.toString(q.vec()));
        Map<String, Object> params = sql.contains(":q") ? Map.of("q", text) : Map.of();
        List<String> ids = new ArrayList<>();
        long rows = 0;
        try (ResultSet rs = db.query("sql", sql, params)) {
            while (rs.hasNext()) {
                Result r = rs.next();
                rows++;
                if (r.hasProperty("n") && r.getPropertyNames().size() == 1) { rows = ((Number) r.getProperty("n")).longValue(); continue; }
                String id = idOf(db, r);
                if (id != null && ids.size() < 10) ids.add(id);
            }
        }
        if (sqlTemplate.contains("@rid AS rid, $score")) ids.clear(); // E: rids only, not ranked documents
        return new Outcome(ids, rows);
    }

    // Lucene's EnglishAnalyzer default stop set, and the same plus question words.
    static final Set<String> STOP1 = Set.of("a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they", "this", "to", "was", "will", "with");
    static final Set<String> STOP2;
    static {
        Set<String> s2 = new HashSet<>(STOP1);
        s2.addAll(List.of("what", "who", "whom", "when", "where", "which", "why", "how", "does", "do", "did", "was", "were", "has", "have", "had", "i", "you", "we", "he", "she", "his", "her", "its", "from", "can", "many", "much"));
        STOP2 = Set.copyOf(s2);
    }

    static String idOf(Database db, Result r) {
        if (r.hasProperty("id") && r.getProperty("id") instanceof String s) return s;
        Vertex v = null;
        if (r.isVertex()) v = r.getVertex().get();
        else if (r.hasProperty("record") && r.getProperty("record") instanceof Identifiable i) v = (Vertex) db.lookupByRID(i.getIdentity(), true);
        else if (r.hasProperty("@rid") && r.getProperty("@rid") instanceof RID rid) v = (Vertex) db.lookupByRID(rid, true);
        return v == null ? null : (String) v.get("id");
    }

    static double ndcg10(List<String> ranked, Set<String> relevant) {
        double dcg = 0, idcg = 0;
        for (int i = 0; i < Math.min(10, ranked.size()); i++) if (relevant.contains(ranked.get(i))) dcg += 1 / (Math.log(i + 2) / Math.log(2));
        for (int i = 0; i < Math.min(10, relevant.size()); i++) idcg += 1 / (Math.log(i + 2) / Math.log(2));
        return idcg == 0 ? 0 : dcg / idcg;
    }

    static List<Q> loadQueries(int n) throws Exception {
        List<String> ids = Files.readAllLines(Path.of(EMB + "queries.ids")).stream().filter(s -> !s.isBlank()).toList();
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(Path.of(EMB + "queries.f32"))).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, String> text = new HashMap<>();
        ObjectMapper json = new ObjectMapper();
        Set<String> wanted = new HashSet<>(ids.subList(0, n));
        try (BufferedReader r = Files.newBufferedReader(Path.of(D + "datasets/nq/raw/queries.jsonl"))) {
            for (String line; (line = r.readLine()) != null; ) {
                JsonNode q = json.readTree(line);
                if (wanted.contains(q.get("_id").asText())) text.put(q.get("_id").asText(), q.get("text").asText());
            }
        }
        List<Q> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float[] v = new float[384];
            for (int d = 0; d < 384; d++) v[d] = buf.getFloat((i * 384 + d) * 4);
            out.add(new Q(ids.get(i), text.get(ids.get(i)), v));
        }
        return out;
    }

    static Map<String, Set<String>> loadQrels() throws Exception {
        Map<String, Set<String>> m = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(D + "datasets/nq/raw/qrels/test.tsv")).subList(1, Files.readAllLines(Path.of(D + "datasets/nq/raw/qrels/test.tsv")).size())) {
            String[] p = line.split("\t");
            if (Integer.parseInt(p[2]) > 0) m.computeIfAbsent(p[0], k -> new HashSet<>()).add(p[1]);
        }
        return m;
    }
}
