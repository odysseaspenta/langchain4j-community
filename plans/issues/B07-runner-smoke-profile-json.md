# B07 — Runner, `smoke` profile, JSON results, trivial report

| | |
|---|---|
| Phase | 1 — Smoke path |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B02, B03, B04, B05, B06 |
| PRD | D15, D16, F14 (smoke), F16, F20, F21, M6, N1, N3, acceptance 1 |

## Scope
- Runner: profile → list of (tier, mode, scenario, params); load once per (tier, mode, version, index config); warm-up pass; timed passes × repetitions; cold first-pass latency recorded separately.
- `smoke` profile: smoke tier, embedded, `dense` (single efSearch — note: the unmodified store cannot pass efSearch, record "store default") + `hybrid-asis`, 1 repetition.
- JSON result writer (F20): metrics per scenario/repetition, full config, seeds, environment (CPU model, cores, RAM, disk, OS, JDK, JVM flags, heap, Docker version), git commit of store and benchmark (+ dirty flag), dataset / embedding / ground-truth manifests, and machine class `reference` | `dev` (PRD N2a; set explicitly by config, default `dev`).
- Output dir `benchmarks/results/<yyyy-MM-dd>-<arcadedb-version>-<profile>/`.
- JVM launch settings for runs: `-Xms=-Xmx`, fixed GC, GC logging (N3) — e.g. a wrapper script or exec plugin config.
- Trivial report: single-run Markdown summary table from the JSON.

## Acceptance
- One command runs prepare → embed (smoke) → ground truth → smoke profile → JSON → Markdown summary.
- Accuracy metrics identical across two consecutive runs (N1).
- Acceptance 1 (≤ ~15 min) measured on the reference machine once available.

## Milestone
**M1 — smoke path proven on the unmodified store** (handoff §5 step 1).

## Outcome (2026-09-29) — code complete, real smoke run deferred
- `rag-bench run --profile smoke [--load-batch-size 1000] [--load-time-cap-minutes n] [--threads n] [--keep-databases]` runs the whole path: `prepare` → `embed` (tier + queries) → `ground-truth` → load → scenarios → `result.json` + `report.md` in `benchmarks/results/<yyyy-MM-dd>-arcadedb-<version>-<profile>[-n]/`. Cached steps are no-ops. Databases go to `<dataDir>/databases/arcadedb-<version>/<tier>-<mode>/` and are deleted after the run unless `--keep-databases`.
- `runner`: `ProfilePlan` (smoke = smoke tier, embedded, `dense` + `hybrid-asis`, k = 100, 1 repetition; canonical/extended exit 2 naming B16/B20), `Scenario` (name, mode, `bucketsBelow`, efSearch), `Runner` (one load per tier × mode; per scenario a cold pass that doubles as warm-up, then timed repetitions; accuracy from the first timed repetition; `stable` flag if any repetition returns different rankings).
- Per scenario: nDCG@10 / Recall@10 / Recall@100 / MRR@10 vs qrels; ANN recall@10/@100 vs exact neighbours (dense and filtered scenarios; `null` for hybrid); shortfall; failed / empty counts with up to 3 failure messages; cold latency; per-repetition latency; p50/p95/p99 spread across repetitions; `efSearchRequested` / `efSearchApplied` (false on the unmodified store — recorded, the store default is used).
- `result.json` (F20): profile, machine class, timestamps, environment (hostname, CPU model, cores, RAM, data-dir filesystem size/free, OS, JDK, JVM args, max heap, Docker version, N3 isolation warnings), git commit + dirty flags (repo and store module), run config, input checksums (dataset manifest, tier files, passage/query embeddings, ground-truth sets used, query-embedding latency), per load the target's config/capabilities (effective index + version defaults) and `LoadStats`.
- Machine class (N2a): `--machine-class` / `RAG_BENCH_MACHINE_CLASS`, default `dev`; the report flags dev runs as not comparable.
- `report.md`: single-run summary (run info, load table, scenario table). Comparisons are B15.
- N3 launcher `benchmarks/langchain4j-community-rag-benchmark/rag-bench`: `-Xms = -Xmx` (`RAG_BENCH_HEAP`, default 8g), G1, `AlwaysPreTouch`, GC log copied into the run directory; uses `$JAVA_HOME/bin/java`. Runs without it get isolation warnings in the result.
- Tests (69 total): runner end-to-end on real embedded ArcadeDB with fake embeddings (4 scenarios incl. filtered and an efSearch the store cannot apply), identical accuracy on consecutive runs (N1), JSON round-trip and Markdown content, isolation warnings, CLI profile errors.

### Deferred to the reference machine
- [ ] `./rag-bench --machine-class reference run --profile smoke` on real NQ (needs the 100k smoke embeddings from B03) — **M1 milestone**.
- [ ] Acceptance 1: smoke run ≤ ~15 min excluding the one-time embedding.
- [ ] N1 on real data: two consecutive smoke runs give identical accuracy metrics.
