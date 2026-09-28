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
