# B11 — Integrate S1–S4 and validate the pipeline

| | |
|---|---|
| Phase | 4 — Viability fixes |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | S1, S2, S3, S4, B09 |
| PRD | acceptance 3, 7 (acceptance 2 → B22) |

## Scope
- Merge / cherry-pick S1–S4 branches into the benchmark branch.
- Use the new builder options in `ArcadeDbTarget` (batch size, deferred build, efSearch).
- Record before/after for each of S1–S4 vs B10 (acceptance 7).
- Acceptance 3: at max efSearch, dense ANN recall@10 ≥ 0.95 on `standard`.
- Sanity-check dense nDCG@10 on `standard` (expected somewhat *above* the MTEB full-corpus score, since there are fewer distractors). The formal acceptance 2 check needs the full corpus and is done in B22.

## Acceptance
- Acceptance 3 passes (or discrepancies investigated and documented).
- Before/after table for S1–S4 committed as a curated report.

## Outcome (2026-10-21)
- Fix stack (S10, S3, S11, S2, S8, S1, 26.10.2, S12, S7) merged as `7dd065c8`; line-break workaround removed. Smoke before/after on 26.10.2 (A5: smoke only, so acceptance 3 on `standard` is not run): `benchmarks/results/2026-10-21-arcadedb-26.10.2-fixes-before-after/report.md`.
- Embedded: load ×2.7, hybrid 544 → 22 ms and nDCG@10 0.794 → 0.856, dense unchanged. Remote: load ×4.9, dense works (was aborted), hybrid 477 → 128 ms and 0.794 → 0.853.
- Open: remote dense ANN recall@10 0.970 vs 0.992 embedded; remote per-query overhead (dense +27 ms, hybrid +106 ms). S4 not yet done.
