# B06 — `BenchmarkTarget` interface + embedded `ArcadeDbTarget`

| | |
|---|---|
| Phase | 1 — Smoke path |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B01 |
| PRD | D7, §8.1, F15 (hooks), F18, F19, M5 |

## Scope
- `BenchmarkTarget` interface: `load(tier data)`, `search(request)`, `stats()`, `close()`. Must not reference ArcadeDB types, so another store can be added later.
- `ArcadeDbTarget` embedded mode wrapping the **unmodified** `ArcadeDBEmbeddingStore` via the `EmbeddingStore` API: `addAll` in client-side batches, `search` for `dense` and `hybrid-asis`.
- Pinned index config: `maxConnections=16`, `beamWidth=100`, `quantization=NONE`, `similarity=COSINE`; record the version's own defaults too.
- Suppress per-search INFO logging (`com.arcadedb.index.vector` → WARNING) (F18).
- Wait for graph build / rebuild to complete before queries and record time-to-searchable (F19). Needs a way to observe build state on 26.7.2 (e.g. poll index status or trigger `buildVectorGraphNow()`); document the mechanism per version.
- `stats()`: ingestion vectors/s, wall time, time-to-searchable, on-disk size, peak heap (M5).
- Load time cap: abort ingestion after a configurable duration and report partial count + extrapolated throughput (needed by B10, risk R1).
- Any raw-SQL path (none yet) must be labelled in results as "not via store API".

## Acceptance
- Loads the smoke tier and answers `dense` and `hybrid-asis` queries through the store API.
- Time cap stops ingestion cleanly and reports partial stats.
