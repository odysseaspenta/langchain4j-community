# S4 — Per-query `efSearch`

| | |
|---|---|
| Phase | 4 — Viability fixes |
| Branch | `fix/arcadedb-efsearch` from `upstream/main` (rebase on S3 for the remote part, or split embedded/remote) |
| Depends on | B10; S3 for remote |
| PRD | S4 |

## Current behaviour
Two-arg `findNeighborsFromVector(queryVector, fetchSize)` (`:542`) → on 26.7.2 adaptive beam `max(2k,20)` above 10k nodes → low recall. Fixed upstream in 26.9.1 (#6494). Remote never passes efSearch.

## Scope
- Builder option `efSearch` (default: current behaviour — no value passed).
- Embedded: `findNeighborsFromVector(vec, k, efSearch)`; remote: 4th arg / `{efSearch: N}` in the S3 query.
- Optionally allow per-request override (e.g. via a store-specific search method) so the benchmark can sweep efSearch without rebuilding the store; otherwise the benchmark builds one store instance per efSearch over the same DB.
- Also expose index-level `efSearch` METADATA only where the version honours it (26.8.1+) — or defer to S6.

## Acceptance
- IT asserting higher efSearch never lowers recall on a synthetic set; module ITs pass.
- Benchmark before/after: dense ANN recall@10 at standard, embedded + remote.
