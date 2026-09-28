# B08 — Remote (Docker) `ArcadeDbTarget`

| | |
|---|---|
| Phase | 2 — Scale-up |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B06, B07 |
| PRD | D7, D9, F13, F17 |

## Scope
- Remote mode of `ArcadeDbTarget` using the store's remote builder against `arcadedata/arcadedb:${arcadedb.version}` in Docker on the same host.
- Container CPU-pinned (cpuset) to cores disjoint from the client JVM; configurable server heap (`JAVA_OPTS`) and client heap; record both.
- Server log level for vector index set to WARNING (F18).
- Record Docker version and container limits in results.
- Data volume on the data dir so a loaded database can be reused (F15, B16).
- Added to `smoke` as an optional mode flag (not in the smoke profile by default — PRD smoke is embedded-only).

## Acceptance
- Smoke tier loads and answers `dense` + `hybrid-asis` remotely on the unmodified store.
- Container is pinned to the configured cpuset (verified via `docker inspect`).

## Notes
Moved ahead of the baseline (handoff had it in step 5) because S2/S3 are remote-only and need a remote "before" measurement (acceptance 7). Expect remote `dense` quality on the unmodified store to be poor — S3's scan-order bug.
