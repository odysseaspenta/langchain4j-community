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

## Status (2026-10-16) — not fixed; re-verified on the fix stack (S10…S1 + 26.10.2 bump) against ArcadeDB 26.10.2

| Step | Embedded | Remote |
|---|---|---|
| add `x`=v1 | OK | OK |
| add `x`=v2 again | **`DuplicatedKeyException`** — still holds v1 | OK — **two records** `x`=v1, `x`=v2 |
| `removeAll(x)`, then add `x`=v3 | removal OK, **re-add fails** (`DuplicatedKeyException`) | OK |
| `addAll([y, y])` in one call | **whole call fails** (with S1 the batch is one transaction, so neither `y` is stored; before S1 the first `y` was stored, then the second failed) | OK — two records `y` |

- Cause unchanged: the soft-deleted embedded record keeps its `id`, and the unique LSM index on `id` rejects the new record. Remote mode has no unique index and does plain inserts.
- Decision needed: (1) the intended semantics of `addAll` with an id that already exists — **upsert (replace)** is what the embedded code attempts and what the proposed fix implements in both modes; the alternative is to reject duplicates consistently in both modes; (2) whether remote mode should change from "duplicate records" to upsert (behaviour change for remote users, but duplicates under one id are almost certainly unintended — `removeAll(id)` already treats the id as one key).

## Outcome (2026-10-16) — commit `f9e93e07` on `fix/arcadedb-embedded-id-reuse` (stacked on the 26.10.2 bump; local, no PR yet)
- Owner decision: **upsert by id in both modes**.
- Embedded: soft delete (by id and by filter, one helper) marks the record deleted, removes it from the vector index **and removes its `id`**, so the unique id index releases it; a record soft-deleted before this fix releases its id the first time the id is reused (without decrementing the active count twice).
- Remote: each insert in the batch script is preceded by `DELETE FROM <type> WHERE doc_id = :idN`; schema init adds a **non-unique** index on `doc_id` (non-unique so it can be created on databases that already hold duplicates).
- Within one `addAll`, the last occurrence of a repeated id wins, within and across batches.
- ITs (shared helper, both modes, `batchSize(3)`): replace an existing id, add again after `removeAll(id)`, repeated ids within and across batches — all four fail on the previous code (embedded `DuplicatedKeyException`, remote duplicate records). Store suite 255 tests green on 26.10.2 and 26.9.1.
- Cost: remote `addAll` ~4,630 → ~4,200 embeddings/s (−10 %; 20k rows, 26.10.2, client CPUs 0–3, server 4–7).
