# RAG benchmark: filtered profile, ArcadeDB 26.11.1

| Run | |
|---|---|
| Started / finished | 2026-11-17T20:10:49.402459322Z / 2026-11-17T22:34:27.781307205Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 4a2c0398dcd216bc4da44ed8e0d6a9756bfa4e9b (dirty), store module modified |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 11.3 | 8,853.3 | 15.8 | 280 | 553 | 5,889 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 34.4 | 2,909.7 | 29.0 | 322 | 89 | 7,626 | 4,666 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.11.1, server CPUs 4-7, heap 4g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8529 | 0.9412 | 0.9792 | 0.8340 | 0.9923 | 0.9835 | 2.28 | 2.71 | 2.97 | 2.33 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 11.73 | 13.05 | 14.06 | 11.86 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 19.73 | 21.65 | 23.79 | 19.84 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-50 | 0.4584 | 0.4818 | 0.4906 | 0.4816 | 0.9916 | 0.9731 | 2.41 | 2.86 | 3.17 | 2.43 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 66.79 | 103.80 | 120.01 | 66.85 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-10 | 0.0869 | 0.0872 | 0.0879 | 0.0989 | – | – | 57.46 | 103.72 | 120.20 | 57.31 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-50 | 0.4624 | 0.4857 | 0.4950 | 0.4856 | – | – | 23.37 | 41.51 | 51.31 | 23.44 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8511 | 0.9401 | 0.9786 | 0.8319 | 0.9925 | 0.9835 | 17.95 | 20.14 | 23.37 | 18.37 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 48.45 | 50.30 | 51.91 | 48.57 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 57.67 | 60.16 | 62.71 | 57.65 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-50 | 0.4595 | 0.4829 | 0.4919 | 0.4826 | 0.9960 | 0.9901 | 71.33 | 74.77 | 77.51 | 71.39 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 63.54 | 82.60 | 92.15 | 63.83 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-10 | 0.0868 | 0.0872 | 0.0879 | 0.0988 | – | – | 73.22 | 92.42 | 102.73 | 73.31 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-50 | 0.4586 | 0.4832 | 0.4921 | 0.4812 | – | – | 88.01 | 107.81 | 117.39 | 88.04 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
