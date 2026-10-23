# RAG benchmark: filtered profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-10-23T12:41:23.981856232Z / 2026-10-23T16:03:19.895639554Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 0c50043ee1a288b34af858eca62fee6777033a32 |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 12.6 | 7,931.2 | 16.8 | 331 | 603 | 5,934 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 36.1 | 2,767.8 | 23.3 | 360 | 89 | 6,430 | 8,814 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8531 | 0.9421 | 0.9799 | 0.8338 | 0.9925 | 0.9832 | 3.29 | 4.01 | 4.54 | 3.63 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 0.4256 | 0.0426 | 3.48 | 4.52 | 5.06 | 3.34 | 0 | 38 | 0 | 100.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-10 | 0.0874 | 0.0869 | 0.0872 | 0.0993 | 0.9839 | 0.3964 | 3.46 | 4.22 | 4.68 | 3.77 | 0 | 0 | 0 | 100.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-50 | 0.4585 | 0.4818 | 0.4908 | 0.4817 | 0.9916 | 0.9730 | 3.45 | 4.20 | 4.64 | 3.44 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 33.07 | 52.36 | 61.73 | 33.42 | 0 | 0 | 0 | 100.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-10 | 0.0876 | 0.0872 | 0.0879 | 0.0998 | – | – | 33.61 | 52.51 | 63.05 | 33.55 | 0 | 0 | 0 | 51.5% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-50 | 0.4618 | 0.4851 | 0.4945 | 0.4850 | – | – | 32.97 | 51.94 | 62.58 | 33.30 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8513 | 0.9405 | 0.9785 | 0.8320 | 0.9923 | 0.9831 | 19.32 | 22.92 | 28.30 | 19.88 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 0.5296 | 0.0534 | 73.52 | 75.52 | 81.62 | 73.55 | 0 | 15 | 0 | 100.0% | store default |
| smoke | arcadedb-remote | dense-filtered-10 | 0.0871 | 0.0866 | 0.0869 | 0.0990 | 0.9825 | 0.4948 | 73.52 | 75.58 | 81.91 | 73.53 | 0 | 0 | 0 | 100.0% | store default |
| smoke | arcadedb-remote | dense-filtered-50 | 0.4581 | 0.4813 | 0.4903 | 0.4811 | 0.9913 | 0.9729 | 73.49 | 75.93 | 84.70 | 73.31 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 166.68 | 187.72 | 199.58 | 167.57 | 0 | 0 | 0 | 100.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-10 | 0.0874 | 0.0869 | 0.0879 | 0.0995 | – | – | 166.62 | 187.83 | 198.73 | 166.51 | 0 | 0 | 0 | 51.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-50 | 0.4613 | 0.4845 | 0.4932 | 0.4845 | – | – | 165.71 | 186.41 | 197.74 | 165.72 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
