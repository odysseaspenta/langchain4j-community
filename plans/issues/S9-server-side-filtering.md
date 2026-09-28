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
