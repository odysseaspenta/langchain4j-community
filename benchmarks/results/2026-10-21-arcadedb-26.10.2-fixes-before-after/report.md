# ArcadeDB store fixes — before / after (B11), smoke tier, ArcadeDB 26.10.2

- **Before:** `2026-10-16-arcadedb-26.10.2-baseline-smoke` — the unmodified store on 26.10.2 (benchmark commit `dc30005b`).
- **After:** `2026-10-21-arcadedb-26.10.2-baseline-smoke-fixes` — the fix stack merged (benchmark commit `7dd065c8`, clean tree): S10, S3, S11, S2, S8, S1, 26.10.2 update, S12, S7. Store defaults throughout (batch sizes 1000 / 500, RRF 8:1, `EnglishAnalyzer`); the harness no longer strips line breaks for remote mode.
- Same protocol for both: smoke tier (100k NQ passages, 3,452 test queries, bge-small-en-v1.5), cold pass + 3 repetitions, k = 100, client on CPUs 0–3, server container on 4–7 with an 8 GB heap, Java Vector API on. Both runs complete.

## Embedded

| | Before | After |
|---|---|---|
| Load | 3,137 docs/s (32 s) | **8,388 docs/s** (12 s) |
| Time to searchable | 16.5 s | 16.3 s |
| Disk | 424 MiB | 323 MiB |
| `dense` nDCG@10 / R@10 / R@100 | 0.8521 / 0.9407 / 0.9789 | 0.8537 / 0.9424 / 0.9802 |
| `dense` ANN recall@10 | 0.993 | 0.992 |
| `dense` p50 / p95 / p99 | 3.34 / 4.26 / 4.69 ms | 3.26 / 4.09 / 4.65 ms |
| `hybrid-asis` nDCG@10 / R@10 / R@100 | 0.7941 / 0.9178 / 0.9894 | **0.8557 / 0.9504** / 0.9814 |
| `hybrid-asis` p50 / p95 / p99 | 544 / 635 / 694 ms | **21.7 / 39.7 / 50.1 ms** |
| Empty hybrid results | 2 | 0 |

## Remote (Docker)

| | Before | After |
|---|---|---|
| Load | 576 docs/s (174 s) | **2,830 docs/s** (35 s) |
| Time to searchable | 25.1 s | 23.3 s |
| `dense` | **aborted**: every query hit the 25 s timeout (S3) | nDCG@10 0.8318, R@10 0.9178, ANN recall@10 0.970 |
| `dense` p50 / p95 / p99 | – | 29.9 / 31.5 / 35.3 ms |
| `hybrid-asis` nDCG@10 / R@10 / R@100 | 0.7935 / 0.9174 / 0.9891 | **0.8525 / 0.9478** / 0.9792 |
| `hybrid-asis` p50 / p95 / p99 | 477 / 563 / 607 ms | **127.9 / 147.9 / 159.4 ms** |
| Empty hybrid results | 2 | 0 |

## Attribution

- **Embedded load ×2.7:** S1 (one transaction per 1,000 records instead of per record).
- **Remote load ×4.9:** S2 (one `sqlscript` per 500 records instead of one HTTP call per record); S11 also lets NQ text with line breaks be inserted as is.
- **Remote dense works at all:** S3 (the store's remote query had made every dense query a full scan).
- **Hybrid ×25 faster (embedded), ×3.7 (remote), +0.06 nDCG@10 in both:** S7 (bounded full-text source, `EnglishAnalyzer`, RRF 8:1). Embedded matches the investigation's probe (25 ms, 0.853).
- **No empty hybrid results:** S8 (queries with Lucene syntax are escaped instead of failing).
- Hybrid now at or slightly above dense-only (0.856 vs 0.854 embedded; 0.853 vs 0.832 remote). On the investigation's probe they tied; read the embedded margin as a tie.

## Open findings (remote)

1. **Remote dense is less accurate than embedded:** ANN recall@10 0.970 vs 0.992, nDCG@10 0.832 vs 0.854, on the same data and index settings. The remote graph is built by the server in the background rather than by `buildVectorGraphNow()`; the embedded log reports 49 of 100,000 vectors unreachable, and the remote figure is not observable over HTTP. Candidate causes: a different or partial graph build on the server, or remote search parameters. To investigate (with S4, per-query efSearch).
2. **Remote overhead is large:** dense 30 ms vs 3.3 ms embedded, hybrid 128 ms vs 22 ms. Both return up to 400 candidate rows, including the 384-float embedding, as JSON per query, so result transfer is the first suspect, but it does not explain why hybrid's overhead (~106 ms) is four times dense's (~27 ms). To profile before proposing a fix (e.g. a fuse/neighbour limit of k when there is no filter, or not returning embeddings unless needed).

Both full-text indexes were checked in the kept databases: `EnglishAnalyzer`, BM25, 100,000 documents — the remote gap is not the analyzer.

## Update 2026-10-22: S4 and the remote findings

Both open findings were investigated (`2026-10-22-arcadedb-26.10.2-dense-recall-and-remote-overhead`) and fixed in S4
(`e4df5e74`): remote dense fetched only `k` neighbours (narrower beam), rows came back twice (nested `record`), and
hybrid returned all 400 fused candidates. Smoke run `2026-10-22-arcadedb-26.10.2-baseline-smoke-s4` (benchmark commit
`f6109cb8`; marked dirty only by the untracked standalone `tools/DenseProbe.java`, not part of the harness; store
module clean), same protocol:

| | B11 after (`7dd065c8`) | With S4 (`f6109cb8`) |
|---|---|---|
| Remote `dense` nDCG@10 / ANN recall@10 | 0.8318 / 0.9702 | **0.8511 / 0.9927** |
| Remote `dense` p50 / p95 / p99 | 29.9 / 31.5 / 35.3 ms | **19.2 / 22.0 / 23.6 ms** |
| Remote `hybrid-asis` nDCG@10 | 0.8525 | 0.8529 |
| Remote `hybrid-asis` p50 / p95 / p99 | 127.9 / 147.9 / 159.4 ms | **34.7 / 55.5 / 65.9 ms** |
| Embedded `dense` nDCG@10 / p50 | 0.8537 / 3.26 ms | 0.8530 / 3.30 ms |
| Embedded `hybrid-asis` nDCG@10 / p50 | 0.8557 / 21.7 ms | 0.8547 / 20.4 ms |
| Load embedded / remote | 8,388 / 2,830 docs/s | 8,263 / 2,784 docs/s |

Remote accuracy now matches embedded. Overall against the unmodified store (2026-10-16): remote dense from aborted
to 19 ms at nDCG@10 0.851; remote hybrid 477 → 35 ms (nDCG@10 0.794 → 0.853). The remaining remote overhead
(~16 ms per 100 rows) is client-side JSON parsing of the embeddings in ArcadeDB's `RemoteDatabase`.
