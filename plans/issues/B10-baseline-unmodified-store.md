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
