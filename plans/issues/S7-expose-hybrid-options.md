# S7 — Expose hybrid / full-text options

| | |
|---|---|
| Phase | 5 — Canonical (pulled forward so `hybrid-tuned` exists before B16; README note 1) |
| Branch | `fix/arcadedb-hybrid-options` from `upstream/main` |
| Depends on | B10 |
| PRD | S7, D17 |

## Current behaviour
`buildHybridSearchQuery` (`:810-834`): RRF only with defaults; full-text subquery unbounded and without `$score`, so LINEAR/DBSF are impossible. FULL_TEXT index created without METADATA (`:455`, `:732`).

## Scope
- Options: fusion (RRF / DBSF / LINEAR), RRF `k`, per-source weights, dense fetch multiplier, bounded full-text source (`SELECT @rid, $score … ORDER BY $score DESC LIMIT N`).
- Full-text index options: analyzer (e.g. EnglishAnalyzer), `bm25_k1`, `bm25_b`, `defaultOperator`.
- Optionally a lexical-only search path so B12 no longer needs raw SQL.
- Defaults reproduce today's query exactly.

## Acceptance
- ITs per option; `hybrid-asis` results unchanged with defaults; enables B19 through the store API.
