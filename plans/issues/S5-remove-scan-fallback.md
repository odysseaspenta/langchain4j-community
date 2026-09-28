# S5 — Remove O(N) scan fallback at scale

| | |
|---|---|
| Phase | 7 — Extended |
| Branch | `fix/arcadedb-bounded-supplement` from `upstream/main` |
| Depends on | B10 |
| PRD | S5 |

## Current behaviour
When HNSW returns fewer than `fetchSize` results, `supplementFromMissedVertices` (`:568-578`, `:645-678`) runs a full `SELECT FROM T` scan.

## Scope
- Bound it (cap scanned rows), gate it by type size, or disable it on versions where the underlying HNSW issue is fixed. Understand first why it was added (git blame / upstream PR) and keep its test passing.
- Builder option if behaviour change is not safe as default.

## Acceptance
- Module ITs pass; benchmark before/after on queries that trigger the fallback (filtered 1% is a likely trigger — measure p99 there).
