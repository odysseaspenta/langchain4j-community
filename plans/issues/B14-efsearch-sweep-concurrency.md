# B14 — efSearch sweep, concurrency, recall-vs-latency

| | |
|---|---|
| Phase | 5 — Canonical |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B11 |
| PRD | D10, §7.4 `dense` sweep, M6, M7, M8, F16 |

## Scope
- `dense` sweep over efSearch ∈ {50, 100, 200, 400, 800} on one loaded DB (per-request override from S4, or one store instance per value).
- Concurrency 1 / 4 / 16 / 64 client threads: QPS and p99 (closed-loop, fixed duration or fixed query count; document which).
- Recall@10 vs p95 curve data per efSearch point (M8), stored in the JSON for B15 to chart.
- 3 repetitions with warm-up; median + spread.

## Acceptance
- Monotonic-ish recall with efSearch; curve data present in JSON for embedded and remote.
