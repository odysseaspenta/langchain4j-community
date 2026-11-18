// Standalone reproduction: latency of vector.neighbors with a filter subquery, ArcadeDB server over HTTP.
// Only the JDK is needed (java.net.http); the server is the official Docker image.
//
//   java FilteredNeighborsProbe.java <port> <rootPassword> [records] [queries]
//
// Loads <records> (default 100,000) synthetic records into database "bench": type Passage with doc_id, text (~500 chars),
// embedding (384 floats, normalized) and meta_bucket = i % 100, an LSM_VECTOR index (COSINE, maxConnections 16,
// beamWidth 100), no index on meta_bucket. Then times, for filters passing 1 %, 10 % and 50 % of the records:
//   scan count     SELECT count(*) FROM Passage WHERE meta_bucket < B
//   allow-list     SELECT @rid FROM Passage WHERE meta_bucket < B                       (rows returned to the client)
//   knn            vector.neighbors(..., 100, { efSearch: 800 })                         (no filter, for reference)
//   knn+filter     vector.neighbors(..., 100, { efSearch: 800, filter: (SELECT @rid FROM Passage WHERE ...).@rid })
//   knn+filter param  the same, with the filter value bound as a parameter: meta_bucket < :p0
//   store query    SELECT *, !record FROM (vector.neighbors(... filter subquery with :p0)), as the LangChain4j store sends
//   knn+rid list   vector.neighbors(..., 100, { efSearch: 800, filter: :rids })          (allow-list passed as parameter)
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilteredNeighborsProbe {
    static final int DIM = 384, K = 100, EF = 800, WARMUP = 20;
    static HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    static String base, auth;

    public static void main(String[] a) throws Exception {
        base = "http://127.0.0.1:" + a[0] + "/api/v1/";
        auth = "Basic " + Base64.getEncoder().encodeToString(("root:" + a[1]).getBytes(StandardCharsets.UTF_8));
        int records = a.length > 2 ? Integer.parseInt(a[2]) : 100_000;
        int queries = a.length > 3 ? Integer.parseInt(a[3]) : 200;
        System.out.println("server " + version() + ", endpoint " + System.getProperty("endpoint", "command")
                + ", serializer " + System.getProperty("serializer", "(default)") + ", typeHints " + Boolean.getBoolean("typeHints"));

        load(records);
        Random random = new Random(7);
        List<String> qs = new ArrayList<>();
        for (int i = 0; i < queries + WARMUP; i++) qs.add(vec(randomUnit(random)));

        String knn = "SELECT doc_id FROM (SELECT expand(`vector.neighbors`('Passage[embedding]', %s, " + K
                + ", { efSearch: " + EF + "%s })))";
        time("knn (no filter)", qs, q -> String.format(knn, q, ""), Map.of(), K);
        for (int b : new int[] {1, 10, 50}) {
            String where = "meta_bucket < " + b;
            int expected = records / 100 * b;
            time("scan count, " + b + " %", qs, q -> "SELECT count(*) AS n FROM Passage WHERE " + where, Map.of(), -1);
            time("allow-list rows, " + b + " %", qs, q -> "SELECT @rid FROM Passage WHERE " + where, Map.of(), expected);
            time("knn+filter subquery, " + b + " %", qs,
                    q -> String.format(knn, q, ", filter: (SELECT @rid FROM Passage WHERE " + where + ").@rid"),
                    Map.of(), Math.min(K, expected));
            time("knn+filter subquery, param, " + b + " %", qs,
                    q -> String.format(knn, q, ", filter: (SELECT @rid FROM Passage WHERE meta_bucket < :p0).@rid"),
                    Map.of("p0", b), Math.min(K, expected));
            time("store query, param, " + b + " %", qs,
                    q -> "SELECT *, !record FROM (SELECT expand(`vector.neighbors`('Passage[embedding]', " + q + ", " + K
                            + ", { efSearch: " + EF + ", filter: (SELECT @rid FROM Passage WHERE meta_bucket < :p0).@rid })))",
                    Map.of("p0", b), Math.min(K, expected));
            List<String> rids = rids("SELECT @rid FROM Passage WHERE " + where);
            time("knn+filter rid list, " + b + " %", qs, q -> String.format(knn, q, ", filter: :rids"),
                    Map.of("rids", rids), Math.min(K, expected));
        }
    }

    static void load(int records) throws Exception {
        if (count("SELECT count(*) AS n FROM Passage") == records) {
            System.out.println("reusing " + records + " records");
            return;
        }
        command("sqlscript", """
                CREATE VERTEX TYPE Passage IF NOT EXISTS;
                CREATE PROPERTY Passage.doc_id IF NOT EXISTS STRING;
                CREATE PROPERTY Passage.embedding IF NOT EXISTS ARRAY_OF_FLOATS;
                CREATE PROPERTY Passage.text IF NOT EXISTS STRING;
                CREATE INDEX IF NOT EXISTS ON Passage (doc_id) NOTUNIQUE;
                CREATE INDEX IF NOT EXISTS ON Passage (text) FULL_TEXT;
                CREATE INDEX IF NOT EXISTS ON Passage (embedding) LSM_VECTOR
                  METADATA { "dimensions": 384, "similarity": "COSINE", "maxConnections": 16, "beamWidth": 100 };
                """, Map.of());
        Random random = new Random(42);
        String[] words = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda river city music history war"
                .split(" ");
        long start = System.nanoTime();
        int batch = 500;
        for (int from = 0; from < records; from += batch) {
            StringBuilder script = new StringBuilder("BEGIN;\n");
            Map<String, Object> params = new LinkedHashMap<>();
            for (int i = from; i < Math.min(records, from + batch); i++) {
                int r = i - from;
                script.append("INSERT INTO Passage SET doc_id = :id").append(r).append(", embedding = :e").append(r)
                        .append(", text = :t").append(r).append(", meta_bucket = :b").append(r).append(";\n");
                StringBuilder text = new StringBuilder();
                while (text.length() < 500) text.append(words[random.nextInt(words.length)]).append(' ');
                params.put("id" + r, "p" + i);
                params.put("e" + r, randomUnit(random));
                params.put("t" + r, text.toString());
                params.put("b" + r, i % 100);
            }
            script.append("COMMIT;");
            command("sqlscript", script.toString(), params);
        }
        System.out.printf(Locale.ROOT, "loaded %d records in %.1f s%n", records, (System.nanoTime() - start) / 1e9);
        // Build the graph before measuring.
        command("sql", String.format("SELECT count(*) FROM (SELECT expand(`vector.neighbors`('Passage[embedding]', %s, 10)))",
                vec(randomUnit(random))), Map.of());
    }

    static void time(String name, List<String> qs, Function<String, String> sql, Map<String, Object> params,
            int expectedRows) throws Exception {
        String only = System.getProperty("only");
        if (only != null && Arrays.stream(only.split(",")).noneMatch(name::startsWith)) return;
        long[] t = new long[qs.size() - WARMUP];
        int rows = 0;
        for (int i = 0; i < qs.size(); i++) {
            long start = System.nanoTime();
            String body = command("sql", sql.apply(qs.get(i)), params);
            long elapsed = System.nanoTime() - start;
            if (i >= WARMUP) {
                t[i - WARMUP] = elapsed;
                rows = countRows(body);
            }
        }
        double mean = Arrays.stream(t).average().orElse(0) / 1e6;
        Arrays.sort(t);
        System.out.printf(Locale.ROOT, "RESULT %-28s p50 %8.2f ms  p90 %8.2f ms  mean %8.2f ms  rows %d%s%n", name,
                t[t.length / 2] / 1e6, t[t.length * 9 / 10] / 1e6, mean, rows,
                expectedRows >= 0 && rows != expectedRows ? "  (expected " + expectedRows + ")" : "");
    }

    static String command(String language, String command, Map<String, Object> params) throws Exception {
        boolean measured = !language.equals("sqlscript") && !command.startsWith("CREATE");
        String serializer = measured ? System.getProperty("serializer", "") : "";
        String json = "{\"language\":" + quote(language) + ",\"command\":" + quote(command)
                + (params.isEmpty() ? "" : ",\"params\":" + json(params))
                + (serializer.isEmpty() ? "" : ",\"serializer\":" + quote(serializer))
                + (measured && Boolean.getBoolean("typeHints") ? ",\"typeHints\":true" : "") + "}";
        String endpoint = measured ? System.getProperty("endpoint", "command") : "command";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + endpoint + "/bench"))
                .header("Authorization", auth).header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(response.statusCode() + ": " + response.body().substring(0,
                    Math.min(500, response.body().length())) + "\nfor: " + command.substring(0, Math.min(300, command.length())));
        }
        return response.body();
    }

    static String version() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "server?mode=basic")).header("Authorization", auth).build();
        String body = http.send(request, HttpResponse.BodyHandlers.ofString()).body();
        Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return m.find() ? m.group(1) : body.substring(0, Math.min(200, body.length()));
    }

    static long count(String sql) {
        try {
            Matcher m = Pattern.compile("\"n\"\\s*:\\s*(\\d+)").matcher(command("sql", sql, Map.of()));
            return m.find() ? Long.parseLong(m.group(1)) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    static List<String> rids(String sql) throws Exception {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"@rid\"\\s*:\\s*\"(#\\d+:\\d+)\"").matcher(command("sql", sql, Map.of()));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static int countRows(String body) {
        int start = body.indexOf("\"result\"");
        if (start < 0) return -1;
        int depth = 0, rows = 0;
        for (int i = body.indexOf('[', start) + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') { // skip strings
                for (i++; body.charAt(i) != '"'; i++) if (body.charAt(i) == '\\') i++;
            } else if (c == '{') {
                if (depth++ == 0) rows++;
            } else if (c == '}') depth--;
            else if (c == ']' && depth == 0) break;
        }
        return rows;
    }

    static float[] randomUnit(Random random) {
        float[] v = new float[DIM];
        double norm = 0;
        for (int i = 0; i < DIM; i++) { v[i] = (float) random.nextGaussian(); norm += v[i] * v[i]; }
        for (int i = 0; i < DIM; i++) v[i] /= (float) Math.sqrt(norm);
        return v;
    }

    static String vec(float[] v) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) b.append(i == 0 ? "" : ",").append(v[i]);
        return b.append(']').toString();
    }

    static String json(Object o) {
        if (o instanceof Map<?, ?> m) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            m.forEach((k, v) -> j.add(quote(k.toString()) + ":" + json(v)));
            return j.toString();
        }
        if (o instanceof List<?> l) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            l.forEach(v -> j.add(json(v)));
            return j.toString();
        }
        if (o instanceof float[] f) return vec(f);
        if (o instanceof Number) return o.toString();
        return quote(o.toString());
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> b.append(c);
            }
        }
        return b.append('"').toString();
    }
}
