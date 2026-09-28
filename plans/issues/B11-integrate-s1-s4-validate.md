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
