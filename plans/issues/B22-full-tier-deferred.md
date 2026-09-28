# B22 — Full-tier runs (deferred)

| | |
|---|---|
| Phase | 8 — Full tier (after everything else is complete) |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | all other issues, in particular B17 and B20 |
| PRD | D5 (`full`), F7, F10, F14, acceptance 2, 5 |

## Why deferred
Owner decision 2026-09-28: run only `smoke` and `standard` until everything else is done; the `full` tier (2.68M) is the most time-consuming part (embedding, ground truth, ingestion, canonical wall time).

## Scope
- Extend the embedding cache from `standard` to `full` (append remaining ~1.68M passages; B03 guarantees no re-embedding).
- Full-tier ground truth.
- Acceptance 2: dense nDCG@10 on `full` at high efSearch within ±1.5 pts of the published MTEB `bge-small-en-v1.5` NQ score — look up the exact reference score for the model revision used (PRD §12) and record it here.
- Add the full-tier part to `canonical` (embedded `dense` single efSearch + `hybrid-tuned`) and `lexical` on full; re-check acceptance 5 (≤ ~8 h per version).
- Add full tier remote to `extended`.
- Re-run the version comparison (B17) with the complete `canonical` and update the curated report.

## Acceptance
- Acceptance 2 and 5 met for the full PRD `canonical`; updated comparison report committed.
