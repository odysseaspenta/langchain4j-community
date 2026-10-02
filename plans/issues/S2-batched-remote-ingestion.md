# S2 — Batched remote ingestion

| | |
|---|---|
| Phase | 4 — Viability fixes |
| Branch | `fix/arcadedb-batched-remote-ingestion` from `upstream/main` |
| Depends on | B10 (remote baseline recorded) |
| PRD | S2, R1 |

## Current behaviour
`addAllRemote` (`:327-358`) sends one HTTP `command` per vector with the vector inlined into SQL via `embeddingToSql`.

Related: [S11](S11-remote-text-escaping.md) (remote SQL inlining breaks on line breaks; note only). If S11 has landed, batch its parameterized insert.

## Scope
- Many rows per request with vectors as parameters: parameterized `sqlscript` inside `begin()/commit()`, `INSERT … CONTENT [...]`, or `RemoteDatabase` `/batch` (handoff §6.3). Pick one, justify in the PR (throughput, upsert semantics, version availability on 26.7.2).
- Configurable batch size; upsert semantics preserved.
- Tests in `ArcadeDBEmbeddingStoreIT` (Testcontainers).

## Acceptance
- Module ITs pass on 26.7.2.
- Benchmark before/after: remote ingestion vectors/s at smoke and standard.

## Outcome (2026-10-02) — commit `196917df` on `fix/arcadedb-batched-remote-ingestion` (stacked on S11; local, no PR yet)
- Options measured against 26.9.1 (5,000 rows, 384-d, text + metadata; client and server sharing CPUs 4–7): per-row `INSERT` ~300 rows/s; per-row inside one remote transaction 850; `INSERT … CONTENT :rows` (list-of-maps parameter) ~2,500; **`sqlscript` of parameterized INSERTs in `BEGIN; … COMMIT;` per batch 1,800–3,250** (batch size 100–2,000 makes little difference). Chosen: the `sqlscript` batch — works on 26.7.2 and 26.9.1, keeps per-row metadata keys, one transaction per batch.
- New remote builder option `batchSize` (default 500). Semantics unchanged (plain inserts, as before; a batch is atomic).
- New IT: 10 embeddings in batches of 3 with differing metadata keys per row. Store suite (246 tests) green on 26.9.1 and 26.7.2.
