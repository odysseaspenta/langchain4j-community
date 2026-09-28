# B19 — `hybrid-variants` sweep → freeze `hybrid-tuned`

| | |
|---|---|
| Phase | 5 — Canonical (pulled forward so `hybrid-tuned` exists before B16; README note 1) |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | S7, S8, B12 |
| PRD | D17, §7.4 `hybrid-variants` / `hybrid-tuned` |

## Scope
- One-factor-at-a-time from `hybrid-asis` on standard tier: (i) fusion RRF / DBSF / LINEAR; (ii) RRF k ∈ {10, 60}, weights dense:text ∈ {1:1, 2:1, 1:2}; (iii) bounded FT source; (iv) EnglishAnalyzer vs StandardAnalyzer (requires separate loads / FT index rebuild); (v) query sanitising on/off.
- Pick the best nDCG@10 variant, freeze it in config as `hybrid-tuned`, record the choice and rationale here.
- Decide which ArcadeDB version the tuning runs on (tuned config is then reused unchanged across versions).

## Acceptance
- Sweep results committed as a curated report; `hybrid-tuned` config frozen and used by B13/B16.
