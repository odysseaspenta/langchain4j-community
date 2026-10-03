# S12 — Embedded mode: an id can never be used again (upsert and re-add fail)

| | |
|---|---|
| Phase | 4 — found during S1 (2026-10-03); owner decision pending |
| Branch | `fix/arcadedb-embedded-id-reuse` (stacked on S1) when picked up |
| Depends on | — |

## Current behaviour
- Embedded `addAll` intends upsert semantics: `softDeleteById(id)` marks an existing record `deleted` and removes it from the vector index, then a new record with the same id is inserted. But the soft-deleted record **keeps its `id`**, and embedded mode creates a **unique** LSM index on `id` (`initEmbeddedSchema`), so the insert fails with `DuplicatedKeyException`.
- Verified on the unmodified store with ArcadeDB 26.9.1 (2026-10-03): adding an existing id fails, and **adding an id again after `removeAll(id)` fails** too — once used, an id can never be stored again in embedded mode. Same with batching (within one batch or across batches).
- Remote mode has no unique index: adding an existing id silently creates a second record with the same id (no upsert either; `removeAll(id)` deletes all of them). The two modes disagree.

## Proposed fix (to confirm)
- Embedded: when soft-deleting, also clear the record's `id` (or move it to another property), so the unique index no longer holds it; the soft-delete design (kept for HNSW connectivity) stays. Upsert and re-add then work, within and across batches.
- Remote: make `addAll` an upsert too (e.g. `DELETE FROM T WHERE doc_id = :idN` before each INSERT in the batch script), so both modes behave the same.
- ITs: re-add existing id (keeps the last version), repeated id within one batch and across batches, add after `removeAll(id)` — both modes.
