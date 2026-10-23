# S9 — Server-side metadata filtering

| | |
|---|---|
| Phase | 7 — Extended |
| Branch | `fix/arcadedb-server-side-filter` from `upstream/main` |
| Depends on | B13 (filtered baseline); S3 for remote |
| PRD | S9 |

## Current behaviour
Filters applied in Java after 5×/10× over-fetch (`:376-381`, `:406`, `:588`, `:622`).

## Scope
- Translate LangChain4j `Filter` to `vector.neighbors(..., {filter: (SELECT @rid ...)})` and full-text `WHERE`, where supported; fall back to post-filter for unsupported filter shapes.
- Note 26.9.1 pre-filter plan for allow-lists ≤20% selectivity (#6502, #6514) — expect version-dependent results.

## Acceptance
- ITs for each supported filter type; benchmark before/after = `filtered` recall, shortfall and latency at 1/10/50%.

## Outcome (2026-10-23) — commits `b4122c8c` + `4d8aca81` on `feat/arcadedb-server-side-filter` (stacked on S6; local, no PR yet)
- B13 `filtered` profile built (`0c50043e`) and run as the "before".
- Option `filterStrategy` (`ArcadeDBFilterStrategy.ADAPTIVE | PRE_FILTER | POST_FILTER`), both modes; default ADAPTIVE: remote always filters in SQL (`vector.neighbors(..., { filter: (SELECT @rid ... WHERE <filter>).@rid })`), embedded post-filters first and falls back to an allow-list (`findNeighborsFromVector(..., allowed)`) when fewer than k pass; hybrid filters both sources (full-text after bounding). ContainsString stays a post-filter.
- Also fixed: remote Float filter values were sent as their shortest decimal (float equality/range filters, incl. `removeAll(filter)`, missed or wrongly matched); 26.9.1 rejects rows as an allow-list (→ `.@rid`); the allow-list's soft-delete check made it 6× slower (dropped; deleted records are not in the vector index).
- Tests: LangChain4j filter suite again with PRE_FILTER in both modes; selective filter complete with ADAPTIVE/PRE_FILTER, short with POST_FILTER; removed records excluded; remote float removal. Store suite 503 tests green on 26.10.2 and 26.9.1.
- Before/after: `benchmarks/results/2026-10-23-arcadedb-26.10.2-filtered-search-s9/report.md` — complete results at every selectivity; remote 2–3.8× faster; embedded 14–23 ms dense / ~60 ms hybrid at 1–10 % (was 3.5 / 33 ms but incomplete), unchanged at 50 %.
- Possible follow-up: embedded hybrid ADAPTIVE pays the failed post-filter attempt at 1–10 %.
