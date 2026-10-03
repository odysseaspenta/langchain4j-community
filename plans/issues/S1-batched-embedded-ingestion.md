# S1 — Batched embedded ingestion (+ deferred graph build)

| | |
|---|---|
| Phase | 4 — Viability fixes |
| Branch | `fix/arcadedb-batched-embedded-ingestion` from `upstream/main` (store module only) |
| Depends on | B10 (baseline recorded) |
| PRD | S1, R1, R3, R7 |

## Current behaviour
`addAllEmbedded` (`ArcadeDBEmbeddingStore.java:488-523`) runs one `transaction()` per vector plus a `softDeleteById` lookup (`:792-806`) per vector.

## Scope
- Insert in chunked transactions, configurable batch size (builder option, sensible default, e.g. 1,000–10,000).
- Preserve upsert-by-id semantics: indexed id lookup per batch or a pre-pass, not one query per vector outside the tx.
- Opt-in deferred HNSW build for bulk loads (`buildGraphNow:false` on CREATE INDEX and/or `buildVectorGraphNow()` after load).
- Public API and default behaviour unchanged; new options opt-in.
- Tests in `ArcadeDBEmbeddingStoreEmbeddedIT` (+ removal IT): upsert of existing ids within and across batches, batch boundary cases, deferred build then search.

## Acceptance
- Module ITs pass on 26.7.2.
- Benchmark before/after: embedded ingestion vectors/s and time-to-searchable at smoke and standard.

## Outcome (2026-10-03) — commit `7d70c9f0` on `fix/arcadedb-batched-embedded-ingestion` (stacked on S8; local, no PR yet)
- Embedded `addAll` inserts each batch in one transaction; new embedded builder option `batchSize` (default 1000). Per-embedding behaviour (id lookup + soft delete + insert) unchanged.
- Measured against 26.9.1 (20,000 random 384-d embeddings with text and metadata, `addAll` in chunks of 1,000, Vector API on): `batchSize(1)` (= old behaviour) ~2,430 embeddings/s; `batchSize(1000)` ~11,900/s (**~4.9×**); 10,000 no better.
- **Deferred graph build dropped from scope:** on 26.9.1 the graph is built lazily at the first search (2.0 s at 20k either way) and inserts carry no per-insert graph cost, so an opt-in deferred build adds nothing on the target version (on 26.7.2 the background rebuild-every-100-mutations behaviour differs).
- New IT: 10 embeddings in batches of 3 with differing metadata keys. Store suite 251 tests green on 26.9.1 and 26.7.2.
- Found while testing upserts: **S12** (an embedded id can never be re-used).
