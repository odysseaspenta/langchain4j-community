# RAG benchmark: filtered profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-11-17T22:46:08.533460257Z / 2026-11-18T00:43:42.876959784Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 4a2c0398dcd216bc4da44ed8e0d6a9756bfa4e9b (dirty), store module modified |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 11.9 | 8,426.9 | 15.7 | 365 | 599 | 5,938 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 34.6 | 2,888.7 | 23.1 | 355 | 89 | 8,695 | 4,685 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 4g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8527 | 0.9415 | 0.9794 | 0.8335 | 0.9926 | 0.9830 | 3.09 | 3.65 | 4.16 | 3.33 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 13.54 | 14.92 | 16.83 | 13.79 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 21.97 | 23.91 | 27.41 | 22.07 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-50 | 0.4585 | 0.4818 | 0.4908 | 0.4817 | 0.9915 | 0.9729 | 3.25 | 3.83 | 4.37 | 3.26 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 61.44 | 95.26 | 111.96 | 62.12 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-10 | 0.0873 | 0.0872 | 0.0879 | 0.0993 | – | – | 56.28 | 98.58 | 114.17 | 56.25 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-50 | 0.4617 | 0.4848 | 0.4935 | 0.4849 | – | – | 34.09 | 51.49 | 60.62 | 34.04 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8504 | 0.9389 | 0.9769 | 0.8313 | 0.9924 | 0.9832 | 19.20 | 21.21 | 23.89 | 19.74 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 27.15 | 29.33 | 31.13 | 27.64 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 36.53 | 39.08 | 42.18 | 36.57 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-50 | 0.4592 | 0.4826 | 0.4916 | 0.4823 | 0.9961 | 0.9902 | 48.74 | 52.15 | 56.25 | 48.77 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 43.54 | 62.21 | 72.77 | 43.91 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-10 | 0.0868 | 0.0872 | 0.0879 | 0.0988 | – | – | 53.32 | 72.19 | 81.31 | 53.39 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-50 | 0.4583 | 0.4829 | 0.4918 | 0.4809 | – | – | 65.57 | 84.83 | 95.65 | 65.53 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
