// The store's filtered query sent through ArcadeDB's Java client (RemoteDatabase), against a database loaded by
// FilteredNeighborsProbe. Run with arcadedb-network + arcadedb-engine (and their dependencies) on the classpath:
//   java -cp <classpath> ClientProbe.java <port> <rootPassword> [queries]
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import java.util.*;

public class ClientProbe {
    public static void main(String[] a) {
        java.util.logging.Logger.getLogger("com.arcadedb").setLevel(java.util.logging.Level.SEVERE);
        int queries = a.length > 2 ? Integer.parseInt(a[2]) : 200;
        RemoteDatabase db = new RemoteDatabase("127.0.0.1", Integer.parseInt(a[0]), "bench", "root", a[1]);
        System.out.println("client " + com.arcadedb.Constants.getRawVersion());
        Random random = new Random(7);
        List<String> qs = new ArrayList<>();
        for (int i = 0; i < queries + 20; i++) qs.add(vec(random));
        String knn = "SELECT *, !record FROM (SELECT expand(`vector.neighbors`('Passage[embedding]', %s, 100, { efSearch: 800%s })))";
        time("store query, no filter", db, qs, q -> String.format(knn, q, ""), Map.of());
        for (int b : new int[] {1, 10, 50}) {
            time("store query, literal, " + b + " %", db, qs,
                    q -> String.format(knn, q, ", filter: (SELECT @rid FROM Passage WHERE meta_bucket < " + b + ").@rid"),
                    Map.of());
            time("store query, param, " + b + " %", db, qs,
                    q -> String.format(knn, q, ", filter: (SELECT @rid FROM Passage WHERE meta_bucket < :p0).@rid"),
                    Map.of("p0", b));
        }
        System.exit(0);
    }

    static void time(String name, RemoteDatabase db, List<String> qs, java.util.function.Function<String, String> sql,
            Map<String, Object> params) {
        long[] t = new long[qs.size() - 20];
        int rows = 0;
        for (int i = 0; i < qs.size(); i++) {
            long start = System.nanoTime();
            int n = 0;
            try (ResultSet rs = db.query("sql", sql.apply(qs.get(i)), params)) {
                while (rs.hasNext()) {
                    Result r = rs.next();
                    r.getProperty("embedding");
                    n++;
                }
            }
            if (i >= 20) {
                t[i - 20] = System.nanoTime() - start;
                rows = n;
            }
        }
        double mean = Arrays.stream(t).average().orElse(0) / 1e6;
        Arrays.sort(t);
        System.out.printf(Locale.ROOT, "RESULT %-28s p50 %8.2f ms  p90 %8.2f ms  mean %8.2f ms  rows %d%n", name,
                t[t.length / 2] / 1e6, t[t.length * 9 / 10] / 1e6, mean, rows);
    }

    static String vec(Random random) {
        double[] v = new double[384];
        double norm = 0;
        for (int i = 0; i < v.length; i++) { v[i] = random.nextGaussian(); norm += v[i] * v[i]; }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) b.append(i == 0 ? "" : ",").append((float) (v[i] / Math.sqrt(norm)));
        return b.append(']').toString();
    }
}
