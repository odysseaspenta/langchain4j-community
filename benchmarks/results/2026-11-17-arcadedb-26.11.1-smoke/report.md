# RAG benchmark: smoke profile, ArcadeDB 26.11.1

| Run | |
|---|---|
| Started / finished | 2026-11-17T20:00:22.860219539Z / 2026-11-17T20:10:38.386408069Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 4a2c0398dcd216bc4da44ed8e0d6a9756bfa4e9b (dirty), store module modified |
| Dataset | nq, seed 42, k = 100, 1 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 12.5 | 8,017.0 | 16.2 | 332 | 555 | 5,924 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 34.0 | 2,937.6 | 23.4 | 332 | 89 | 8,261 | 4,679 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.11.1, server CPUs 4-7, heap 4g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8533 | 0.9418 | 0.9796 | 0.8344 | 0.9923 | 0.9833 | 2.29 | 2.78 | 3.07 | 2.34 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.8550 | 0.9498 | 0.9811 | 0.8328 | – | – | 16.66 | 33.91 | 43.09 | 17.27 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8501 | 0.9383 | 0.9767 | 0.8311 | 0.9926 | 0.9834 | 18.08 | 20.38 | 23.15 | 18.32 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.8514 | 0.9466 | 0.9781 | 0.8290 | – | – | 32.01 | 51.14 | 60.86 | 32.18 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
