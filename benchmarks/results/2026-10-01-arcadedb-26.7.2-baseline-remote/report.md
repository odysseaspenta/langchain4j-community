# RAG benchmark: baseline profile, ArcadeDB 26.7.2

> **Status: incomplete** — at least one load or scenario failed; see the errors below.

| Run | |
|---|---|
| Started / finished | 2026-10-01T05:12:15.488436743Z / 2026-10-01T06:45:59.341246313Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 4.0 GiB |
| Git | c8e2abaa (dirty) |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-remote | smoke | 100,000 / 100,000 | 188.9 | 529.3 | 91.8 | 473 | 80 | 3,044 | 26,126 |
| arcadedb-remote | standard | failed | – | – | – | – | – | – | – |

arcadedb-remote (smoke): arcadedata/arcadedb:26.7.2, server CPUs 4-7, heap 24g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

arcadedb-remote (standard): arcadedata/arcadedb:26.7.2, server CPUs 4-7, heap 24g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-remote | dense ⚠ aborted | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 32,930.76 | 46,646.25 | 46,646.25 | 32,930.76 | 0 | 10 | 3442 | 100.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.4540 | 0.8475 | 0.9698 | 0.3414 | – | – | 180.21 | 214.57 | 227.89 | 180.95 | 0 | 2 | 0 | 0.1% | store default |

⚠ arcadedb-remote / dense aborted: 10 consecutive failed or empty queries. Skipped queries count as empty rankings; latency covers the queries run.

❌ arcadedb-remote / standard failed: `java.lang.IllegalStateException: ArcadeDB server ran out of heap (24g); see /sysnet/rag-bench/databases/arcadedb-26.7.2/standard-remote/log. Increase --server-heap.`

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
