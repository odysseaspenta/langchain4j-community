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
