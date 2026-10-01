# RAG benchmark: baseline profile, ArcadeDB 26.7.2

| Run | |
|---|---|
| Started / finished | 2026-10-01T06:49:46.356160135Z / 2026-10-01T10:38:01.429536003Z |
| Machine | Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz, 4 cores, 47.0 GiB RAM |
| JVM | 21.0.12.1 (OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu), max heap 4.0 GiB |
| Git | 42d716e594dabc690f1b6e61c2a7566431e80d77 (dirty) |
| Dataset | nq, seed 42, k = 100, 3 repetition(s) |
| Embeddings | bge-small-en-v1.5, query embedding p50 5.44 ms (not included below) |

## Load

| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB | Peak heap MiB (incl. garbage) | Server peak MiB |
|---|---|---|---|---|---|---|---|---|---|
| arcadedb-remote | standard | 1,000,000 / 1,000,000 | 1,805.3 | 553.9 | 907.0 | 3,804 | 148 | 3,177 | 29,999 |

arcadedb-remote (standard): arcadedata/arcadedb:26.7.2, server CPUs 4-7, heap 24g, client CPUs 0-3, query timeout 25000 ms. Time to searchable = until a probe query succeeds and the server is idle (embedded: forced graph build).

## Scenarios

| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100 | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Skipped | Short | efSearch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| standard | arcadedb-remote | dense ⚠ aborted | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 30,034.50 | 35,608.15 | 35,608.15 | 30,034.50 | 0 | 10 | 3442 | 100.0% | store default |
| standard | arcadedb-remote | hybrid-asis | 0.3222 | 0.6159 | 0.9062 | 0.2417 | – | – | 777.28 | 1,144.19 | 1,274.87 | 776.92 | 0 | 2 | 0 | 0.1% | store default |

⚠ arcadedb-remote / dense aborted: 10 consecutive failed or empty queries. Skipped queries count as empty rankings; latency covers the queries run.

Latency is the median across repetitions of each repetition's percentile; the cold pass runs first and doubles as warm-up. Full details: `result.json`.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
