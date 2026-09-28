# B09 — Standard-tier embedding cache + ground truth (run)

| | |
|---|---|
| Phase | 2 — Scale-up |
| Branch | `arcadedb-rag-benchmark` (no code expected; ops) |
| Depends on | B00, B03, B04 |
| PRD | F7, F9, F10, F11, R5 |

## Scope
- Embed the 1,000,000 `standard` passages + 3,452 queries on the reference machine (checkpointed; ~1–3 h at the handoff's estimated throughput).
- Compute ground truth for `smoke` and `standard`; filtered ground truth for `standard` at 1/10/50%.
- Record wall time and throughput (used to size B22); archive the cache with checksums (backup copy outside the data dir).

## Acceptance
- Manifests present and consistent; embedding checksum recorded in this issue.
- Ground-truth caches for smoke and standard load without recomputation.

## Notes
Start as soon as B03 works — it does not depend on the rest of the smoke path. Full-tier embedding and ground truth are deferred to B22.
