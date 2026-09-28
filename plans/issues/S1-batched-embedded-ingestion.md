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
