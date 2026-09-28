# B05 — Metrics library

| | |
|---|---|
| Phase | 1 — Smoke path |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B01 |
| PRD | D3, M1–M4, M6 (stats), F16 (median / spread) |

## Scope
- IR metrics vs qrels: nDCG@10, Recall@10, Recall@100, MRR@10 with BEIR / trec_eval definitions (graded relevance, doc-level qrels; query ids with no relevant docs handled as trec_eval does).
- ANN recall@10 / @100 vs ground truth (plain and filtered).
- Result-count shortfall (fraction of queries with < k results) and failure rate (exceptions, parse errors, empty results).
- Latency stats: p50/p95/p99/max from recorded per-query nanos (HdrHistogram or sorted arrays); median + min/max across repetitions.
- Pure functions, no store / dataset dependencies.

## Acceptance
- Unit tests against a small fixture whose expected values were computed with `pytrec_eval` / BEIR (values pasted into the test).
- Latency percentile tests on known distributions.
