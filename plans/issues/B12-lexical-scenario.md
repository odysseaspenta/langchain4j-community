# B12 — `lexical` scenario

| | |
|---|---|
| Phase | 5 — Canonical |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B11 |
| PRD | §7.4 `lexical`, §8.1 labelling rule, R4 |

## Scope
- BM25-only retrieval via `SEARCH_INDEX('T[text]', q)` ordered by `$score DESC LIMIT k` — raw SQL through the target, **labelled "not via store API"** in results — switch to the store API if S7 adds a lexical-only path.
- Query text escaping for Lucene syntax in the harness (independent of S8) so parse errors don't dominate; count remaining failures (M4).
- Record full-text index config (analyzer, BM25 params — defaults on 26.7.2) and bucket count (R4: per-bucket IDF on 26.7.2).
- Tier: standard, embedded and remote (full tier → B22).

## Acceptance
- nDCG@10 in the plausible range for BM25 on NQ (BEIR BM25 NQ ≈ 0.33; StandardAnalyzer without stemming may differ) — document the number.
