# PRD: ArcadeDB RAG Retrieval Benchmark

| | |
|---|---|
| Status | Draft — requirements agreed, not yet implemented |
| Date | 2026-09-27 |
| Owner | odysseas |
| Scope | `embedding-stores/langchain4j-community-arcadedb` + new `benchmarks/langchain4j-community-rag-benchmark` module |
| Companion | [`arcadedb-rag-benchmark-handoff.md`](arcadedb-rag-benchmark-handoff.md) (implementation handoff) |

## 1. Problem statement

The LangChain4j ArcadeDB integration (`ArcadeDBEmbeddingStore`) is pinned to ArcadeDB **26.7.2**. Newer ArcadeDB releases (26.8.1, 26.9.1, …) ship vector-index and full-text changes that may materially improve RAG retrieval — especially hybrid (dense + BM25) search. Today there is no way to measure, at realistic scale, whether upgrading the integration's ArcadeDB version improves or regresses **retrieval accuracy** and **speed**, so upgrade decisions and the results published alongside them are unsupported by data.

Code review of the current store also shows it cannot be meaningfully benchmarked at ≥1M vectors without fixes (see §6): ingestion is one transaction / HTTP call per vector, remote search does not return true nearest neighbours, and search depth (`efSearch`) is never passed.

## 2. Goals

1. A **repeatable regression suite** that measures retrieval accuracy and speed of a basic RAG retrieval pipeline backed by ArcadeDB, at **≥1M vectors**, and compares any two (or more) ArcadeDB versions.
2. Quantify the benefit of **hybrid search** (dense + BM25 fusion), both as the store exposes it today and when using ArcadeDB fusion/full-text features the store does not yet expose.
3. Drive and measure a set of **store improvements** (§6), each landed separately, with a before/after baseline.
4. Produce results that a human can read to decide on an upgrade and publish alongside it.

## 3. Non-goals

- LLM answer generation / answer-quality evaluation (future extension; see §13).
- Sparse (SPLADE) vectors as a hybrid source — needs a sparse embedding model LangChain4j does not ship.
- Comparing ArcadeDB with other embedding stores (the design must not preclude it; see §8.1).
- An automated pass/fail upgrade gate (humans decide from the report).
- CI integration beyond the `smoke` profile.
- Merging the benchmark module or these documents into the upstream `langchain4j/langchain4j-community` repository.

## 4. Users and use cases

- **Integration maintainer** runs the `canonical` profile for ArcadeDB version A and version B on the reference machine, then runs `compare` to produce a Markdown report for the upgrade PR.
- **Store contributor** runs `smoke` locally while developing a store improvement, then `canonical` for before/after numbers.
- **Hybrid-search investigator** runs the `extended` profile to sweep fusion/BM25 options and pick the "hybrid, tuned" configuration.

## 5. Settled decisions (summary)

| # | Decision |
|---|---|
| D1 | Purpose: repeatable regression suite; primary axis = ArcadeDB version; hybrid search is first-class |
| D2 | Retrieval only (no generation) |
| D3 | Accuracy = ANN recall@k vs exact brute-force **and** IR quality (nDCG@10, Recall@10/100, MRR@10) vs qrels |
| D4 | Dataset: BEIR **NQ** (pluggable: any BEIR-format corpus) |
| D5 | Tiers: `smoke` 100k, `standard` 1M, `full` 2.68M |
| D6 | Embedding model: `bge-small-en-v1.5` (full precision, 384-d), in-process ONNX, embedded once and cached |
| D7 | Deployment modes: embedded and remote (Docker), side by side |
| D8 | Store fixes in scope as separate requirements/PRs; baseline measured on unmodified store |
| D9 | Version switch: single Maven property `arcadedb.version` drives jar and Docker tag |
| D10 | Speed: ingestion, single-client latency, concurrent throughput, recall-vs-latency curve; mixed read/write optional |
| D11 | Metadata-filtered search via synthetic `bucket` field at 1/10/50% selectivity (standard tier) |
| D12 | Pinned index config + small grid (maxConnections × quantization) |
| D13 | New unreleased module `benchmarks/langchain4j-community-rag-benchmark`, `-Pbenchmarks` |
| D14 | All-Java toolchain (prep, embedding, ground truth, benchmark, report) |
| D15 | JSON results per run; `compare` → Markdown + SVG; warn-only thresholds |
| D16 | 3 repetitions, warm-up + cold pass, pinned JVM, noise-band significance |
| D17 | Hybrid: three baselines + one-factor-at-a-time variant sweep → "hybrid, tuned" |
| D18 | No gate; human decision |
| D19 | Run profiles `smoke` / `canonical` / `extended`; canonical ≤ ~8 h on reference machine |

## 6. Store improvement requirements (`ArcadeDBEmbeddingStore`)

Each item is an independent PR against the ArcadeDB module. **Before any of them land, the benchmark records a baseline run of the unmodified store** (with whatever workarounds are needed to finish ingestion — see §9.2) so before/after deltas are measurable. All changes keep the existing public API and default behaviour; new capabilities are opt-in builder options.

Line references are to `ArcadeDBEmbeddingStore.java` at commit `7de83f5`.

| ID | Requirement | Current behaviour / evidence |
|---|---|---|
| S1 | **Batched embedded ingestion.** `addAll` inserts in chunked transactions (configurable batch size, e.g. 1,000–10,000), preserving upsert-by-id semantics via an indexed id lookup or a pre-pass. Optionally support deferring the HNSW graph build until after bulk load (`buildGraphNow:false` / `buildVectorGraphNow()`). | One `transaction()` per vector plus a soft-delete lookup per vector (`:488-523`, `softDeleteById :792`). |
| S2 | **Batched remote ingestion.** Many rows per request using parameterized commands (`sqlscript` with params, `INSERT … CONTENT [...]`) or the `/batch` endpoint; vectors passed as parameters, not SQL text. | One HTTP `command` per vector, vector inlined via `embeddingToSql` (`:327-358`). |
| S3 | **Correct remote top-k.** Use `SELECT expand(vector.neighbors('<idx>', ?, ?, {efSearch: ?}))` with parameters; use the returned `distance`. | `SELECT *, vector.neighbors(...) AS neighbors FROM T` is a full type scan running one ANN search per row, auto-limited to 20,000 rows by the server; the loop takes the first `maxResults` rows in scan order (`:361-415`). Results are **not** nearest neighbours; filtered results are also wrong. |
| S4 | **Per-query `efSearch`.** Builder option (default: current behaviour) and use of `findNeighborsFromVector(vec, k, efSearch)` / SQL `{efSearch:N}`. | Two-arg call (`:542`) → ArcadeDB 26.7.2 adaptive beam `max(2k,20)` above 10k nodes → low recall (upstream measured recall@10 0.92 → 0.54). Fixed upstream in 26.9.1. |
| S5 | **Remove O(N) scan fallback at scale.** `supplementFromMissedVertices` must not full-scan large types (bound it, gate it by size, or drop it on versions that fix the underlying HNSW issue). | Full `SELECT FROM T` scan when HNSW returns fewer than `fetchSize` (`:568-578`, `:645-678`). |
| S6 | **Expose index options**: `quantization` (NONE/INT8/BINARY/PRODUCT), `similarity` for embedded mode, and document `beamWidth` correctly as build-time. | Embedded similarity hardcoded `COSINE` (`:749`, also `:297` in `removeAll`); Javadoc calls `beamWidth` a search beam width (`:1107`). |
| S7 | **Expose hybrid options**: fusion method (RRF / DBSF / LINEAR), RRF `k`, per-source weights, dense fetch multiplier, bounded full-text source (`SELECT @rid, $score … ORDER BY $score DESC LIMIT N`), full-text index analyzer / `bm25_k1` / `bm25_b` / `defaultOperator`. | RRF only, defaults, unbounded full-text subquery with no `$score` (LINEAR/DBSF impossible) (`buildHybridSearchQuery :810-834`); FULL_TEXT index created without METADATA (`:455`, `:732`). |
| S8 | **Query sanitising for full-text.** Escape Lucene special characters; on parse failure fall back to dense-only rather than returning empty. | Only SQL quotes escaped (`escapeString :997`); parse errors caught and return an empty result (`:390-393`, `:637-639`). |
| S9 | **Server-side filtering.** Push metadata filters into `vector.neighbors(..., {filter: (SELECT @rid ...)})` / full-text `WHERE` where supported, instead of Java post-filtering with over-fetch. | Post-filter in Java with 5×/10× over-fetch (`:376-381`, `:406`, `:588`, `:622`). |
| S10 | **Version compatibility.** Store compiles and passes its ITs on the baseline (26.7.2) and target (latest) ArcadeDB versions; note: from 26.8.1 unknown index METADATA keys are rejected and `CREATE INDEX IF NOT EXISTS` with differing values returns HTTP 400. | Pinned to 26.7.2 (`pom.xml:15`). |

Priority for benchmark viability: **S1, S2, S3, S4** are required before `standard`/`full` numbers are meaningful; S5–S9 are required for their respective scenarios; S10 is required for the version comparison itself.

## 7. Benchmark functional requirements

### 7.1 Dataset and data preparation

- **F1.** Download BEIR NQ corpus, queries and test qrels from Hugging Face (`BeIR/nq` + `BeIR/nq-qrels`, or `mteb/nq`; ungated, CC BY-SA). Verify checksums; cache locally outside the repo.
- **F2.** Query set: NQ **test** split, 3,452 queries.
- **F3.** Tiers, deterministic by seed and stored as id lists:
  - `smoke`: 100,000 passages; `standard`: 1,000,000; `full`: all 2,681,468.
  - `smoke` and `standard` contain **every passage referenced by the test qrels**, filled with uniformly random passages (fixed seed). Smaller tiers are subsets of larger ones.
- **F4.** Passage text = `title + "\n" + text` (one passage → one vector; no further chunking). Metadata: `doc_id`, `title`, and synthetic `bucket` (see F12).
- **F5.** Dataset is a config parameter; the loader accepts any BEIR-format corpus.

### 7.2 Embeddings

- **F6.** Embed with LangChain4j in-process `bge-small-en-v1.5` (full precision, 384-d, normalized). Queries use the BGE query instruction prefix (`"Represent this sentence for searching relevant passages: "`); passages do not.
- **F7.** Embed the `full` tier once; all tiers read from the same cache. Cache format: raw little-endian float32 matrix file + ordered id file + manifest (model id, dims, normalization, prefix, dataset version, checksum).
- **F8.** Query embeddings cached the same way. Query-time embedding latency is measured separately and **never** included in store latency.
- **F9.** Embedding is resumable (checkpointed) and parallel across cores.

### 7.3 Ground truth

- **F10.** Exact top-100 neighbours (cosine) per query per tier by multithreaded brute force in Java; cached with a manifest tied to the embedding cache checksum.
- **F11.** Filtered ground truth: exact top-100 restricted to each `bucket` selectivity (standard tier).
- **F12.** Synthetic metadata `bucket`: integer 0–99 assigned by seeded hash of `doc_id`; filters `bucket < 1` (1%), `bucket < 10` (10%), `bucket < 50` (50%).

### 7.4 Scenarios

| Scenario | Description | Tiers |
|---|---|---|
| `dense` | Vector search only, explicit `efSearch`, sweep `efSearch` ∈ {50, 100, 200, 400, 800} | all |
| `lexical` | BM25 full-text only (via ArcadeDB `SEARCH_INDEX` with `$score`) | standard, full |
| `hybrid-asis` | Store's current hybrid query (RRF k=60, equal weights, unbounded FT) | all |
| `hybrid-variants` | One-factor-at-a-time from `hybrid-asis`: (i) fusion RRF/DBSF/LINEAR; (ii) RRF k ∈ {10, 60}, weights dense:text ∈ {1:1, 2:1, 1:2}; (iii) bounded FT source; (iv) EnglishAnalyzer vs StandardAnalyzer; (v) query sanitising on/off | standard |
| `hybrid-tuned` | Best-nDCG@10 variant from `hybrid-variants`, frozen in config | all |
| `filtered` | `dense` and `hybrid-tuned` with `bucket` filters at 1/10/50% | standard, embedded |
| `index-grid` | maxConnections ∈ {16, 32} × quantization ∈ {NONE, INT8}, `dense` scenario | standard |
| `mixed` (optional) | Queries at fixed QPS while inserting new vectors | standard |

Pinned canonical index configuration: `maxConnections=16`, `beamWidth=100`, `quantization=NONE`, `similarity=COSINE`. Each run also records the version's own defaults.

### 7.5 Metrics

**Accuracy**
- **M1.** ANN recall@10 and recall@100 vs brute-force ground truth (dense and filtered scenarios).
- **M2.** nDCG@10, Recall@10, Recall@100, MRR@10 vs NQ test qrels (all scenarios). Computed with standard BEIR/trec_eval definitions; validated against a known reference (see acceptance criteria).
- **M3.** Result-count shortfall: fraction of queries returning fewer than k results (filtered/hybrid).
- **M4.** Query failure rate (exceptions, full-text parse errors, empty results).

**Speed**
- **M5.** Ingestion: vectors/s, total wall time, time until index is fully built and searchable, final on-disk size, peak heap / RSS.
- **M6.** Single-client latency: p50/p95/p99/max per scenario, after warm-up; cold first-pass latency reported separately.
- **M7.** Throughput: QPS and p99 at concurrency 1, 4, 16, 64.
- **M8.** Recall-vs-latency curve: recall@10 vs p95 across the `efSearch` sweep.
- **M9.** Query-embedding latency (reported separately, informational).

### 7.6 Execution

- **F13.** One Maven property `arcadedb.version` selects both the ArcadeDB jars (embedded) and the Docker image tag `arcadedata/arcadedb:<version>` (remote). A compile failure against a version is recorded as a finding.
- **F14.** Run profiles selected by one flag:
  - `smoke` (~15 min): smoke tier, embedded, `dense` (single efSearch) + `hybrid-asis`, 1 repetition.
  - `canonical` (target ≤ ~8 h per version on the reference machine): standard tier in embedded **and** remote with pinned config — `dense` (with sweep), `lexical`, `hybrid-asis`, `hybrid-tuned`, `filtered` (embedded); plus full tier embedded — `dense` (single efSearch) and `hybrid-tuned`. 3 repetitions of query phases.
  - `extended` (opt-in): canonical + `index-grid` + `hybrid-variants` + full tier remote + optional `mixed`.
- **F15.** A loaded database is reused across all query scenarios for the same (tier, mode, version, index config); loads happen once per such tuple. Loaded databases may be cached between runs keyed by that tuple plus store commit.
- **F16.** Warm-up pass over the query set before timed passes; each timed query phase repeated 3× (`standard`/`full`), 1× (`smoke`); report median and min/max spread.
- **F17.** Remote mode: server in Docker on the same host, container CPU-pinned (cpuset) separate from the client; configurable heap for both.
- **F18.** ArcadeDB per-search INFO logging suppressed (26.7.2 logs every search in `LSMVectorIndex`).
- **F19.** The harness waits for the vector graph build/rebuild to complete before timing queries and records how long that took.

### 7.7 Results and reporting

- **F20.** Each run writes a JSON result file containing: all metrics per scenario/repetition, full config (profile, tier, mode, ArcadeDB version, index/hybrid settings, seeds), environment (CPU model, cores, RAM, disk, OS, JDK, JVM flags, heap, Docker version), git commit of the store and benchmark, dataset/embedding/ground-truth manifests.
- **F21.** Results stored under `benchmarks/results/<yyyy-MM-dd>-<arcadedb-version>-<profile>/` (git-ignored by default; curated reports committed manually).
- **F22.** `compare` command: input ≥2 result files → Markdown report with delta tables, recall-vs-latency SVG charts, dense vs lexical vs hybrid quality tables, and per-metric noise bands. A latency delta smaller than the repetition spread is shown as "no significant change".
- **F23.** Configurable warn-only regression thresholds (defaults: recall −1 pt, nDCG@10 −1 pt, p95 +10%, ingestion +10%) flagged in the report; never fail the build.

## 8. Non-functional requirements

- **N1. Reproducibility.** Same inputs (dataset version, seeds, embedding cache, config, ArcadeDB version, hardware) → accuracy metrics identical; latency within noise band.
- **N2. Reference machine.** ≥16 physical cores, ≥64 GB RAM, local NVMe SSD, ≥200 GB free disk, Linux, Docker, JDK 21. Results record the actual machine; cross-machine comparisons are flagged in the report.
- **N3. Isolation.** `-Xms = -Xmx`, fixed GC, GC logging on; no other heavy workload during runs.
- **N4. Resumability.** Long phases (download, embedding, ground truth, ingestion) checkpoint and resume.
- **N5. Build hygiene.** Benchmark module excluded from default reactor, not deployed/released, no new dependencies leaking into published modules.
- **N6. Licensing.** NQ is CC BY-SA; published reports cite BEIR/NQ.

### 8.1 Architecture constraints

Deep modules with narrow interfaces, so a second store could be added later without touching data/metrics code:

- **Dataset** — BEIR loader, tier sampler (knows nothing about stores).
- **EmbeddingCache** — builds/reads vector + id files for corpus and queries.
- **GroundTruth** — brute-force exact kNN (plain and filtered), cached.
- **BenchmarkTarget** (interface) — `load(tier)`, `search(request)`, `stats()`, `close()`; implementation `ArcadeDbTarget` (embedded/remote) wraps `ArcadeDBEmbeddingStore` — all queries go through the LangChain4j `EmbeddingStore` API, not raw SQL, except where a scenario explicitly measures a not-yet-exposed ArcadeDB feature (e.g. early `lexical`/`hybrid-variants` before S7), which must be labelled as such.
- **Runner** — profiles, repetitions, warm-up, concurrency, timing.
- **Metrics / Report** — IR metrics, ANN recall, latency stats, JSON writer, `compare` Markdown/SVG generator.

## 9. Constraints, risks and mitigations

| Risk | Mitigation |
|---|---|
| R1. Unmodified store too slow to ingest 1M vectors (1M transactions / HTTP calls) for the baseline | Baseline on unmodified store runs at `smoke` tier fully and at `standard` tier with a recorded time cap; if the cap is hit, record extrapolated throughput and mark the baseline as partial. |
| R2. New ArcadeDB versions break compilation (store uses `LSMVectorIndex` internals) or reject index METADATA (26.8.1+) | S10; record compile/IT failures as findings. |
| R3. 26.7.2 graph rebuilds every 100 mutations / 15 s idle distort ingestion and first-query latency | Measure time-to-searchable separately (F19); optionally defer graph build (S1); report version difference as a finding. |
| R4. BM25 IDF per bucket in 26.7.2 skews lexical scoring | Record bucket count; note in report; compare with 26.8.1+. |
| R5. Embedding 2.68M passages on CPU takes hours | One-time, checkpointed, cached; reused across all versions/runs. |
| R6. Latency noise hides small differences | 3 repetitions, noise bands, pinned JVM/CPU (F16, F17, N3). |
| R7. Memory: ~1.5 GB raw vectors/M plus heap copies during ingestion on 26.7.2 | Reference machine 64 GB; configurable heap; record peak heap. |

## 10. Acceptance criteria

1. `smoke` profile completes end-to-end in ≤ ~15 min on the reference machine and produces a valid JSON result.
2. Dense nDCG@10 on the `full` tier with high `efSearch` is within ±1.5 pts of the published `bge-small-en-v1.5` BEIR NQ score (MTEB), validating embedding + metrics pipeline.
3. At maximum `efSearch`, dense ANN recall@10 ≥ 0.95 on `standard` (after S3/S4), validating ground truth and target wiring.
4. Brute-force ground truth for a sample of queries matches an independent exact computation (e.g. an in-memory LangChain4j store on a 10k subset).
5. `canonical` completes for ArcadeDB 26.7.2 and the latest release within ~8 h each on the reference machine, both modes.
6. `compare` produces a Markdown report with delta tables, noise bands and SVG charts from two canonical results.
7. Baseline (unmodified store) run recorded and each of S1–S9 has a before/after measurement.
8. Benchmark module does not build or deploy without `-Pbenchmarks`; `./mvnw -pl embedding-stores/langchain4j-community-arcadedb verify` unaffected.

## 11. Deliverables

1. Store improvement PRs S1–S10 (upstream-eligible, independent of the benchmark).
2. `benchmarks/langchain4j-community-rag-benchmark` module (fork-only).
3. Baseline and version-comparison reports (curated, committed on the benchmark branch).

## 12. Open questions (non-blocking)

- Exact MTEB reference score to use for acceptance criterion 2 (look up at implementation time for the model revision used).
- Whether to also keep a quantized `bge-small-en-v1.5-q` variant for query-time embedding speed comparison (informational only).
- `mixed` workload parameters (insert rate, QPS) — define when that optional scenario is implemented.

## 13. Future extensions

- Generation + answer-quality evaluation (LLM judge) on top of retrieval.
- Additional BEIR datasets (HotpotQA, FiQA, SciFact) for cross-domain hybrid results.
- Larger embedding models (768/1024-d) as a vector-size dimension.
- Sparse (SPLADE) vectors as a third hybrid source.
- Additional `BenchmarkTarget` implementations for other stores in this repo.
