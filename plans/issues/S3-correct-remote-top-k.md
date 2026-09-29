# S3 — Correct remote top-k

| | |
|---|---|
| Phase | 4 — Viability fixes |
| Branch | `fix/arcadedb-remote-top-k` from `upstream/main` |
| Depends on | B10 |
| PRD | S3 |

## Current behaviour
`searchRemote` (`:361-415`) issues `SELECT *, vector.neighbors(...) AS neighbors FROM T`: a full type scan running one ANN search per row, server-limited to 20,000 rows; the loop takes the first `maxResults` rows in scan order. Confirmed experimentally (handoff §6.2). Filtered results are also wrong.

**Measured in B08 (2026-09-29, 26.7.2, server 4 CPUs):** with only 5,000 random 384-d vectors a remote `dense` query (k = 10 or 100) does not finish within 30 s, or exhausts a 2 GB server heap (`OutOfMemoryError`). With k = 1 it takes ~25 s. A client-side timeout leaves the query running on the server, slowing the next ones; the benchmark therefore sets `arcadedb.command.timeout` on the database and aborts a scenario after a streak of failed/empty queries. Expect the smoke baseline (B10) to show remote `dense` as aborted with nDCG@10 ≈ 0. Hybrid remote at 5,000 vectors: 50–100 ms.

## Scope
- `SELECT expand(vector.neighbors('<idx>', ?, ?))` with parameters; score from the returned `distance` (`1 − d/2` for COSINE, as today).
- Keep the existing Java post-filter for metadata filters (S9 replaces it) but make it operate on true neighbours with over-fetch.
- Hybrid remote path checked for the same pattern.
- IT that fails on the current code: insert vectors whose true nearest neighbour is not first in scan order and assert it is returned.

## Acceptance
- New IT red before / green after; module ITs pass on 26.7.2.
- Benchmark before/after: remote `dense` nDCG@10 and ANN recall at smoke.
