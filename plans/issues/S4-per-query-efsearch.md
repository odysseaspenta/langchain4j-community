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

## Outcome (2026-10-22) — commit `e4df5e74` on `fix/arcadedb-efsearch` (stacked on S7; local, no PR yet)
- Investigation of the two B11 remote findings: `benchmarks/results/2026-10-22-arcadedb-26.10.2-dense-recall-and-remote-overhead/report.md` (probe `tools/DenseProbe.java`).
- Builder option `efSearch` (both modes; dense side of hybrid when set). Default: embedded unchanged (ArcadeDB's beam for `max(4k, k + 100)`); remote fetches `k` with `{ efSearch: 2 × max(4k, k + 100) }` (same recall as embedded, a quarter of the rows). Index-level efSearch METADATA deferred to S6.
- Also fixed (found in the investigation): remote rows were sent twice (nested `record`) → `SELECT *, !record FROM (…)`; `vector.fuse` limit = `k` (`10k` filtered) instead of all candidates.
- Tests: `ArcadeDBEfSearchTestHelper` (synthetic 3,000 × 32-d, recall@10 vs exact never drops as efSearch grows, ≥ 0.9) for both modes; validation. Store suite 268 tests green on 26.10.2 and 26.9.1.
- Smoke (`2026-10-22-arcadedb-26.10.2-baseline-smoke-s4`): remote dense ANN recall@10 0.970 → 0.993, p50 29.9 → 19.2 ms; remote hybrid p50 127.9 → 34.7 ms; embedded unchanged. Acceptance's `standard`-tier check not run (A5).
