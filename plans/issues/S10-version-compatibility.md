# S10 — Store version compatibility

| | |
|---|---|
| Phase | 6 — Versions |
| Branch | `fix/arcadedb-version-compat` from `upstream/main` |
| Depends on | — (can start any time; needed before B17) |
| PRD | S10, D9, F13, R2 |

## Scope
- Build the store and run its ITs against 26.7.2 and the latest release (26.9.1 at time of writing) via `-Darcadedb.version`.
- Fix compile breaks from `LSMVectorIndex` internals changes.
- 26.8.1+: unknown index METADATA keys rejected; `CREATE INDEX IF NOT EXISTS` with differing values → HTTP 400. Make schema init tolerant (only send keys the version supports; detect existing index config).
- Decide with the owner whether the upstream PR also bumps the default `arcadedb.version` (`pom.xml:15`) or only guarantees compatibility.
- Record any incompatibilities as findings for the report.

## Acceptance
- `./mvnw -pl embedding-stores/langchain4j-community-arcadedb verify` passes with both `-Darcadedb.version=26.7.2` and `=26.9.1`.
