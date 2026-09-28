# B03 — Embedding cache (corpus + queries)

| | |
|---|---|
| Phase | 1 — Smoke path |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B02 |
| PRD | D6, F6–F9, M9, N1, N4, R5 |

## Scope
- In-process `bge-small-en-v1.5` (full precision, 384-d, normalized) via `langchain4j-embeddings-bge-small-en-v15`.
- Queries prefixed with `"Represent this sentence for searching relevant passages: "`; passages not prefixed.
- Cache format: raw little-endian float32 matrix + ordered id file + manifest (model id/revision, dims, normalization, prefix, dataset manifest checksum, matrix checksum). Memory-mapped reader.
- One append-only cache for all tiers: embed `smoke` ids first, then the rest of `standard`, later (B22) the rest of `full`. Tiers index into the cache by id, so extending it never re-embeds or reorders existing rows. The manifest records which tier the cache currently covers.
- Parallel across cores; checkpointed/resumable (append + progress marker; restart continues where it stopped).
- Query embeddings cached the same way. Measure per-query embedding latency (M9) separately from store latency.
- Log throughput (passages/s) — needed to estimate B09 duration.

## Acceptance
- Embedding the `smoke` tier works end-to-end; kill + restart resumes without re-embedding finished work and yields an identical checksum.
- Spot check: cached vector for a sample passage equals a fresh embedding of the same text (exact, same machine).
- Throughput number recorded for this machine.
- Extending a smoke-only cache to a larger tier appends rows; existing rows and their checksum prefix are unchanged.

## Notes
Handoff estimate 100–400 passages/s on a many-core CPU → 2–7 h for 2.68M. Treat the finished cache as a checksummed artefact that is copied between machines rather than regenerated (N1).
