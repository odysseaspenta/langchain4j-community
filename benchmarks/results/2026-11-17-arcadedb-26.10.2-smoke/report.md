# RAG benchmark: smoke profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-11-17T22:34:45.353518123Z / 2026-11-17T22:45:57.355771077Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 4a2c0398dcd216bc4da44ed8e0d6a9756bfa4e9b (dirty), store module modified |
| Dataset | nq, seed 42, k = 100, 1 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 11.4 | 8,740.3 | 16.0 | 386 | 599 | 5,909 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 34.5 | 2,901.9 | 23.1 | 353 | 89 | 8,150 | 4,713 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 4g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8526 | 0.9414 | 0.9792 | 0.8334 | 0.9922 | 0.9834 | 3.13 | 3.74 | 4.37 | 3.46 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.8542 | 0.9494 | 0.9807 | 0.8319 | – | – | 20.18 | 37.47 | 47.49 | 20.52 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8506 | 0.9391 | 0.9773 | 0.8315 | 0.9922 | 0.9832 | 19.27 | 20.98 | 26.48 | 19.86 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.8521 | 0.9473 | 0.9786 | 0.8297 | – | – | 34.60 | 53.72 | 62.95 | 34.91 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
