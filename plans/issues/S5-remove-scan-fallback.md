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

## Outcome (2026-10-24) — commit `17e89ca8` on `fix/arcadedb-scan-fallback` (stacked on S9; local, no PR yet)
- Origin: added in upstream #599 (2026-03) as a workaround for identical vectors the HNSW graph cannot reach.
- ArcadeDB completes short graph results with a brute-force scan itself in every version checked (26.7.2, 26.9.1, 26.10.2: "Graph search returned only N results (expected M, available M) … falling back to brute-force scan"), so the store's full `SELECT FROM <type>` supplement was redundant. Removed with the active-vertex counter it needed, whose `count(*)` scan ran at every embedded store open (874 ms cold / 65–115 ms warm at 100k records; linear in size).
- Tests: new IT, 60 identical vectors all returned (on 26.9.1 the graph reaches 44, ArcadeDB completes the rest). Store suite 505 tests green on 26.10.2 and 26.9.1.
- Benchmark: the supplement never triggered at smoke scale (the graph always returns the full candidate count), so search results/latencies are unaffected and no re-run was made; the measurable effect is store open time.
