# B10 — Baseline run on the unmodified store

| | |
|---|---|
| Phase | 3 — Baseline |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B07, B08, B09 |
| PRD | D8, §6 preamble, R1, R3, acceptance 7 |

## Scope
- Store at `7de83f5` (no S-fixes), ArcadeDB 26.7.2.
- `smoke` tier fully: embedded + remote, `dense` (store default efSearch) + `hybrid-asis`.
- `standard` tier with a recorded load time cap (embedded + remote); if the cap is hit, record partial count and extrapolated throughput and mark the baseline as partial.
- Record time-to-searchable and rebuild behaviour (R3).
- Commit a curated baseline report (`benchmarks/results/.../report.md`) on the benchmark branch.

## Acceptance
- JSON results for all of the above; curated baseline report committed.
- This is the "before" referenced by every S1–S9 before/after.

## Attempt 1 (2026-09-30, aborted) — `run --profile baseline`, client heap 14 GB, server heap 10 GB
- Smoke embedded, smoke remote and standard embedded completed (standard embedded: load 1M in 595.7 s = **1,679 docs/s**, flat vs 100k — no stall from 26.7.2's rebuilds; graph build **728 s**; hybrid ≈ 0.8–0.9 s/query at 1M, estimated from the timeline).
- **Standard remote failed — finding:** after loading 1M vectors (≈ 540 docs/s), the ArcadeDB 26.7.2 server ran out of its **10 GB heap** in `LSMVectorIndex.rebuildGraphBeforeSearch` (21:41–21:43 UTC), then failed to persist the graph (`ConcurrentModificationException … Failed to persist graph … nodes=1000000`). The readiness probe then hung on a query that never returned. Embedded mode built the same graph inside a 14 GB client heap. Logs: `/sysnet/rag-bench/b10-attempt1.log`, `/sysnet/rag-bench/b10-attempt1-server.log`.
- **Harness bugs, fixed the same day:** results were only written at the very end and one failed load aborted the run, so all measurements were lost; a probe attempt could block for 6 h. Now: `result.json` / `report.md` are rewritten after every load (`status: running`), a failed load is recorded (`error`) and the run continues (`status: incomplete`, exit code 1), probe attempts time out after ≤ 10 min within the readiness deadline, and a server `OutOfMemoryError` in the server log fails the load immediately.
- **Rerun plan:** two invocations so each side gets the memory it needs on the 46 GB machine — `--mode embedded --label embedded` (client heap 14 GB) and `--mode remote --label remote` (client heap 4 GB, server heap 24 GB); the curated baseline report combines both result directories.
