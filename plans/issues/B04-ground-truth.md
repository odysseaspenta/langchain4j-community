# B04 — Brute-force ground truth (plain + filtered)

| | |
|---|---|
| Phase | 1 — Smoke path |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B03 |
| PRD | D3, F10, F11, acceptance 4 |

## Scope
- Exact top-100 cosine neighbours (dot product on normalized vectors) per test query per tier; multithreaded brute force over the mmapped cache.
- Filtered ground truth: top-100 restricted to `bucket < 1`, `< 10`, `< 50` (standard tier; cheap enough to also do smoke for testing).
- Cached with a manifest tied to the embedding-cache checksum, tier manifest and filter; recomputed automatically if either changes.
- Deterministic tie-breaking (e.g. by id) so results are reproducible.

## Acceptance
- Acceptance 4: for a sample of queries on a 10k subset, results match an independent exact computation (LangChain4j `InMemoryEmbeddingStore`).
- Smoke-tier ground truth computed and cached; rerun is a cache hit.

## Outcome (2026-09-29) — code complete, heavy runs deferred
- `rag-bench ground-truth [--tier smoke,standard,...] [--k 100] [--threads n]` writes, per tier, the unfiltered set and `bucket < 1 / 10 / 50` sets to `groundtruth/<dataset>/seed-<seed>/<model>/k<k>/<tier>[-bucket-lt-<t>].{rows,scores,json}` (int32 cache rows / float32 scores, little-endian, best first; `-1`/NaN padding when a filter leaves fewer than k candidates). Filtered sets are computed for every tier, not only standard — they share the same pass and cost almost nothing extra.
- One pass for everything: tiers are prefixes of the cache order, so the search over the largest requested tier snapshots each smaller tier at its boundary, and each dot product feeds the unfiltered and all filtered top-k heaps. Work is split by query chunks (32) streaming passages in 2,048-row blocks.
- Determinism: fixed-order float accumulation (4 lanes), ties broken by lower row; results identical for 1 vs 7 threads and for combined vs separate prefix searches (tests).
- Caching: a set is reused when its manifest matches (tier size, filter, bucket seed, k, query vectors SHA-256, and the embedding-cache identity — model weights SHA, prefix, dataset, seed, order; the cache is append-only so identity + row count fixes the vectors) and its files match their checksums; otherwise recomputed.
- **Acceptance 4 met (unit test):** 10k random 32-d unit vectors, 25 queries, top-100 identical to LangChain4j `InMemoryEmbeddingStore`, unfiltered and with `metadataKey("bucket").isLessThan(10)`. (`langchain4j` added as a test-scope dependency for this.)

### Deferred to the reference machine
- [ ] Smoke-tier ground truth on real NQ embeddings (needs the deferred B03 smoke embedding), then rerun to confirm a cache hit.
- [ ] Standard-tier (1M) plain + filtered ground truth — part of B09.
- [ ] Record brute-force wall time per tier (sizing input for B09/B22).
