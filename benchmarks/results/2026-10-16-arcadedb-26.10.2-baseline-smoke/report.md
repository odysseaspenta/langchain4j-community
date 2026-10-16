# RAG benchmark: baseline profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-10-16T17:57:59.114441420Z / 2026-10-16T21:52:17.904263786Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | dc30005bf123f8c5c7915eac16f6ea815d1e7ed6 (dirty) |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 31.9 | 3,137.1 | 16.5 | 424 | 699 | 5,672 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 173.6 | 576.0 | 25.1 | 388 | 88 | 7,726 | 8,875 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8521 | 0.9407 | 0.9789 | 0.8331 | 0.9934 | 0.9834 | 3.34 | 4.26 | 4.69 | 3.63 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.7941 | 0.9178 | 0.9894 | 0.7665 | – | – | 543.66 | 635.29 | 693.68 | 551.04 | 0 | 2 | 0 | 0.1% | store default |
| smoke | arcadedb-remote | dense ⚠ aborted | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 26,201.25 | 32,346.64 | 32,346.64 | 26,201.25 | 0 | 10 | 3442 | 100.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.7935 | 0.9174 | 0.9891 | 0.7657 | – | – | 477.19 | 563.26 | 606.66 | 480.57 | 0 | 2 | 0 | 0.1% | store default |

⚠ arcadedb-remote / dense aborted: 10 consecutive failed or empty queries. Skipped queries count as empty rankings; latency covers the queries run.

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
