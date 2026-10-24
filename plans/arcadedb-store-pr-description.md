feat(arcadedb): correctness, performance and configuration improvements for the ArcadeDB embedding store

## Summary

This PR fixes a set of correctness and performance problems in `ArcadeDBEmbeddingStore`, in both embedded and remote
(HTTP) mode, and adds builder options for ingestion, hybrid search, the vector index and metadata filtering. The
problems were found by running a RAG benchmark against the store: BEIR Natural Questions (100k passages, 3,452 test
queries), embeddings from `bge-small-en-v1.5`, k = 100, embedded and remote (ArcadeDB in Docker). It also updates
ArcadeDB to 26.10.2; the store is tested on 26.10.2 and 26.9.1.

The change is limited to `embedding-stores/langchain4j-community-arcadedb`. The commits are one per fix and can be
reviewed one at a time.

## Fixes

- **Remote dense search returned records in scan order, not the nearest neighbours.** The query projected
  `vector.neighbors()` per record of the type, so it ran one ANN search per record and timed out on 100k records.
- **Values were inlined into SQL.** Text with line breaks or quotes failed to insert remotely, and filter values were
  open to injection. All values are now bound as parameters.
- **Re-opening a remote store failed on ArcadeDB 26.8.1+**, where `CREATE INDEX IF NOT EXISTS` rejects an existing
  index with different settings. Existing indexes are now kept, as in embedded mode.
- **Lucene syntax in hybrid queries** (`?`, `:`, `(`, …) made hybrid search fail; on 26.10.2 a query that fails to
  parse is re-planned as a full scan. Query text is escaped (`rawFullTextQuery(true)` keeps Lucene syntax), and a
  failing hybrid query falls back to dense search.
- **`addAll` with existing ids failed**, and an id that had been removed could not be added again. `addAll` is now an
  upsert by id in both modes.
- **Selective metadata filters returned a fraction of the results.** The store fetched a fixed number of candidates
  and filtered them in Java: with a filter allowing 1 % of the records it returned about 4 of 100 results (some queries
  none), with 10 % about 40. Filtered searches now return complete results (see `filterStrategy`).
- **Float metadata filters in remote mode** (`isEqualTo(1.1f)`, ranges, and `removeAll(filter)`) missed or wrongly
  matched stored floats, because a `Float` parameter was sent as its shortest decimal. Floats are bound as their exact
  double value.
- **Scores for non-cosine indexes**: remote mode scored by cosine whatever the index function, and embedded mode
  applied the cosine conversion to any distance. Scores now follow the index's similarity function.
- **Remote rows were sent twice**: rows of `expand(vector.neighbors(...))` and `expand(vector.fuse(...))` carry the
  record's properties and the whole record again under `record`. Remote queries now drop the copy.

## Performance

- **Batched ingestion**: one transaction per batch in embedded mode, one request and transaction per batch in remote
  mode (`sqlscript`).
- **Hybrid search**: the full-text side was unbounded. A natural-language query matches most documents under Lucene's
  default OR, so fusing all of them dominated the query time. The full-text side is now bounded to its best matches by
  BM25 score.
- **Remote dense search** fetches only `k` rows and widens the search beam instead, and `vector.fuse` returns only the
  rows the store uses.
- **Filtered search** in remote mode runs as one server-side query with an allow-list instead of fetching 5k full rows.
- **No full-table scans** at search time or when an embedded store is opened. The store's own supplement scan for
  vectors the HNSW graph cannot reach is removed: ArcadeDB completes short graph results with a brute-force scan
  itself (checked on 26.7.2, 26.9.1 and 26.10.2).

## New builder options

| Option | Builders | Default |
|---|---|---|
| `batchSize` | both | 1,000 embedded, 500 remote |
| `rawFullTextQuery` | both | `false` (query text is escaped) |
| `hybridFusion` (`RRF`, `LINEAR`, `DBSF`) | both | `RRF` |
| `hybridWeights(dense, fullText)` | both | 8 : 1 |
| `hybridRrfK` | both | 60 |
| `fullTextAnalyzer` | both | `org.apache.lucene.analysis.en.EnglishAnalyzer` |
| `fullTextBm25K1`, `fullTextBm25B` | both | ArcadeDB's defaults |
| `efSearch` | both | embedded: ArcadeDB's default; remote: a beam equivalent to embedded search |
| `quantization` (`NONE`, `INT8`, `BINARY`, `PRODUCT`) | both | `NONE` |
| `similarityFunction` (`COSINE`, `EUCLIDEAN`, `DOT_PRODUCT`) | embedded (remote already had it) | `COSINE` |
| `filterStrategy` (`ADAPTIVE`, `PRE_FILTER`, `POST_FILTER`) | both | `ADAPTIVE` |

Index settings (analyzer, BM25, quantization, similarity) apply when an index is created; existing indexes keep their
settings.

## Behaviour changes

- **Hybrid search defaults**: RRF weighted 8 : 1 towards the dense ranking, and an `EnglishAnalyzer` (stop words and
  stemming) for **newly created** full-text indexes. These were the best settings measured on the benchmark. The
  analyzer is English-specific: applications with text in other languages should set `fullTextAnalyzer`.
- **`addAll` replaces records with an existing id** instead of failing.
- **Filtered searches return complete results** with the default `ADAPTIVE` strategy: remote mode filters on the
  server; embedded mode filters the nearest candidates first and repeats the search with an allow-list when too few
  pass. `POST_FILTER` restores the previous behaviour. `ContainsString`, which has no SQL mapping, is still applied
  after the search.
- **Unsupported similarity names are rejected** when the store is built (the Javadoc listed `SQUARED_EUCLIDEAN`, which
  ArcadeDB does not support).
- **ArcadeDB 26.10.2** is the default version. It needs `slf4j-api` 2.0.20, so the module sets `slf4j.version` for the
  enforcer's dependency-convergence rule.

## Measurements

Benchmark smoke tier: 100k NQ passages, 3,452 queries, k = 100, ArcadeDB 26.10.2; remote mode against ArcadeDB in
Docker (4 CPUs, 8 GB heap), client on 4 other CPUs. Medians of three repetitions after a cold pass.

| | Before | After |
|---|---|---|
| Embedded load | 3,137 docs/s | 8,263 docs/s |
| Remote load | 576 docs/s | 2,784 docs/s |
| Embedded dense: nDCG@10 / p50 | 0.852 / 3.3 ms | 0.853 / 3.3 ms |
| Embedded hybrid: nDCG@10 / p50 | 0.794 / 544 ms | 0.855 / 20.4 ms |
| Remote dense: nDCG@10 / p50 | every query timed out | 0.851 / 19.1 ms |
| Remote hybrid: nDCG@10 / p50 | 0.793 / 477 ms | 0.853 / 34.7 ms |

Filtered dense search (ANN recall@100 against the filtered exact neighbours / p50):

| Filter passes | Embedded before | Embedded after | Remote before | Remote after |
|---|---|---|---|---|
| 1 % | 0.043 / 3.5 ms | 1.000 / 13.9 ms | 0.053 / 73.5 ms | 1.000 / 27.8 ms |
| 10 % | 0.396 / 3.5 ms | 1.000 / 22.6 ms | 0.495 / 73.5 ms | 1.000 / 36.9 ms |
| 50 % | 0.973 / 3.4 ms | 0.973 / 3.5 ms | 0.973 / 73.5 ms | 0.990 / 49.3 ms |

Filtered hybrid search now returns `k` results at every selectivity (before: 100 % of the queries short at 1 %,
about half at 10 %), at 36–64 ms embedded and 44–67 ms remote (before: about 33 ms and 166 ms).

## Testing

- Module `verify` with the default ArcadeDB version (26.10.2) and with `-Darcadedb.version=26.9.1`: 505 tests pass,
  4 skipped, in both.
- The LangChain4j filter suite (`EmbeddingStoreWithFilteringIT`) also runs with `PRE_FILTER` in both modes, so every
  filter type is tested in SQL as well as in Java.
- New tests for each fix, among them: remote nearest neighbours rather than scan order; text with line breaks and
  quotes; re-opening a remote store; Lucene syntax in hybrid queries; upserts and re-adding removed ids; each fusion,
  analyzer, similarity function and quantization; efSearch never lowering recall; selective filters returning all
  matches; float filters in remote `removeAll`; identical vectors all returned.

## Not in this PR

- Documentation of the new options on the LangChain4j documentation site (separate repository).
- Embedded hybrid search with a selective filter first tries the cheaper post-filter (~33 ms) before the filtered
  query; choosing the strategy from the first attempt's pass rate could save that.
