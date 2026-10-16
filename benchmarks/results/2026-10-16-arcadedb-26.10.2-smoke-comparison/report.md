# Smoke tier: ArcadeDB 26.9.1 vs 26.10.2 (unmodified store)

Same store code (unmodified `ArcadeDBEmbeddingStore`, `7de83f5`), built with `-Darcadedb.version=26.10.2` (`-Denforcer.skip=true`, see below); same protocol as the 26.9.1 smoke baseline (PRD A5): smoke tier (100k passages, 3,452 NQ test queries), k = 100, cold pass + 3 timed repetitions, client pinned to CPUs 0–3, server container to 4–7, client and server heaps 8 GB, Java Vector API on for embedded, reference machine.

| Run | Directory | Commit |
|---|---|---|
| 26.9.1 embedded | `2026-10-01-arcadedb-26.9.1-baseline-smoke-embedded-vector-api/` | `4a69814d` (clean) |
| 26.9.1 remote | `2026-10-01-arcadedb-26.9.1-baseline-smoke/` (remote rows) | `8ed8251d` (clean) |
| 26.10.2 embedded + remote | `2026-10-16-arcadedb-26.10.2-baseline-smoke/` | `dc30005b` (code clean; marked dirty only by an untracked `log/` folder that `rag-bench info` created in the working directory) |

## Results

Latency in ms: median across repetitions; [min–max] of p50 across repetitions is the noise band.

| | 26.9.1 | 26.10.2 | Change |
|---|---|---|---|
| **Embedded** load | 2,762 docs/s | 3,137 docs/s | +14 % |
| Embedded time to searchable | 19.5 s | 16.5 s | −15 % |
| Vectors left unreachable by the graph build | 54 / 100,000 | 46 / 100,000 | |
| `dense` nDCG@10 / Recall@100 / ANN R@10 | 0.8531 / 0.9801 / 0.9928 | 0.8521 / 0.9789 / 0.9934 | unchanged |
| `dense` p50 [band] / p99 | 3.13 [3.11–3.24] / 4.46 | 3.34 [3.30–3.38] / 4.69 | +7 % (outside the band, +0.2 ms) |
| `hybrid-asis` nDCG@10 / Recall@100 | 0.7943 / 0.9892 | 0.7941 / 0.9894 | unchanged |
| `hybrid-asis` p50 [band] / p99 | 622 [621–622] / 776 | **544** [543–549] / **694** | **−13 % / −11 %** |
| **Remote** load (store's per-row inserts) | 634 docs/s | 576 docs/s | −9 % |
| Remote time to searchable | 53.1 s | 25.1 s | −53 % |
| Server peak memory (load) | 9,082 MiB | 8,874 MiB | |
| Remote `dense` | aborted (store bug S3) | aborted (store bug S3) | — |
| Remote `hybrid-asis` nDCG@10 | 0.7937 | 0.7935 | unchanged |
| Remote `hybrid-asis` p50 [band] / p99 | 530 [530–530] / 660 | **477** [476–479] / **607** | **−10 % / −8 %** |

## Other checks (2026-10-16)

- **Compatibility:** the store fix stack (S10, S3, S11, S2, S8, S1) passes all 251 store tests against 26.10.2 with a matching 26.10.2 server, no code changes.
- **Build:** the module does not build against 26.10.2 as is: the enforcer's upper-bound rule rejects ArcadeDB's `slf4j-api` 2.0.20 against the repository's managed 2.0.18 — a dependency-management bump (root `pom.xml`).
- **HTTP/2 30 s cut-off still present:** a remote request running longer than 30 s still fails at 30.1 s with `EOFException` on 26.10.2 (26.10.2's #9953 does not cover it).
- **Release notes, relevant fixes:** a graph-rebuilding query honours the JVM-wide rebuild limit (#7814 — our concurrent-rebuild OOM pattern), searches during an async rebuild no longer get poor neighbours (#8862), a vector `filter` that matched nothing was treated as no filter and returned records outside it (#8959), HNSW build cache sized from the heap ceiling (#7146), JVM-wide heap budget for query buffers (#8591), `SEARCH_INDEX` pushes `@rid` ranges and merges score-ordered branches (#9895, #9909, #10022), BM25 counters fixed (#10018), statement/plan cache by query shape, JVector 4.0.1, Lucene 10.5.2. Behaviour changes: query admission gate on by default (HTTP 503 when saturated), "already exists" answers 409, Docker image on Java 25.

## Verdict

26.10.2 is better than or equal to 26.9.1 everywhere it matters for this integration — same accuracy; hybrid 10–13 % faster; faster ingestion and much faster time-to-searchable; fewer unreachable vectors; fixes for vector-rebuild concurrency, memory and filter correctness — with two small costs: dense p50 +0.2 ms (+7 %) and an `slf4j` dependency bump. It does **not** fix the main 26.9.1 weakness: hybrid search remains ~5× slower than on 26.7.2 (S7), and the remote 30 s HTTP/2 cut-off remains.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
