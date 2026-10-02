# RAG benchmark: baseline profile, ArcadeDB 26.7.2

| Run | |
|---|---|
| Started / finished | 2026-10-01T00:12:58.392872381Z / 2026-10-01T05:12:04.780324418Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 14.0 GiB |
| Git | c8e2abaa |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 57.6 | 1,736.5 | 53.9 | 377 | 668 | 9,504 | – |
| arcadedb-embedded | standard | 1,000,000 / 1,000,000 | 593.6 | 1,684.7 | 785.9 | 3,773 | 5,054 | 23,343 | – |

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8527 | 0.9420 | 0.9803 | 0.8335 | 0.9921 | 0.9833 | 28.34 | 35.72 | 38.40 | 28.36 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-asis | 0.4525 | 0.8451 | 0.9679 | 0.3406 | – | – | 108.43 | 156.99 | 176.48 | 108.89 | 0 | 2 | 0 | 0.1% | store default |
| standard | arcadedb-embedded | dense | 0.6234 | 0.8025 | 0.9307 | 0.5821 | 0.9738 | 0.9579 | 35.26 | 44.49 | 47.83 | 35.36 | 0 | 0 | 0 | 0.0% | store default |
| standard | arcadedb-embedded | hybrid-asis | 0.3206 | 0.6135 | 0.9047 | 0.2402 | – | – | 1,018.40 | 1,598.97 | 1,780.63 | 1,016.23 | 0 | 2 | 0 | 0.1% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
