# RAG benchmark: smoke profile, ArcadeDB 26.7.2

| Run | |
|---|---|
| Started / finished | 2026-09-29T23:25:34.024351536Z / 2026-09-29T23:56:25.007099096Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 071bae91 (dirty) |
| Dataset | nq, seed 42, k = 100, 1 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-remote | smoke | 100,000 / 100,000 | 181.6 | 550.6 | 93.7 | 375 | 26 | 5,316 | 9,419 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.7.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-remote | dense ⚠ aborted | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 30,244.23 | 31,992.97 | 31,992.97 | 30,244.23 | 0 | 10 | 3442 | 100.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.4537 | 0.8458 | 0.9690 | 0.3415 | – | – | 180.26 | 215.07 | 228.14 | 181.11 | 0 | 2 | 0 | 0.1% | store default |

⚠ arcadedb-remote / dense aborted: 10 consecutive failed or empty queries. Skipped queries count as empty rankings; latency covers the queries run.

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
