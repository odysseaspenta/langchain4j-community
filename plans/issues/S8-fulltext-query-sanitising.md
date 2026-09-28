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
