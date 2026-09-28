# B16 — `canonical` profile + loaded-DB reuse

| | |
|---|---|
| Phase | 5 — Canonical |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B12, B13, B14, B19 |
| PRD | D19, F14 (canonical), F15, F16, acceptance 5 |

## Scope
- Profile (for now): standard tier embedded **and** remote — `dense` (with sweep), `lexical`, `hybrid-asis`, `hybrid-tuned`, `filtered` (embedded). 3 repetitions.
- The PRD's full-tier part (embedded `dense` single efSearch + `hybrid-tuned`) is added in B22; keep the profile definition data-driven so that is a config change.
- Load once per (tier, mode, version, index config); optional on-disk DB cache between runs keyed by that tuple + store commit (F15). Cache invalidation must be conservative.
- Progress / ETA logging; resumable at scenario granularity (a crash does not force a reload + rerun of finished scenarios).
- Record actual wall time per version; the ≤ ~8 h budget (acceptance 5) is re-checked once B22 adds the full tier.

## Acceptance
- Full canonical run on 26.7.2 completes with a valid JSON; duration recorded.
