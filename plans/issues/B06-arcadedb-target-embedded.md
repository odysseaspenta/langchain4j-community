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

## Outcome (2026-09-29) — code complete, smoke-tier load deferred
- `targets` package (store-neutral): `BenchmarkTarget` (`load`, `search`, `capabilities`, `describe`, `close`), `Document`, `DocumentSource`, `SearchRequest` (mode DENSE/HYBRID, vector, text, k, `bucketsBelow`, efSearch), `SearchResult` (failures returned, not thrown), `LoadOptions` (batch size, time cap), `LoadStats` (M5).
- `runner.TierDocumentSource`: tier load order → corpus text (`PassageReader`) + cached vector + `bucket` metadata.
- `targets.arcadedb.ArcadeDbTarget.embedded(dir, ArcadeDbSettings.pinned(dim))`: fresh database per load, unmodified store via `embeddedBuilder().database(db)`, `addAll` in client batches, all searches through `EmbeddingStore.search` (hybrid = `EmbeddingSearchRequest.query(text)`, filter = `metadataKey("bucket").isLessThan(t)`). No raw SQL. `capabilities()` reports `efSearch=false` (unmodified store).
- **Time-to-searchable (F19), 26.7.2 mechanism:** after loading, `LSMVectorIndex.buildVectorGraphNow()` forces a full synchronous graph build (retrying on `NeedRetryException` while a background rebuild holds the index); its duration is time-to-searchable. Re-check the API when compiling against newer versions (S10).
- **Recorded config:** pinned settings, the created index's effective `LSMVectorIndexMetadata` (public fields, read reflectively) and the version's own defaults (a fresh `LSMVectorIndexMetadata`), so version comparisons show default changes (e.g. maxConnections 16 → 32 in 26.8.1).
- **Logging (F18):** ArcadeDB configures java.util.logging on first use and resets levels, so `com.arcadedb.index.vector` is set to WARNING after the database opens (and again after the build). Verified: the per-search INFO line no longer appears.
- **Guard:** the store searches only the first vector sub-index; the target fails if the type has more than one (default `arcadedb.typeDefaultBuckets` = 1, so fine unless reconfigured).
- Time cap: checked after each batch, so a capped load always stores ≥ 1 batch; reports partial count and extrapolated time.
- Tests (real embedded ArcadeDB, 600 fake 8-d docs): load stats, dense ANN recall@10 ≥ 0.9 vs ground truth, hybrid and filtered search, time cap, failures returned.
- **Real spot check (dev box, 2,000 NQ passages, 50 queries, 26.7.2, unmodified store):** load 576 docs/s, graph build 1.0 s, 59 MB on disk, peak heap 394 MB; dense p50 3.2 ms / p99 33 ms; hybrid p50 12 ms / p99 123 ms, 0 failures, 0 empty.
- **Early finding for B10/S7:** for q0 ("what is non controlling interest on balance sheet") dense returns the two judged-relevant passages (doc0, doc1) at ranks 1–2, but hybrid-asis ranks neither in its top 3 — consistent with the full-text source being unbounded and unscored, so RRF ranks are arbitrary. To be quantified by `hybrid-asis` nDCG@10.

### Deferred to the reference machine
- [x] Acceptance: load the full smoke tier (100k) through the target and answer `dense` + `hybrid-asis` — 2026-09-29 smoke run (see B07).
- [ ] Observe 26.7.2's rebuild-every-100-mutations behaviour at scale during load (R3).
