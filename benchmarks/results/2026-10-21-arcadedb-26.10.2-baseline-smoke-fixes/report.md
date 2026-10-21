# RAG benchmark: baseline profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-10-21T20:09:36.648983800Z / 2026-10-21T20:54:41.464525416Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 7dd065c8c3cfda624811c458f1db744d1d360474 |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 11.9 | 8,388.0 | 16.3 | 324 | 600 | 2,593 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 35.3 | 2,830.0 | 23.3 | 349 | 89 | 7,618 | 8,800 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8537 | 0.9424 | 0.9802 | 0.8346 | 0.9919 | 0.9836 | 3.26 | 4.09 | 4.65 | 3.58 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.8557 | 0.9504 | 0.9814 | 0.8337 | – | – | 21.69 | 39.66 | 50.09 | 22.45 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8318 | 0.9178 | 0.9543 | 0.8134 | 0.9702 | 0.9140 | 29.93 | 31.52 | 35.28 | 30.31 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.8525 | 0.9478 | 0.9792 | 0.8300 | – | – | 127.89 | 147.88 | 159.37 | 128.32 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
