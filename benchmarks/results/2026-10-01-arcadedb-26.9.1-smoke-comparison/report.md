# Smoke tier: ArcadeDB 26.7.2 vs 26.9.1, and the Java Vector API (unmodified store)

Same store code (`ArcadeDBEmbeddingStore` at `7de83f5`, unmodified), built with `-Darcadedb.version=26.9.1` (it compiles and the benchmark's tests pass without changes). Same protocol as the B10 baseline: smoke tier (100k passages, 3,452 NQ test queries), k = 100, cold pass + 3 timed repetitions, client pinned to CPUs 0–3, server container to 4–7, reference machine.

| Run | Directory | Commit |
|---|---|---|
| 26.7.2 (B10 baseline, smoke rows) | `2026-09-30-arcadedb-26.7.2-baseline-embedded/`, `2026-10-01-arcadedb-26.7.2-baseline-remote/` | `6f053a75` |
| 26.9.1, embedded + remote | `2026-10-01-arcadedb-26.9.1-baseline-smoke/` | `8ed8251d` (clean) |
| 26.9.1, embedded, `--add-modules jdk.incubator.vector` | `2026-10-01-arcadedb-26.9.1-baseline-smoke-embedded-vector-api/` | `4a69814d` (clean) |

## Results

Latency in ms (median over repetitions; repetitions agree within ~1 %).

| | 26.7.2 | 26.9.1 | 26.9.1 + Vector API |
|---|---|---|---|
| **Embedded** load (docs/s) | 1,736 | 2,760 | 2,762 |
| Embedded time to searchable (s) | 54 | 39 | **19.5** |
| `dense` nDCG@10 / ANN recall@10 | 0.853 / 0.992 | 0.854 / 0.992 | 0.853 / 0.993 |
| `dense` p50 / p99 | 28.3 / 38.4 | 5.7 / 7.8 | **3.1 / 4.5** |
| `hybrid-asis` nDCG@10 / Recall@100 | 0.453 / 0.968 | **0.794 / 0.989** | 0.794 / 0.989 |
| `hybrid-asis` p50 / p99 | **108 / 176** | 630 / 780 | 622 / 776 |
| **Remote** load (docs/s) | 529 | 634 | – |
| Remote time to searchable (s) | 92 | 53 | – |
| Remote `dense` | aborted (every query ~30 s, empty) | aborted | – |
| Remote `hybrid-asis` nDCG@10, p50 | 0.454, 180 | **0.794**, 530 | – |

## Observations

1. **Upgrading ArcadeDB alone fixes hybrid accuracy:** nDCG@10 0.45 → 0.79 (dense-only: 0.85), embedded and remote alike, with no store change. Cause not yet established (candidates: 26.8.1 full-text changes such as BM25 statistics comparable across buckets; the fusion now receiving scored full-text results). This changes the premise of S7.
2. **…but hybrid gets ~6× slower** (p50 108 → 630 ms embedded, 180 → 530 ms remote). The Vector API does not help, so the cost is on the full-text/fusion side, not vector distances.
3. **Dense search is ~5× faster at the same recall** (p50 28 → 5.7 ms), consistent with 26.9.1's search-depth fix (#6494: 26.7.2 searched with `max(2k, 20)` above 10k nodes).
4. **The Java Vector API roughly halves vector work** in embedded mode: graph build 39 → 19.5 s, dense p50 5.7 → 3.1 ms. The `rag-bench` launcher did not enable it before (`RAG_BENCH_JAVA_OPTS` now can), while the ArcadeDB server image always does — so embedded numbers so far (B10 included) are without SIMD and not like-for-like with remote.
5. **Remote `dense` is still broken** — the store's own query (S3), independent of the ArcadeDB version.
6. **26.9.1 warns that its graph build leaves vectors unreachable** (57 and 54 of 100,000 in two builds), "serving them from the delta scan"; ANN recall is unaffected at this size.
7. The store pins `maxConnections` = 16; 26.9.1's own default is 32 (26.7.2: 16).

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
