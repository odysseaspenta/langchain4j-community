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
