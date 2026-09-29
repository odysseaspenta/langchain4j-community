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

## Outcome (2026-09-29) — code complete, real smoke run deferred
- `ArcadeDbTarget.remote(serverDir, settings, RemoteSettings)`: same load loop and `EmbeddingStore` search path as embedded, via the store's remote builder (`createDatabase(true)`). `load` starts a fresh container, `close` stops (`docker stop -t 60`) and removes it; a shutdown hook removes it if the JVM exits early.
- `DockerServer` (docker CLI, no Testcontainers): `arcadedata/arcadedb:<classpath version>` (F13), `--cpuset-cpus`, HTTP published on loopback only, `ARCADEDB_OPTS_MEMORY=-Xms=-Xmx`, ZGC made explicit, server GC log, logging config with `com.arcadedb.index.vector` at WARNING (F18, verified: no per-search line in the server log). Bind mounts `<dataDir>/databases/arcadedb-<v>/<tier>-remote/{databases,log}` so the database outlives the container (F15 hook for B16). Recorded: image id + repo digest, cpuset, memory/CPU limits, JVM env, container peak memory (cgroup `memory.peak`, new `LoadStats.serverPeakMemoryBytes`).
- CPU pinning (F17): `--server-cpus` (default: CPUs the client isn't pinned to, else the upper half); client pinned by the launcher with `RAG_BENCH_CLIENT_CPUS` → `taskset`. Overlap → stderr warning + `isolationWarnings` in the target config and report. Server heap `--server-heap` (default 4g); client heap `RAG_BENCH_HEAP`.
- **Time to searchable, remote:** `REBUILD INDEX` on the vector index (keeps its metadata; synchronous). No remote equivalent of `buildVectorGraphNow()` (server JS can't reach the index API; `schema:indexes` shows no build state). Upper bound — it also re-reads records. Labelled "harness step, not via store API".
- **Server query timeout:** `ALTER DATABASE arcadedb.command.timeout` after load+rebuild (`--query-timeout-seconds`, default 30); client HTTP timeout set above it. Without it, a client-side timeout leaves the query running on the server and slows the next queries (observed).
- **Runner circuit breaker:** a pass is aborted after `--failure-streak-limit` (default 10) consecutive failed/empty queries or `--pass-time-cap-minutes`; skipped queries count as empty rankings, no further passes run, the scenario is flagged `aborted` (JSON + report, `Failures.skipped`). Needed because the store swallows remote errors and returns empty results.
- CLI: `run --mode embedded,remote` overrides the profile's modes (`smoke` stays embedded-only by default).
- **Finding 1 (→ S11, note only):** unmodified remote `addAll` can't insert text with line breaks (SQL literal, `escapeString` misses `\n`), i.e. no BEIR passage at all. Workaround in the remote target: line breaks → spaces (recorded as `storedText`; embeddings and full-text tokens unchanged).
- **Finding 2 (→ S3):** remote `dense` at 5,000 random 384-d vectors: > 30 s per query or server OOM at 2 GB heap; k = 1 ≈ 25 s. At 100k expect every query to time out and the scenario to abort. Remote hybrid at 5,000: 50–100 ms; remote load ≈ 400 docs/s (one HTTP call per vector).
- Tests (77 total, Docker-backed ones skip without Docker): remote load/search, cpuset verified via `docker inspect`, no per-search log line, GC log present, container removed on close; runner end-to-end in remote mode; breaker aborts after the streak and counts skipped queries; `CpuSet` parsing/defaults.

### Deferred to the reference machine
- [x] Acceptance: `rag-bench run --profile smoke --mode embedded,remote` on real NQ — 2026-09-29. Remote load 100k in 185 s (**540 docs/s**, one HTTP call per vector; S2 baseline), server peak memory 9.4 GiB (8 GB heap). `dense` aborted after 10 queries at ~30 s each (all empty: server timeout swallowed by the store) — S3 baseline. `hybrid-asis`: nDCG@10 0.4535 (embedded 0.4526), p50 181 ms / p99 228 ms (embedded 111 / 178 ms).
- [x] Remote load throughput at 100k: 540 docs/s, no insert stalled near the client timeout.
- [ ] **Fix remote time-to-searchable (bug found 2026-09-29).** The smoke result reports 0.7 s; it is wrong. Reproduced on the kept database with vector logging at INFO: on 26.7.2 `REBUILD INDEX` only reloads the vectors (100k in 0.9 s) and marks the index READY; the JVector graph is **not** built. The first vector query then blocks until a full graph build finishes (~58 s at 100k on 4 CPUs), and 15 s after the rebuild the inactivity timer starts a **second** full build ("triggering graph rebuild for 100000 pending mutations"), which can overlap measured queries. In the smoke run this landed in the aborted remote `dense` scenario, so `hybrid-asis` latencies are unaffected. Fix: after loading, time until a probe `vector.neighbors` query succeeds, then wait past the inactivity timeout and probe again until fast, and count that as time-to-searchable; drop `REBUILD INDEX` (it also discards the graph built during load) or keep it only as an optional full-rebuild step matching embedded `buildVectorGraphNow()`. Note: a `float[]` positional parameter over HTTP fails on the server; the probe must inline the vector.
