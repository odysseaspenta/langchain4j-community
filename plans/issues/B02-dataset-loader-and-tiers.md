# B02 — BEIR dataset loader, tier sampler, `bucket` metadata

| | |
|---|---|
| Phase | 1 — Smoke path |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B01 |
| PRD | D4, D5, F1–F5, F12, N4, N6 |

## Scope
- Download BEIR NQ from Hugging Face (`BeIR/nq` + `BeIR/nq-qrels`, or `mteb/nq`), ungated. Resumable download, checksum verification, cached in the data dir. Record dataset source + revision + checksums in a manifest.
- Generic BEIR-format loader (corpus / queries / qrels) — dataset is a config parameter (F5); nothing NQ-specific in the loader.
- Query set = test split only: filter the queries file by ids present in test qrels → expect **3,452** queries.
- Passage text = `title + "\n" + text`; metadata `doc_id`, `title`, `bucket`.
- `bucket` = integer 0–99 from a seeded hash of `doc_id` (F12).
- Tier sampler: `smoke` 100k, `standard` 1M, `full` 2,681,468. Smoke and standard include every passage referenced by test qrels, filled with uniformly random passages (fixed seed); `smoke ⊂ standard ⊂ full`. Persist each tier as an ordered id list with a manifest (seed, counts, checksum).

## Acceptance
- Corpus count 2,681,468; test queries 3,452; every qrels passage present in every tier.
- Re-running with the same seed produces byte-identical tier files.
- `bucket` distribution ~uniform (1% / 10% / 50% thresholds within tolerance on each tier).
- Unit tests on a tiny synthetic BEIR corpus (nesting, qrels inclusion, determinism).

## Notes
Handoff §6.1: the queries file contains all splits; HF parquet (764 MB) vs mteb jsonl (1.46 GB) — pick one, keep the other as fallback. Cite BEIR/NQ (CC BY-SA) in reports (N6).

## Outcome (2026-09-29)
- Source: `mteb/nq` jsonl at revision `b84726e65fd226125cf7c0cbeeb5c214d49e8187` (Java-friendly; the BeIR parquet would need Hadoop/Parquet libraries). All three files pinned by size + SHA-256 in `DatasetSource.NQ`; queries file holds only the 3,452 test queries; qrels: 4,201 pairs, all score 1, one relevant passage each.
- `rag-bench prepare` on the dev box: 1.46 GB download (resumed correctly after an interrupt, through the HF CDN redirect) + verification + sampling in ~20 s of processing.
- Tier layout: `priority.ids` (relevant first, rest shuffled) whose prefixes are the tiers — this is the order B03 must fill the embedding cache in; `<tier>.ids` = members shuffled again per tier = store load order, so relevant passages are spread through the index (mean position ≈ 50k of 100k in smoke) rather than inserted first.
- Checksums on seed 42: smoke `a0314d6f…f2a8`, standard `8c555193…bf25`, full `6e2a47f0…f55b`; regenerating produces byte-identical files.
- Verified independently (Python) on the real data: all 4,201 relevant passages in every tier; tiers nest and equal priority-order prefixes; `bucket` fractions smoke 1.06% / 10.01% / 49.99%, standard 0.98% / 9.99% / 50.02%.
- `bucket` algorithm (FNV-1a 64 → SplitMix64 → floorMod 100) is pinned by a golden-value test cross-checked against an independent implementation.
- Not built: `--local-dir` for arbitrary BEIR folders. Adding a dataset = adding a `DatasetSource` constant (loader is generic, F5).
- Logging: excluded `slf4j-jdk14` (shipped by arcadedb-engine) so `slf4j-simple` is the only SLF4J provider; ArcadeDB itself still logs via JUL (relevant for F18 in B06).
