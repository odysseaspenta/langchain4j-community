# RAG benchmark: smoke profile, ArcadeDB 26.7.2

| Run | |
|---|---|
| Started / finished | 2026-09-29T21:31:29.772146657Z / 2026-09-29T22:19:28.631343256Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | ab31a4117fe0867891541980cc3115352b2d2491 (dirty) |
| Dataset | nq, seed 42, k = 100, 1 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Peak heap MiB | Server peak MiB |
|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 59.8 | 1,672.9 | 59.0 | 376 | 5,740 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 185.1 | 540.2 | 0.7 | 369 | 5,490 | 9,425 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.7.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 30000 ms. Time to searchable = `REBUILD INDEX` (upper bound; embedded builds the graph only).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8528 | 0.9421 | 0.9806 | 0.8336 | 0.9926 | 0.9835 | 31.35 | 39.48 | 42.47 | 33.38 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.4526 | 0.8445 | 0.9674 | 0.3409 | – | – | 110.52 | 159.93 | 178.46 | 111.96 | 0 | 2 | 0 | 0.1% | store default |
| smoke | arcadedb-remote | dense ⚠ aborted | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 30,327.04 | 34,365.12 | 34,365.12 | 30,327.04 | 0 | 10 | 3442 | 100.0% | store default |
| smoke | arcadedb-remote | hybrid-asis | 0.4535 | 0.8435 | 0.9677 | 0.3423 | – | – | 181.40 | 215.39 | 228.00 | 182.04 | 0 | 2 | 0 | 0.1% | store default |

⚠ arcadedb-remote / dense aborted: 10 consecutive failed or empty queries. Skipped queries count as empty rankings; latency covers the queries run.

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
