# RAG benchmark: baseline profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-10-22T21:11:44.533741210Z / 2026-10-22T21:32:40.561572859Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | f6109cb8dc56b6a8590323473bb35c1677311c32 (dirty) |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 12.1 | 8,263.4 | 16.1 | 372 | 600 | 5,938 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 35.9 | 2,783.6 | 23.4 | 356 | 89 | 8,224 | 8,811 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8530 | 0.9414 | 0.9791 | 0.8340 | 0.9921 | 0.9832 | 3.30 | 3.98 | 4.37 | 3.43 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.8547 | 0.9494 | 0.9805 | 0.8326 | – | – | 20.40 | 39.17 | 48.60 | 20.87 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8511 | 0.9396 | 0.9783 | 0.8319 | 0.9927 | 0.9831 | 19.15 | 22.02 | 23.58 | 19.71 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.8529 | 0.9482 | 0.9796 | 0.8304 | – | – | 34.73 | 55.53 | 65.85 | 34.93 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
