# B15 — `compare` command

| | |
|---|---|
| Phase | 5 — Canonical |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B07 (JSON schema); can be developed against smoke results |
| PRD | D15, D18, F22, F23, acceptance 6, N2, N6 |

## Scope
- Input ≥2 result JSONs → Markdown report: delta tables per scenario/metric, dense vs lexical vs hybrid quality tables, recall-vs-latency SVG charts (hand-written SVG, no browser/JS), ingestion table.
- Noise bands: a latency delta smaller than the repetition spread → "no significant change".
- Warn-only thresholds (defaults: recall −1 pt, nDCG@10 −1 pt, p95 +10%, ingestion +10%), configurable; never fail the process.
- Flag cross-machine comparisons (N2) and differing dataset/embedding/GT manifests.
- Citation footer for BEIR/NQ (N6).

## Acceptance
- Report generated from two smoke results; later verified on two canonical results (acceptance 6, in B17).
