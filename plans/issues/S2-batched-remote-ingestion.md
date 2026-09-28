# S2 — Batched remote ingestion

| | |
|---|---|
| Phase | 4 — Viability fixes |
| Branch | `fix/arcadedb-batched-remote-ingestion` from `upstream/main` |
| Depends on | B10 (remote baseline recorded) |
| PRD | S2, R1 |

## Current behaviour
`addAllRemote` (`:327-358`) sends one HTTP `command` per vector with the vector inlined into SQL via `embeddingToSql`.

## Scope
- Many rows per request with vectors as parameters: parameterized `sqlscript` inside `begin()/commit()`, `INSERT … CONTENT [...]`, or `RemoteDatabase` `/batch` (handoff §6.3). Pick one, justify in the PR (throughput, upsert semantics, version availability on 26.7.2).
- Configurable batch size; upsert semantics preserved.
- Tests in `ArcadeDBEmbeddingStoreIT` (Testcontainers).

## Acceptance
- Module ITs pass on 26.7.2.
- Benchmark before/after: remote ingestion vectors/s at smoke and standard.
