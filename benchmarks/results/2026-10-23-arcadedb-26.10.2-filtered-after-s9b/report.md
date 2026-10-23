# RAG benchmark: filtered profile, ArcadeDB 26.10.2

| Run | |
|---|---|
| Started / finished | 2026-10-23T20:25:18.030541193Z / 2026-10-23T22:26:09.233286251Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 8.0 GiB |
| Git | 46a86dde4b6f0c6ec3181e55525f7139923e305b |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-embedded | smoke | 100,000 / 100,000 | 11.9 | 8,372.0 | 16.4 | 376 | 597 | 3,299 | – |
| arcadedb-remote | smoke | 100,000 / 100,000 | 35.4 | 2,823.8 | 23.1 | 351 | 89 | 6,744 | 8,781 |

arcadedb-remote (smoke): arcadedata/arcadedb:26.10.2, server CPUs 4-7, heap 8g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| smoke | arcadedb-embedded | dense | 0.8522 | 0.9411 | 0.9789 | 0.8330 | 0.9924 | 0.9833 | 3.34 | 4.00 | 4.59 | 3.74 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 13.94 | 15.35 | 17.90 | 13.97 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 22.57 | 24.81 | 28.45 | 22.66 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | dense-filtered-50 | 0.4588 | 0.4822 | 0.4912 | 0.4819 | 0.9919 | 0.9732 | 3.52 | 4.21 | 4.68 | 3.53 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 64.31 | 100.66 | 117.82 | 65.12 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-10 | 0.0869 | 0.0872 | 0.0879 | 0.0989 | – | – | 59.05 | 103.49 | 121.53 | 59.07 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-embedded | hybrid-filtered-50 | 0.4621 | 0.4854 | 0.4947 | 0.4853 | – | – | 36.11 | 55.06 | 65.30 | 36.02 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense | 0.8495 | 0.9382 | 0.9763 | 0.8303 | 0.9926 | 0.9833 | 19.34 | 22.66 | 26.83 | 19.92 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | 1.0000 | 1.0000 | 27.84 | 29.75 | 31.85 | 27.93 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-10 | 0.0878 | 0.0872 | 0.0879 | 0.0998 | 1.0000 | 1.0000 | 36.91 | 39.74 | 42.62 | 36.92 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | dense-filtered-50 | 0.4590 | 0.4825 | 0.4915 | 0.4820 | 0.9960 | 0.9901 | 49.34 | 52.53 | 56.00 | 49.39 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-1 | 0.0069 | 0.0063 | 0.0063 | 0.0087 | – | – | 44.04 | 63.95 | 74.03 | 44.43 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-10 | 0.0868 | 0.0872 | 0.0879 | 0.0988 | – | – | 53.67 | 73.63 | 84.05 | 53.66 | 0 | 0 | 0 | 0.0% | store default |
| smoke | arcadedb-remote | hybrid-filtered-50 | 0.4582 | 0.4827 | 0.4916 | 0.4807 | – | – | 66.49 | 86.88 | 97.42 | 66.45 | 0 | 0 | 0 | 0.0% | store default |

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
