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

## Re-scope proposal after the hybrid investigation (2026-10-17, target ArcadeDB 26.10.2)
See `benchmarks/results/2026-10-17-arcadedb-26.10.2-hybrid-investigation/report.md`. Findings: the store's full-text source is unbounded (an NQ question matches ~86 % of the passages; matching plus the per-record `deleted` check is ~470 of 543 ms); 26.10.2 makes a score-ordered, bounded full-text source cheap; an EnglishAnalyzer index cuts matches to ~20k; equal-weight RRF costs ~0.06 nDCG@10 against dense-only.

Proposed scope:
1. **Bounded, score-ordered full-text source** (`ORDER BY $score DESC LIMIT <fetch>`; embedded: `deleted` filter on the bounded set) — default; 543 → ~130 ms with no accuracy change.
2. **Fusion options**: `RRF` with `k` and per-source weights, `LINEAR`, `DBSF` (needs the bounded source's `$score`). Measured best on NQ: RRF 8:1 towards dense (with 400 dense candidates) 0.853, LINEAR 0.844–0.836 vs equal RRF 0.794.
3. **Full-text index options** on creation: analyzer (e.g. EnglishAnalyzer) and BM25 `k1`/`b`; changing them requires an index rebuild. EnglishAnalyzer: hybrid ~21–25 ms, lexical 0.60 → 0.68.
4. Defaults (owner decision): compatible (RRF equal weights, default analyzer) vs measured-best (weighted RRF or LINEAR; EnglishAnalyzer for English text).

**Owner decision (2026-10-21): measured-best defaults.** Bounded, score-ordered full-text source; dense candidates as in dense search (`max(4k, k + 100)`), full-text candidates `2k`; RRF weighted 8:1 towards dense; `EnglishAnalyzer` for newly created full-text indexes (configurable; non-English text should choose another analyzer — to be flagged in the PR). Implementation on the fix stack, stacked on S12.

## Outcome (2026-10-21) — commit `35f2debc` on `feat/arcadedb-hybrid-options` (stacked on S12; local, no PR yet)
- Hybrid query: full-text source bounded to the top `2k` by `$score` (`10k` with a metadata filter), projecting `$score AS score`; embedded filters soft-deleted records after bounding; dense candidates `max(4k, k + 100)` (`10k` filtered); `vector.fuse(..., { fusion, k, weights, limit })`.
- New builder options (both builders): `hybridFusion` (`ArcadeDBHybridFusion.RRF | LINEAR | DBSF`), `hybridWeights(dense, fullText)`, `hybridRrfK`, `fullTextAnalyzer`, `fullTextBm25K1`, `fullTextBm25B`. Defaults (owner decision, measured-best): RRF, weights 8:1, k 60, `EnglishAnalyzer`; BM25 parameters unset (ArcadeDB defaults). Full-text index settings apply on creation; an existing full-text index is kept (remote now checks for it like the vector index; embedded creates it with `CREATE INDEX … FULL_TEXT METADATA {…}`).
- Tests: `ArcadeDBHybridSettingsTest` (options rendering, validation); ITs for each fusion (remote + embedded), custom analyzer/BM25 (remote), stemming with the default EnglishAnalyzer vs none with StandardAnalyzer (embedded, via `SEARCH_INDEX`), re-open with another analyzer keeps the index. Store suite 264 tests green on 26.10.2 and 26.9.1.
- **To flag in the PR:** the EnglishAnalyzer default is language-specific; non-English applications should set `fullTextAnalyzer`.
- End-to-end before/after on the benchmark: B11.
