# Hybrid search investigation: why 26.9.1+ hybrid is slow, and how to make it fast

**Question:** with the unmodified store, hybrid search on the smoke tier went from nDCG@10 0.45 / p50 108 ms (ArcadeDB 26.7.2) to 0.79 / 544 ms (26.10.2). Why, and what would fix the latency?

**Method:** the store's hybrid SQL was taken apart and varied directly against the smoke-tier databases kept from the benchmark runs (100k NQ passages, bge-small-en-v1.5 vectors), embedded, with the matching ArcadeDB engine jars and the Java Vector API, on the reference machine. Each variant ran over the NQ test queries (cached query vectors, raw query text, test qrels), after a warm-up. The probe (`benchmarks/langchain4j-community-rag-benchmark/tools/HybridProbe.java`) reproduces the store: dense-only with the store's 400 candidates gives nDCG@10 0.8521 and the store's hybrid query 0.7941 — both exactly the benchmark's values.

The store's hybrid query (embedded):

```sql
SELECT expand(vector.fuse(
  vector.neighbors('Passage[embedding]', <vector>, 200),
  (SELECT @rid AS rid FROM Passage WHERE SEARCH_INDEX('Passage[text]', :query) = true
     AND (deleted IS NULL OR deleted != true)),
  { fusion: 'RRF', limit: 200 }))
```

## 1. Decomposition across versions (100 queries)

| | 26.7.2 | 26.9.1 | 26.10.2 |
|---|---|---|---|
| Store hybrid as is: time / nDCG@10 | 106 ms / 0.439 | 607 ms / 0.801 | 543 ms / 0.803 |
| Passages matched by the full-text query | 16,193 | 86,331 | 86,331 |
| Counting the matches | 47 ms | 339 ms | 310 ms |
| … with the store's `deleted` filter | 81 ms | 558 ms | 473 ms |
| Full-text only, top-100 by `$score`: nDCG@10 | **0.068** | 0.602 | 0.602 |
| Full-text top-200 by `$score`: time | 54 ms | 378 ms | **127 ms** |

- **26.7.2 was fast because its full-text search was broken**: lexical-only nDCG@10 0.07 and far fewer matches; the hybrid's full-text half contributed noise (hence 0.44).
- **From 26.9.1 on BM25 works** (lexical 0.60) and hybrid accuracy jumps to 0.80 — but natural-language questions match ~86 % of all passages (default `OR`, no stop words), and the store feeds every match into the fusion. Matching ~86k documents (~310 ms) and checking `deleted` on each of them (~160 ms, it loads every matched record) is almost all of the 543 ms.
- **26.10.2 makes a bounded, score-ordered full-text source cheap** (127 vs 378 ms on 26.9.1 — the release's score-ordered `SEARCH_INDEX` work).

## 2. What fixes it (26.10.2, all 3,452 test queries)

| Configuration | Time (mean / p50) | nDCG@10 |
|---|---|---|
| **Dense only** (store's 400 candidates) | 6–8 / 6 ms | **0.852** |
| Store hybrid as is (default index) | 522 / 553 ms | 0.794 |
| Bounded full-text top-200, RRF (default index) | 130 / 132 ms | 0.793 |
| Bounded top-200, LINEAR fusion (default index) | 129 / 130 ms | 0.836 |
| Bounded top-200, LINEAR, query stop words removed (default index) | 12 / 10 ms | 0.836 |
| **EnglishAnalyzer full-text index**: store hybrid as is | 101 / 92 ms | 0.807 |
| EnglishAnalyzer: bounded top-200, LINEAR | 21 / 19 ms | 0.844 |
| EnglishAnalyzer: bounded top-200, RRF weights dense:lexical 3:1 / 5:1 / 8:1 / 10:1 | 21 / 19 ms | 0.836 / 0.843 / 0.846 / 0.849 |
| **EnglishAnalyzer: dense 400 + bounded top-200, RRF weights 8:1** | **25 / 22 ms** | **0.853** |
| EnglishAnalyzer: full-text only, top-100 (lexical) | 15 / 13 ms | 0.677 (default index 0.602) |

(2 of 3,452 queries fail to parse in every full-text variant — `/` in the raw text; S8's escaping removes that. On 26.10.2 a full-text parse failure also makes ArcadeDB re-plan the statement as a full scan, so with the unmodified store such a query is slow as well as empty.)

## 3. Conclusions

1. **The slowness is the store's unbounded full-text source**, not ArcadeDB's fusion or vector search. Bounding it to the top-N by BM25 score (N ≈ the fetch count; 50–500 makes no difference) and applying the `deleted` filter *after* bounding takes hybrid from 543 to ~130 ms on 26.10.2 with no accuracy loss.
2. **Stop words are the second lever**: an `EnglishAnalyzer` full-text index (stop words + stemming) cuts the matches from 86k to 20k and hybrid to ~21–25 ms (**~22× faster than today**); it also improves lexical-only retrieval (0.60 → 0.68). The index must be (re)built with the analyzer — 8.5 s for 100k passages. Even the store's current query becomes 5× faster on such an index.
3. **Fusion decides accuracy**: equal-weight RRF (the store's only option) costs ~0.06 nDCG@10 against dense-only; LINEAR or RRF weighted towards dense recovers it. The best configuration ties dense-only (0.853 vs 0.852) — **on NQ with bge-small, hybrid does not beat dense-only**; its value is matching dense quality while adding exact-term matching (names, codes, rare words) that NQ does not measure. A 100-query sample had suggested hybrid > dense (0.871); the full set does not confirm it.

## 4. Implications for S7 (expose hybrid / full-text options) on 26.10.2

- **Bounded, score-ordered full-text source by default** (`ORDER BY $score DESC LIMIT <fetch>`; embedded: `deleted` filter on the bounded set). Same results as today within noise, ~4× faster.
- **Fusion options**: `RRF` (with `k` and per-source weights), `LINEAR`, `DBSF` (now possible because the bounded source carries `$score`).
- **Full-text index options**: analyzer (e.g. `EnglishAnalyzer`) and BM25 parameters on index creation, with a note that changing them needs an index rebuild (S10's keep-existing-index rule applies).
- Defaults to decide with the owner: keep RRF (compatible) or switch to the better-measured LINEAR/weighted RRF; keep the default analyzer (compatible) or recommend EnglishAnalyzer for English text.

Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.
