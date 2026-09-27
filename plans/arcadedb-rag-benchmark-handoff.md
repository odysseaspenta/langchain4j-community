# Handoff: ArcadeDB RAG Benchmark

Read this first when picking the work up on the implementation machine (human or Claude Code session). The requirements are in [`arcadedb-rag-benchmark-prd.md`](arcadedb-rag-benchmark-prd.md); this document carries the context, research findings and setup that the PRD does not.

## 1. Status as of 2026-09-27

- Requirements agreed with the owner through a design interview; PRD written. **No code written yet.**
- Research done on the dev box (7 cores / 13 GB RAM — too small for the real runs). Implementation and all `standard`/`full` runs happen on the more powerful reference machine (PRD N2).
- Nothing has been pushed upstream. These documents live only on the fork branch (see §2).

## 2. Git / repository strategy (keep benchmark out of upstream)

- `origin` = the owner's fork `github.com/odysseaspenta/langchain4j-community`. There is no `upstream` remote on the dev box; add it where needed:
  `git remote add upstream https://github.com/langchain4j/langchain4j-community.git`
- **`arcadedb-rag-benchmark`** — long-lived fork-only branch. Holds `plans/`, the `benchmarks/` module and curated reports. **Never open a PR from this branch to upstream.**
- **Store fixes (PRD §6, S1–S10)** — one short-lived branch per fix, created from `upstream/main` (not from the benchmark branch), containing only `embedding-stores/langchain4j-community-arcadedb` changes. These are the only things proposed upstream.
- Keep the benchmark branch current by merging `main` (after syncing the fork) and merging/cherry-picking store-fix branches into it, so the benchmark runs against unreleased fixes.
- Result data (`benchmarks/results/**`), dataset/embedding caches and ArcadeDB databases are git-ignored; only curated Markdown/SVG reports get committed on the benchmark branch.

## 3. Resuming with Claude Code

Suggested opening prompt on the new machine:

> Read `plans/arcadedb-rag-benchmark-prd.md` and `plans/arcadedb-rag-benchmark-handoff.md`. We are on branch `arcadedb-rag-benchmark`. Start with the setup checklist, then turn the PRD into a phased implementation plan (prd-to-plan) before writing code.

Owner preferences recorded during the interview: ask clarifying questions **one at a time**; plans/PRDs live as Markdown under `plans/`, not as GitHub issues.

## 4. Reference machine setup checklist

- [ ] Linux, ≥16 physical cores, ≥64 GB RAM, local NVMe, ≥200 GB free (PRD N2)
- [ ] JDK 21 (ArcadeDB module sets `maven.compiler.source/target` 21), Maven via `./mvnw`
- [ ] Docker; pre-pull `arcadedata/arcadedb:26.7.2` and `arcadedata/arcadedb:26.9.1` (tags verified to exist; 26.7.3 and 26.8.1 also exist)
- [ ] Outbound HTTPS to `huggingface.co` (ungated, no token needed for BeIR/mteb NQ) and Maven Central
- [ ] Choose a data directory outside the repo (e.g. `/data/rag-bench`) for: raw dataset (~0.8–1.5 GB), embedding cache (~4.1 GB for 2.68M × 384 float32), ground truth, ArcadeDB databases (several GB per tier/version/config)
- [ ] Record machine spec (CPU model, cores, RAM, disk) — harness writes it into every result file
- [ ] Optional: local ArcadeDB source checkout for reading version diffs (`git clone https://github.com/ArcadeData/arcadedb`), tags `26.7.2`, `26.8.1`, `26.9.1`

## 5. Suggested order of work

1. **Harness skeleton + smoke path** on the *unmodified* store: dataset download → 100k tier → embedding cache → ground truth → embedded `dense` + `hybrid-asis` → JSON → trivial report. Proves the pipeline (PRD acceptance 1, 4).
2. **Full embedding cache** (2.68M, checkpointed; start early, it runs for hours) and ground truth for all tiers.
3. **Baseline run on the unmodified store** (PRD R1): smoke fully; standard with time cap; record everything. This is the "before" for S1–S9.
4. **Store fixes S1–S4** (each its own upstream-eligible branch), merge into benchmark branch, validate acceptance 2 and 3.
5. Remote mode + `lexical`, `filtered`, concurrency, efSearch sweep → `canonical` profile.
6. **S10** version compatibility; run `canonical` on 26.7.2 and 26.9.1 (or latest); build `compare` report.
7. S5–S9, `extended` profile, `hybrid-variants` sweep → freeze `hybrid-tuned`.

## 6. Research findings to carry forward

Sources: ArcadeDB source at tags `26.7.2`/`26.8.1`, ArcadeDB release notes, docs.arcadedb.com, HF Hub API, BEIR paper (arXiv 2104.08663). Line numbers refer to ArcadeDB 26.7.2 unless stated.

### 6.1 Dataset

| | BEIR NQ |
|---|---|
| HF ids | `BeIR/nq` (corpus + queries, parquet, 764 MB) + `BeIR/nq-qrels` (TSV); or `mteb/nq` (jsonl + `qrels/test.tsv`, 1.46 GB) |
| Corpus | 2,681,468 passages, avg 78.9 words |
| Test queries | 3,452 (queries file contains all splits — filter by test qrels ids) |
| License | CC BY-SA (BEIR Appendix E) |
| Mirror | `public.ukp.informatik.tu-darmstadt.de/thakur/BEIR/datasets/nq.zip` (md5 in BEIR README) |

- **No precomputed embeddings** exist for NQ with bge-small / MiniLM / e5-small; must embed ourselves.
- The langchain4j repos contain no sizeable document collections (checked).
- Rough CPU embedding estimate (extrapolated, not published): bge-small ~100–400 passages/s on a many-core CPU → 2.68M in roughly 2–7 h. Measure early.
- Alternatives considered: MS MARCO (8.8M, **non-commercial licence**), HotpotQA (5.2M), FEVER (5.4M), DBPedia-entity (4.6M, 400 queries).

### 6.2 ArcadeDB vector search (LSM_VECTOR, JVector HNSW)

- **Idiomatic top-k SQL**: `SELECT expand(vector.neighbors('Type[embedding]', ?, ?, {efSearch: N, filter: (SELECT @rid ...), maxDistance: d}))`. Rows carry properties, `@rid`, `distance`.
- The store's remote form `SELECT *, vector.neighbors(...) AS neighbors FROM T` was **experimentally confirmed** (2,000 vectors, embedded, 26.7.2) to return scan-order rows and run one ANN search per row; the server auto-appends `LIMIT 20000` (`PostCommandHandler.java:84,123-138`).
- Distance for COSINE = `1 − cos` ∈ [0,2]; store's `score = 1 − d/2` is correct.
- Index METADATA (26.7.2, `LSMVectorIndexMetadata.java`): `dimensions`, `similarity` (COSINE/DOT_PRODUCT/EUCLIDEAN), `quantization` (NONE/INT8/BINARY/PRODUCT), `encoding`, `maxConnections` (16; **32 from 26.8.1**), `beamWidth` (100, build-time only), `efSearch` (100, but METADATA value **ignored until 26.8.1**), `neighborOverflowFactor`, `alphaDiversityRelaxation`, `pq*`, `buildGraphNow` (CREATE INDEX only).
- Per-query `efSearch`: SQL 4th arg or `{efSearch:N}`; Java `findNeighborsFromVector(vec, k, efSearch)` (`LSMVectorIndex.java:2813,2843`). Without it, 26.7.2 uses `max(2k,20)` above 10k nodes (`:2948`) — fixed in **26.9.1** (#6494).
- 26.7.2 graph maintenance: inserts go to an in-heap delta list (brute-force searched); full graph rebuild after `mutationsBeforeRebuild`=100 or 15 s idle; ≥1000 nodes rebuilds in background with hot-swap; 1 concurrent rebuild JVM-wide. Deferred build via `buildGraphNow:false`, creating the index after load, or `buildVectorGraphNow()` / `REBUILD INDEX`.
- 26.7.2 logs INFO on **every** search (`LSMVectorIndex.java:~2960`) — set `com.arcadedb.index.vector` logger to WARNING. Removed in 26.9.1 (#6559).
- Memory estimate at 1M × 384: 1.5 GB raw + up to 1.5 GB delta copies during ingest + ~80 MB graph + build cache; plan 4–6 GB heap per million. INT8 ≈ ¼ vector storage.

### 6.3 Bulk ingestion options

- Embedded: large transactions; `database.async()` (`setParallelLevel`, `setCommitEvery`, `setTransactionUseWAL(false)`, `waitCompletion`); or load then create index (`LSMVectorIndex.build()` bulk-loads with WAL off).
- Remote: parameterized `command("sqlscript", script, params)` inside `begin()/commit()`; `INSERT … CONTENT [ {...}, ... ]`; streaming `POST /batch` (JSONL/CSV; Java `RemoteDatabase.batch()` / `RemoteGraphBatch`); gRPC `BulkInsert`/`InsertStream` (`arcadedb-grpc-client`).

### 6.4 Hybrid search / full-text

- Store hybrid = `vector.fuse(vector.neighbors(idx, v, N), (SELECT @rid AS rid FROM T WHERE SEARCH_INDEX('T[text]', q) = true), {fusion:'RRF', limit:N})`, N = 2×k (10×k with filter).
- `vector.fuse` (since 26.5.1): `fusion` RRF | DBSF | LINEAR, `k` (60), `weights`, `groupBy`, `groupSize`, `limit`. LINEAR/DBSF need `score`/`$score`/`distance` on every source row — the store's FT subquery projects none, so only RRF works today. **No new fusion features after 26.7.2.**
- Full-text: native **BM25** default since 26.7.1, `$score` exposed; METADATA `similarity` (BM25/CLASSIC), `bm25_k1` (1.2), `bm25_b` (0.75), `analyzer` (default `StandardAnalyzer`, no stemming), `defaultOperator` (OR), per-field analyzers/boosts. Queries go through Lucene `QueryParser` → special chars (`: ( ) " - + * ? ~ ^`) are syntax; parse error → store returns empty.
- 26.7.2 BM25 IDF computed **per bucket**; comparable across buckets from **26.8.1** (#5267).
- 26.8.1: index METADATA keys validated (unknown → rejected; `IF NOT EXISTS` with different values → HTTP 400); reusable searchers/vector cache; incremental ingestion at scale (#5391).
- 26.9.1: adaptive efSearch fix (#6494); pre-filter plan for RID allow-lists ≤20% selectivity (#6502, #6514); parallel graph build (#5577, `arcadedb.vectorIndex.graphBuildParallelism`); rebuild no longer blocks first query (#6655…); grouped-search fix (#5761).
- Also available but out of scope: `LSM_SPARSE_VECTOR` + `vector.sparseNeighbors`, `vector.mmr`, `vector.rerank`.

## 7. Store code pointers (commit `7de83f5`)

File: `embedding-stores/langchain4j-community-arcadedb/src/main/java/dev/langchain4j/community/store/embedding/arcadedb/ArcadeDBEmbeddingStore.java`

| What | Lines |
|---|---|
| `addAllRemote` (per-vector HTTP) | 327–358 |
| `searchRemote` (scan-order bug) | 361–415 |
| `initRemoteSchema` (index DDL) | 441–474 |
| `addAllEmbedded` (per-vector tx) | 488–523 |
| `searchEmbedded` (2-arg ANN call, fetchSize) | 526–580 |
| `searchEmbeddedHybrid` | 582–643 |
| `supplementFromMissedVertices` (O(N) scan) | 645–678 |
| `initEmbeddedSchema` (hardcoded COSINE) | 702–757 |
| `softDeleteById` | 792–806 |
| `buildHybridSearchQuery` | 810–834 |
| `escapeString` | 997 |
| Builders (remote / embedded) | 1001–end |

Existing tests: `ArcadeDBEmbeddingStoreIT` (remote, Testcontainers), `ArcadeDBEmbeddingStoreEmbeddedIT`, removal ITs, `ArcadeDBEmbeddingStoreHybridSearchTestHelper`. Test dep already includes `langchain4j-embeddings-all-minilm-l6-v2-q`; the benchmark needs `langchain4j-embeddings-bge-small-en-v15`.

## 8. Things deliberately left open

See PRD §12. Also: exact module/package names and CLI shape are implementation choices — keep the PRD §8.1 module boundaries.
