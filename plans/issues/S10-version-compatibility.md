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

## Outcome (2026-10-01) — fix on branch `fix/arcadedb-version-compat` (commit `269dc68c`, local, not yet pushed / PR'd)
- **Owner decision (2026-10-01): the store fixes target the LangChain4j ArcadeDB integration running on ArcadeDB 26.9.1.** Upstream already bumped the default `arcadedb.version` to 26.9.1 (`16fc0b74`, #612, Renovate), so the version question in the scope is settled; every S-fix branch from `upstream/main` builds against 26.9.1.
- **Gap found:** upstream's remote ITs still started `arcadedata/arcadedb:26.7.2` while the client jars were 26.9.1, so remote mode had never been tested against a 26.9.1 server. Upstream as-is: 238 tests, 0 failures (26.9.1 client / 26.7.2 server).
- **Incompatibility found (26.8.1+):** `CREATE INDEX IF NOT EXISTS` with a configuration that differs from the existing index is rejected ("… already exists … with a different configuration: maxConnections=16 (requested 32). Drop the existing index first …"). The remote builder sent it on every open, so **re-opening an existing remote database with different `maxConnections`/`beamWidth` failed**; embedded mode reuses the existing index silently; 26.7.2 kept it silently. Index METADATA is not visible over HTTP (`schema:indexes` has no settings). The store sends only supported METADATA keys, so the unknown-key validation does not affect it.
- **Fix:** remote schema init creates the vector index only if `schema:indexes` has no `<type>[embedding]` (as embedded mode does). Remote ITs take the image tag from the ArcadeDB client jar (`ArcadeDBTestServer`), so `-Darcadedb.version` switches the server under test too. New ITs for re-opening with different index settings (remote: red before / green after; embedded: parity).
- **Acceptance:** `./mvnw -pl embedding-stores/langchain4j-community-arcadedb verify` passes with `-Darcadedb.version=26.9.1` (default) and `=26.7.2` — 240 tests, 0 failures, 2 skipped each, against the matching server image.
- The benchmark harness and the unmodified store also compile and pass the benchmark's tests against 26.9.1 (2026-10-01 smoke comparison).
