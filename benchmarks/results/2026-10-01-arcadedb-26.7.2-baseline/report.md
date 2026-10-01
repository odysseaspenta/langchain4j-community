# Baseline: unmodified ArcadeDB store, ArcadeDB 26.7.2 (B10)

The "before" for every store fix (S1–S9). Curated from three runs of `rag-bench run --profile baseline`; their raw `result.json` files are kept locally (not committed), and each directory's `report.md` is committed alongside this one.

| | |
|---|---|
| Store | `ArcadeDBEmbeddingStore` at `7de83f5` (unmodified; store module clean in every run) |
| ArcadeDB | 26.7.2 — embedded jar and `arcadedata/arcadedb:26.7.2` |
| Machine | reference: Intel i7-9700K (8 cores, no SMT), 46 GB RAM, NVMe; Linux 6.8, JDK 21 |
| Isolation | client JVM pinned to CPUs 0–3 (`taskset`), server container to CPUs 4–7; embedded mode therefore also runs the store on 4 cores. `-Xms = -Xmx`, G1 client / ZGC server, GC logs on |
| Heaps | embedded run: client 14 GB · remote runs: client 4 GB, server 24 GB |
| Dataset | BEIR NQ (`mteb/nq`), 3,452 test queries; smoke = 100k passages, standard = 1M (seed 42; every judged passage plus random filler) |
| Embeddings | `bge-small-en-v1.5`, 384-d; passages: rows < 100k in-process, rows ≥ 100k via the GPU server (PRD A4, gate passed); queries in-process |
| Protocol | k = 100; per scenario a cold pass, then 3 timed repetitions; accuracy from repetition 1; load time cap 60 min (not reached) |
| Index | pinned `maxConnections` 16, `beamWidth` 100, COSINE, no quantization; efSearch = store default (the unmodified store cannot pass one) |

## Load

| Tier | Mode | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Live heap MiB |
|---|---|---|---|---|---|---|---|
| smoke | embedded | 100,000 | 58 | 1,736 | 54 (forced graph build) | 377 | 668 |
| smoke | remote | 100,000 | 189 | 529 | 92 (probe + server idle) | 473 | 79 (client) |
| standard | embedded | 1,000,000 | 594 | 1,685 | 786 (forced graph build) | 3,772 | 5,053 |
| standard | remote | 1,000,000 | 1,805 | 554 | 907 (probe + server idle) | 3,803 | 147 (client) |

Embedded ingestion is flat from 100k to 1M (one transaction per vector, no stall from 26.7.2's rebuild-every-100-mutations at this scale). Remote ingestion is ~3× slower: one HTTP call per vector (S2).

## Retrieval

Latency in ms: median over the 3 repetitions of each repetition's percentile; the p50 band is min–max across repetitions.

| Tier | Mode | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 / R@100 | p50 (band) | p95 | p99 |
|---|---|---|---|---|---|---|---|---|---|---|
| smoke | embedded | dense | **0.8527** | 0.9420 | 0.9803 | 0.8335 | 0.992 / 0.983 | 28.3 (28.3–28.3) | 35.7 | 38.4 |
| smoke | embedded | hybrid-asis | **0.4525** | 0.8451 | 0.9679 | 0.3406 | – | 108.4 (108.2–108.5) | 157.0 | 176.5 |
| smoke | remote | dense | ⚠ aborted | – | – | – | – | ~30,000 (timeouts) | – | – |
| smoke | remote | hybrid-asis | **0.4540** | 0.8475 | 0.9698 | 0.3414 | – | 180.2 (180.0–180.5) | 214.6 | 227.9 |
| standard | embedded | dense | **0.6234** | 0.8025 | 0.9307 | 0.5821 | 0.974 / 0.958 | 35.3 (35.2–35.3) | 44.5 | 47.8 |
| standard | embedded | hybrid-asis | **0.3206** | 0.6135 | 0.9047 | 0.2402 | – | 1,018.4 (1,015.8–1,019.9) | 1,599.0 | 1,780.6 |
| standard | remote | dense | ⚠ aborted | – | – | – | – | ~30,000 (timeouts) | – | – |
| standard | remote | hybrid-asis | **0.3222** | 0.6159 | 0.9062 | 0.2417 | – | 777.3 (776.6–779.1) | 1,144.2 | 1,274.9 |

All scenarios returned identical rankings in every repetition (`stable`); p50 bands are < 0.5 % wide. Accuracy on the smoke tier is higher than on standard by design (the smoke tier holds every judged passage among only 100k). Remote and embedded `hybrid-asis` accuracy differ slightly because each load builds its HNSW graph independently.

## What the baseline shows (before S1–S9)

1. **Hybrid is much worse than dense** — nDCG@10 0.32 vs 0.62 at 1M, 0.45 vs 0.85 at 100k — and **~29× slower** at 1M (p50 1.0 s, p99 1.8 s embedded). The full-text source is unbounded and unscored, so RRF ranks are arbitrary (S7).
2. **Remote `dense` is unusable** at both tiers: the store's `SELECT *, vector.neighbors(...) FROM T` runs one ANN search per scanned row; every query hits the 30 s limit and the store returns nothing; the scenario aborts after 10 empty queries (S3).
3. **Two queries per pass return nothing in hybrid mode** (`ngn / ims`, `3/5 compromise`): `/` is Lucene query syntax and the parse error is swallowed (S8).
4. **Remote ingestion** ~550 docs/s vs ~1,700 embedded (S2); the remote store also cannot ingest text containing line breaks at all — every NQ passage — so the harness stores them with spaces (S11, note only).
5. **Remote hybrid is faster than embedded at 1M** (p50 777 vs 1,018 ms): the remote server has CPUs 4–7 to itself, while the embedded store shares CPUs 0–3 with the harness.

## ArcadeDB 26.7.2 behaviour found while running it

- **Remote requests are cut at 30 s:** the Java client always uses HTTP/2 and the server closes HTTP/2 requests after 30 s (HTTP/1.1 is not cut), whatever the timeouts (B08).
- **Concurrent rebuilds after a bulk load:** each query that finds the vector graph stale runs its own full rebuild, and an abandoned query keeps running. At 1M the post-load build takes ~15 min remotely; re-sending a query every 30 s stacked up concurrent 1M-vector builds and exhausted a 10 GB, then a 24 GB server heap. With one query at a time a 24 GB heap was enough. An application that queries right after a bulk load can hit this (B10, attempts 1–2).
- **`REBUILD INDEX` does not build the vector graph** (it only reloads the vectors), so it cannot be used to make an index searchable (B08).

## Runs

| Directory | Content | Status | Commit |
|---|---|---|---|
| `2026-09-30-arcadedb-26.7.2-baseline-embedded/` | smoke + standard, embedded | complete | `6f053a75` (clean) |
| `2026-10-01-arcadedb-26.7.2-baseline-remote/` | smoke remote (complete); standard remote failed — server OOM caused by overlapping readiness probes | incomplete | `6f053a75`* |
| `2026-10-01-arcadedb-26.7.2-baseline-remote-standard/` | standard remote, rerun with non-overlapping probes | complete | `42d716e5`* |

\* Marked `dirty` only because earlier runs' `report.md` files were untracked under `benchmarks/results/`; the store module was clean. The dirty check now ignores `benchmarks/results/`. The harness changes between `6f053a75` and `42d716e5` only affect the remote readiness wait, not loading or querying.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
