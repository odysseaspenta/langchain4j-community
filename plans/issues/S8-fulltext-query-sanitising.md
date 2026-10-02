# S8 — Full-text query sanitising + dense fallback

| | |
|---|---|
| Phase | 5 — Canonical (pulled forward so `hybrid-tuned` exists before B16; README note 1) |
| Branch | `fix/arcadedb-fulltext-sanitising` from `upstream/main` |
| Depends on | B10 |
| PRD | S8 |

## Current behaviour
Only SQL quotes escaped (`escapeString :997`); Lucene syntax chars (`: ( ) " - + * ? ~ ^` …) reach `QueryParser`; parse errors are caught and return empty results (`:390-393`, `:637-639`).

## Scope
- Escape Lucene special characters (option to disable for callers who pass Lucene syntax deliberately).
- On parse failure fall back to dense-only instead of empty.

## Acceptance
- IT with queries containing each special char; benchmark before/after = `hybrid-asis` failure rate (M4) and nDCG@10.

## Outcome (2026-10-02) — commit `c4b49d25` on `fix/arcadedb-fulltext-query-sanitising` (stacked on S2; local, no PR yet)
- Hybrid query text is escaped before it reaches the full-text index (remote and embedded): Lucene special characters `\ + - ! ( ) : ^ [ ] " { } ~ * ? | & /` get a backslash, and the standalone operators `AND`/`OR`/`NOT` are lower-cased so they are searched as words (the default analyzer lower-cases anyway; `ANDROID` etc. are left alone).
- **Default behaviour change (deliberate):** escaping is on by default; new builder option `rawFullTextQuery(true)` (both builders) passes Lucene syntax through unchanged. Rationale: the old default silently returned nothing for natural-language questions containing `/`, `?`, `:` … (2 of 3,452 NQ queries per pass in every baseline). Owner to confirm when reviewing the fix stack.
- If a hybrid query still fails (e.g. invalid raw syntax), the store logs a warning and returns the **dense-only** results instead of an empty list (remote and embedded).
- Tests: unit test for the escaping (`ArcadeDBFullTextQueryTest`); ITs for a question full of Lucene syntax (remote + embedded, red on the old code) and for raw mode — valid Lucene query works, invalid one falls back to dense. Store suite: 250 tests green on 26.9.1. On 26.7.2 one inherited embedded test (`should_filter_by_metadata` case 57, many identical vectors) failed once (24 of 26 matches) and passed on two reruns — intermittent approximate-search behaviour of 26.7.2, unrelated to S8.
