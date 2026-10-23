# S6 — Expose index options

| | |
|---|---|
| Phase | 7 — Extended |
| Branch | `fix/arcadedb-index-options` from `upstream/main` |
| Depends on | B10 |
| PRD | S6, D12 |

## Current behaviour
Embedded similarity hardcoded `COSINE` (`:749`, and `:297` in `removeAll`); no quantization option; Javadoc describes `beamWidth` as a search beam width (`:1107`) though it is build-time only.

## Scope
- Builder options: `quantization` (NONE/INT8/BINARY/PRODUCT), `similarity` for embedded mode (score conversion per similarity), optionally index `efSearch` METADATA (26.8.1+).
- Fix `beamWidth` Javadoc.
- Respect S10's per-version METADATA key handling.

## Acceptance
- ITs for each option; benchmark before/after = `index-grid` (B18).

## Outcome (2026-10-23) — commit `0676b5b7` on `feat/arcadedb-index-options` (stacked on S4; local, no PR yet)
- `similarityFunction` for embedded mode (was hard-coded COSINE in index creation and `removeAll`); validated names COSINE / EUCLIDEAN / DOT_PRODUCT in both modes (remote Javadoc listed the unsupported SQUARED_EUCLIDEAN).
- `quantization` (`ArcadeDBVectorQuantization.NONE | INT8 | BINARY | PRODUCT`) in both modes; remote via `METADATA { …, "quantization": "…" }` (verified in the server's schema.json), embedded via `withQuantization`. Only sent when not NONE.
- Scores follow the index function: COSINE `(1 + cos) / 2` (unchanged), EUCLIDEAN `1 / (1 + d)`, DOT_PRODUCT `(1 + dot) / 2`. Embedded COSINE still converts ArcadeDB's distance; other functions compute from the stored vector.
- `beamWidth` Javadoc fixed (build-time beam; search beam = `efSearch`).
- Index-level efSearch METADATA not exposed: the store passes efSearch per query (S4), which overrides it.
- Tests: each similarity (order + scores) and each quantization (finds a stored vector) in both modes; embedded asserts the index metadata, and that `removeAll` keeps the settings; unsupported-name validation. Store suite 284 tests green on 26.10.2 and 26.9.1.
- Defaults unchanged → no re-measurement; before/after per option is the `index-grid` scenario (B18).
