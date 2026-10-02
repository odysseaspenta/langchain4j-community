# RAG benchmark: baseline profile, ArcadeDB 26.9.1

| Run | |
|---|---|
| Started / finished | 2026-10-01T14:31:28.186140289Z / 2026-10-01T18:56:14.707404221Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 4ea752e3 |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 36.2 | 2,760.4 | 39.1 | 357 | 631 | 5,630 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 157.6 | 634.4 | 53.1 | 437 | 91 | 7,275 | 9,082 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.9.1, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8536 | 0.9428 | 0.9805 | 0.8343 | 0.9924 | 0.9835 | 5.67 | 7.04 | 7.79 | 6.69 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.7942 | 0.9178 | 0.9888 | 0.7666 | – | – | 629.77 | 732.37 | 780.03 | 629.85 | 0 | 2 | 0 | 0.1% | store default |
| smoke | arcadedb-remote | dense ⚠ aborted | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 28,115.08 | 31,724.92 | 31,724.92 | 28,115.08 | 0 | 10 | 3442 | 100.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.7937 | 0.9187 | 0.9886 | 0.7658 | – | – | 530.24 | 618.55 | 659.86 | 531.50 | 0 | 2 | 0 | 0.1% | store default |

⚠ arcadedb-remote / dense aborted: 10 consecutive failed or empty queries. Skipped queries count as empty rankings; latency covers the queries run.

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
