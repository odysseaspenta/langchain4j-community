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

## Outcome (2026-09-29) — code complete, heavy acceptance runs deferred
- `rag-bench embed [--tier smoke|standard|full | --rows n] [--threads n] [--verify n]`: passages embedded in `priority.ids` order into `embeddings/<dataset>/seed-<seed>/<model>/passages.{f32,ids,json}`, committed in chunks of 256; interrupted runs resume after the last committed chunk (uncommitted bytes truncated). Queries: `queries.{f32,ids,json}`, embedded one at a time with latency stats (M9). Cache identity = model id + SHA-256 of the ONNX weights + prefix + dataset manifest + seed + order; a mismatch is refused, never silently rebuilt.
- LangChain4j facts that shape this: one text per ONNX call (so vectors do not depend on batching), CLS pooling, L2-normalised, texts > 510 tokens split and averaged.
- **Throughput fix:** stock LangChain4j sessions each use all cores, so parallel callers oversubscribe the CPU. Bulk embedding uses the same LangChain4j encoder with a 1-intra-op-thread ONNX session per call (`InProcessModels`); single-query embedding (M9) uses the stock model. Measured on the dev box (7-core ARM VM, 500 passages): stock 1 caller 7.9/s, stock 7 callers 14.1/s, 1-thread sessions × 7 callers **22.7/s**, bit-identical to stock output (30/30 samples).
- Checked on real NQ data (dev box): 3,000 passages embedded with 7 stock callers; `--verify 50` re-embedded 50 passages single-threaded → 0 mismatches (bit-identical). Query embedding: 3,452 queries, single-query p50 25 ms / p95 53 ms / p99 84 ms (dev box, not a reference number).
- Unit tests (fake deterministic model): priority-order rows, append-only extension with byte-identical prefix and no re-embedding, crash + torn write + resume = byte-identical to an uninterrupted run, identity mismatch refused, query prefix, verify.
- Bug found and fixed: `--verify` could sample a row twice, which `PassageReader` silently lost; sampling is now distinct and the reader rejects duplicate ids.

### Deferred to the reference machine (owner request 2026-09-29: dev box too slow)
At ~23 passages/s the smoke tier alone takes ~75 min here, so these acceptance runs wait for the reference machine (and B09 for standard):
- [x] Embed the full `smoke` tier (100k) and record throughput — 2026-09-29, i7-9700K 8 threads: **95.3 passages/s**, 100k in 18 min; query embedding p50 5.4 ms / p99 10.9 ms (1 thread).
- [ ] Real kill + restart during `embed`, then confirm the final checksum equals an uninterrupted run in a second data dir (unit-tested with the fake model only).
- [ ] Confirm a cache built with 1-thread sessions matches the stock model on a larger sample (`--verify 200`).
- Note: the dev box's cache at `~/.cache/langchain4j-rag-benchmark/embeddings/nq/seed-42/bge-small-en-v1.5` holds 3,800 rows built in several runs with different thread settings; it is valid but not a reference artefact.
