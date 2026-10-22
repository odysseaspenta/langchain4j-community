# Remote dense recall and remote query overhead — ArcadeDB 26.10.2 (S4, B11 findings)

Follow-up to the two open findings of B11 (`2026-10-21-arcadedb-26.10.2-fixes-before-after`):
remote dense ANN recall@10 0.970 vs 0.992 embedded, and remote per-query overhead (dense +27 ms, hybrid +106 ms).

**Setup.** Copies of the kept smoke-tier databases of the B11 "after" run (100k NQ passages, bge-small-en-v1.5).
Probe: `benchmarks/langchain4j-community-rag-benchmark/tools/DenseProbe.java`; all 3,452 test queries; ANN recall@10
against the harness's exact ground truth (`groundtruth/.../k100/smoke`). Probe client on CPUs 0–3; for remote, an
ArcadeDB 26.10.2 container on CPUs 4–7 with an 8 GB heap, as in the benchmark.

## 1. Remote dense recall: candidate count, not the graph

The remote store asked `vector.neighbors` for exactly `k` neighbours; embedded asks `findNeighborsFromVector` for
`max(4k, k + 100)` = 400. With ArcadeDB's default beam (≈ 2× the neighbours asked for), fewer neighbours means a
narrower search.

| Variant (k = 100) | Embedded DB | Server-built DB, opened embedded |
|---|---|---|
| fetch 100 (remote store before) | 0.9701 | 0.9702 |
| fetch 400 (embedded store) | 0.9919 | 0.9924 |
| fetch 100, efSearch 100 | 0.9450 | 0.9462 |
| fetch 100, efSearch 200 | 0.9701 | 0.9702 |
| fetch 100, efSearch 400 | 0.9842 | 0.9848 |
| fetch 100, efSearch 800 | 0.9919 | 0.9924 |

- Both graphs are equivalent; the server's background graph build is not the cause.
- The default beam for `k = 100` is exactly efSearch 200.
- Fetching 100 with efSearch 800 equals fetching 400 in recall, is slightly faster embedded (3.0 vs 3.4 ms), and
  returns a quarter of the rows — what matters over HTTP.

## 2. Remote overhead: result rows, sent twice

| Remote variant | mean | p50 | ANN recall@10 |
|---|---|---|---|
| Dense, store query, fetch 100 (before) | 31.2 ms | 30.3 ms | 0.9702 |
| Dense, `doc_id` only, fetch 100 | 3.6 ms | 3.6 ms | 0.9702 |
| Dense, store query, fetch 400 | 113.0 ms | 112.5 ms | 0.9924 |
| Dense, store query, fetch 100, efSearch 800 | 32.3 ms | 32.2 ms | 0.9924 |
| **Dense, fetch 100, efSearch 800, without nested record** | **19.4 ms** | **19.0 ms** | **0.9924** |
| Hybrid, store query, fuse limit 400 (before) | 131.3 ms | 128.6 ms | – |
| Hybrid, `doc_id` only, fuse limit 400 | 26.0 ms | 23.3 ms | – |
| Hybrid, store query, fuse limit 100 | 50.9 ms | 48.2 ms | – |
| **Hybrid, fuse limit 100, without nested record** | **37.1 ms** | **34.4 ms** | – |

- The ANN search and fusion are cheap (dense 3.6 ms, hybrid ~24 ms with only ids returned); the cost is the rows.
- **Each row is sent twice.** Rows of `expand(vector.neighbors(...))` and `expand(vector.fuse(...))` carry the
  record's properties and, again, the whole record under `record`: ~11 KB per NQ passage row (embedding as JSON
  ≈ 5.1 KB, text ≈ 0.7 KB, each twice). `SELECT *, !record FROM (...)` drops the copy and keeps `distance` /
  `score`, the metadata properties and the order: 1.10 → 0.55 MB per dense query.
- **Hybrid returned all fused candidates** (`limit` = 400) although the store keeps `k`.
- Over curl the full 1.1 MB dense response takes 13.4 ms; through the Java `RemoteDatabase` 31 ms. Most of what
  remains after the fixes (~16 ms for 100 rows) is client-side JSON parsing of the embeddings, which only the
  ArcadeDB client can improve (or not returning embeddings, which `EmbeddingMatch` carries).

## Fix (S4, `e4df5e74`)

- Builder option `efSearch` (both modes; also the dense side of hybrid when set). Default: embedded unchanged;
  remote fetches `k` with a beam of `2 × max(4k, k + 100)`, equivalent to embedded search.
- Remote dense and hybrid queries project `SELECT *, !record`.
- `vector.fuse` returns `k` rows (`10k` with a metadata filter).

End-to-end effect: smoke run `2026-10-22-arcadedb-26.10.2-baseline-smoke-s4` (see the S4 before/after section in
`plans/issues/S4-per-query-efsearch.md` and the B11 report).
