# Filtered search before / after S9 — ArcadeDB 26.10.2, smoke tier

`filtered` profile (B13): `dense` unfiltered for reference, then `dense` and hybrid (store defaults) with the metadata
filter `bucket < 1 | 10 | 50` (≈ 1 / 10 / 50 % of the 100k NQ passages pass), k = 100, cold pass + 3 repetitions,
client CPUs 0–3, server container CPUs 4–7 (8 GB heap). All runs complete and clean.

- **Before:** `2026-10-23-arcadedb-26.10.2-filtered-before-s9` — fix stack through S6 (`0c50043e`): post-filter only.
- **After, first version:** `2026-10-23-arcadedb-26.10.2-filtered-after-s9` (`05f1d074`, S9 `b4122c8c`).
- **After:** `2026-10-23-arcadedb-26.10.2-filtered-after-s9b` (`46a86dde`, S9 + `4d8aca81`), default `ADAPTIVE` strategy.

Accuracy is ANN recall@100 against the *filtered* exact ground truth, plus the share of queries returning fewer than k
results. nDCG@10 is not meaningful here: most relevant passages are excluded by the filter itself.

## Embedded

| Scenario | Before: recall@100 / short / p50 | After: recall@100 / short / p50 (p99) |
|---|---|---|
| `dense` | 0.983 / 0 % / 3.3 ms | 0.983 / 0 % / 3.3 ms |
| `dense-filtered-1` | **0.043 / 100 %** / 3.5 ms (38 queries empty) | **1.000 / 0 %** / 13.9 ms (17.9) |
| `dense-filtered-10` | **0.396 / 100 %** / 3.5 ms | **1.000 / 0 %** / 22.6 ms (28.4) |
| `dense-filtered-50` | 0.973 / 0 % / 3.4 ms | 0.973 / 0 % / 3.5 ms (4.7) |
| `hybrid-filtered-1` | – / **100 %** / 33.1 ms | – / **0 %** / 64.3 ms (117.8) |
| `hybrid-filtered-10` | – / **52 %** / 33.6 ms | – / **0 %** / 59.0 ms (121.5) |
| `hybrid-filtered-50` | – / 0 % / 33.0 ms | – / 0 % / 36.1 ms (65.3) |

## Remote

| Scenario | Before: recall@100 / short / p50 | After: recall@100 / short / p50 (p99) |
|---|---|---|
| `dense` | 0.983 / 0 % / 19.3 ms | 0.983 / 0 % / 19.3 ms |
| `dense-filtered-1` | **0.053 / 100 %** / 73.5 ms (15 queries empty) | **1.000 / 0 % / 27.8 ms** (31.9) |
| `dense-filtered-10` | **0.495 / 100 %** / 73.5 ms | **1.000 / 0 % / 36.9 ms** (42.6) |
| `dense-filtered-50` | 0.973 / 0 % / 73.5 ms | 0.990 / 0 % / **49.3 ms** (56.0) |
| `hybrid-filtered-1` | – / **100 %** / 166.7 ms | – / **0 % / 44.0 ms** (74.0) |
| `hybrid-filtered-10` | – / **51 %** / 166.6 ms | – / **0 % / 53.7 ms** (84.1) |
| `hybrid-filtered-50` | – / 0 % / 165.7 ms | – / 0 % / **66.5 ms** (97.4) |

## Reading

- **Before**, the store fetched a fixed candidate set and filtered it in Java: a 1 % filter returned ~4 of 100 results
  (some queries none), 10 % ~40. It was fast because it was incomplete.
- **After**, every filtered scenario returns complete results; at 1 % and 10 % they are exact.
- **Remote** is faster at every selectivity (one server-side query instead of 5k full rows over HTTP): dense 2–2.6×,
  hybrid 2.5–3.8×.
- **Embedded** keeps the cost of an unfiltered search where post-filtering already works (50 %), and pays 14–23 ms
  (dense) / ~60 ms (hybrid) for complete results where it did not. Embedded hybrid at 1 / 10 % pays the failed
  post-filter attempt (~33 ms) before the SQL-filtered query (~30 ms in the probe); `PRE_FILTER` would skip it at the
  cost of the 50 % case (≈ 49 vs 36 ms in the probe). Possible follow-up: choose the strategy from the observed
  pass rate of the first attempt's candidates.
- The first S9 version was complete but slow in embedded mode (dense 78–90 ms, hybrid 134–166 ms): the allow-list
  checked the soft-delete flag (6× slower scan) and hybrid filtered every full-text match before bounding. Fixed in
  `4d8aca81` (see the probe numbers in its commit message).

Probes: `benchmarks/langchain4j-community-rag-benchmark/tools/FilterProbe.java` (strategies; dense allow-list costs:
scan 10–22 ms, ~1 ms at 1 % with an index on the metadata key, but 70 ms at 50 %; so the store does not create
metadata indexes).
