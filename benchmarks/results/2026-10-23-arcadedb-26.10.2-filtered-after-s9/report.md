# RAG benchmark: filtered profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-10-23T16:35:30.986225204Z / 2026-10-23T20:13:32.200608267Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 05f1d074c9cc2cc56b19e25cf0c8b0cf045877e7 |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 11.9 | 8,390.5 | 16.1 | 369 | 601 | 5,891 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 35.8 | 2,792.6 | 23.7 | 355 | 89 | 8,663 | 8,787 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8523 | 0.9410 | 0.9788 | 0.8333 | 0.9922 | 0.9833 | 3.34 | 3.99 | 4.40 | 3.52 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 78.22 | 81.13 | 83.20 | 78.40 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 89.76 | 93.28 | 95.89 | 89.69 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-50 | 0.4584 | 0.4818 | 0.4908 | 0.4816 | 0.9918 | 0.9732 | 3.51 | 4.18 | 4.69 | 3.57 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 165.51 | 242.97 | 277.59 | 166.34 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-10 | 0.0875 | 0.0872 | 0.0879 | 0.0996 | – | – | 133.52 | 241.26 | 278.36 | 134.09 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-50 | 0.4615 | 0.4848 | 0.4941 | 0.4847 | – | – | 35.62 | 54.11 | 65.20 | 35.59 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8514 | 0.9405 | 0.9787 | 0.8322 | 0.9931 | 0.9832 | 19.09 | 22.41 | 26.43 | 19.86 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 27.76 | 29.29 | 32.05 | 27.82 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 36.75 | 39.66 | 42.59 | 36.72 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-50 | 0.4599 | 0.4835 | 0.4926 | 0.4830 | 0.9963 | 0.9902 | 49.17 | 52.41 | 56.03 | 49.23 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 81.99 | 145.22 | 172.20 | 82.22 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-10 | 0.0876 | 0.0872 | 0.0879 | 0.0997 | – | – | 92.93 | 156.95 | 184.59 | 92.56 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-50 | 0.4596 | 0.4846 | 0.4928 | 0.4821 | – | – | 109.04 | 177.85 | 208.67 | 109.08 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
