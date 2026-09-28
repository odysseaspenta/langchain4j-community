# B13 — `filtered` scenario

| | |
|---|---|
| Phase | 5 — Canonical |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B11 (and B19 for the `hybrid-tuned` half) |
| PRD | D11, §7.4 `filtered`, F11, M1, M3 |

## Scope
- `dense` and `hybrid-tuned` with metadata filter `bucket < 1 | < 10 | < 50` via the LangChain4j `Filter` API (`metadataKey("bucket").isLessThan(n)`), standard tier, embedded.
- ANN recall vs filtered ground truth; result-count shortfall (M3); latency.
- `dense` half can run as soon as B11 lands; the `hybrid-tuned` half runs once B19 has frozen the config.

## Acceptance
- Results for all three selectivities; shortfall reported. This is the "before" for S9.
