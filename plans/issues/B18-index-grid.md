# B18 — `index-grid` scenario

| | |
|---|---|
| Phase | 7 — Extended |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | S6, B16 |
| PRD | D12, §7.4 `index-grid` |

## Scope
- maxConnections ∈ {16, 32} × quantization ∈ {NONE, INT8}, `dense` scenario, standard tier (4 loads).
- Report ingestion, disk size, heap, recall-vs-latency per cell.

## Acceptance
- Four cells in one result file; `compare` renders them.
