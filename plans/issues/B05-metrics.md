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

## Outcome (2026-09-29)
- `metrics` package, pure functions over ids / nanos, no dataset or store dependencies:
  - `IrMetrics` — nDCG@k (trec_eval `ndcg_cut`: linear graded gain, log2(rank+1) discount, ideal DCG from all relevant judgments), Recall@k, MRR@k; `evaluate(...)` → mean nDCG@10, Recall@10, Recall@100, MRR@10.
  - `AnnRecall` — recall@k vs exact neighbours; padded (filtered) truth uses the available candidates as denominator; undefined queries skipped.
  - `LatencyStats` — mean / p50 / p95 / p99 / max in ms, nearest-rank percentiles (now also used by the embedding cache for M9; JSON-compatible with existing `queries.json`).
  - `Spread` — median / min / max across repetitions (noise band, F16); `ResultCounts` — shortfall (M3) and failure counts/rate (M4).
- **Deliberate difference from BEIR:** a judged query with no results scores 0 instead of being dropped (BEIR/pytrec_eval omit it, which would reward failing queries). Queries with no relevant judgment are skipped, as in trec_eval. Rankings are used in store order; repeated ids count once.
- Acceptance: per-query values match pytrec_eval (pytrec-eval-terrier, Python 3.12) to 1e-12 on a 5-query fixture covering graded relevance, a judged non-relevant passage ranked first, a hit only at rank 15, an empty result list and a 12-relevant query; latency percentile tests on uniform, skewed and single-sample distributions. No heavy runs involved.
