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
- JSON result writer (F20): metrics per scenario/repetition, full config, seeds, environment (CPU model, cores, RAM, disk, OS, JDK, JVM flags, heap, Docker version), git commit of store and benchmark (+ dirty flag), dataset / embedding / ground-truth manifests.
- Output dir `benchmarks/results/<yyyy-MM-dd>-<arcadedb-version>-<profile>/`.
- JVM launch settings for runs: `-Xms=-Xmx`, fixed GC, GC logging (N3) — e.g. a wrapper script or exec plugin config.
- Trivial report: single-run Markdown summary table from the JSON.

## Acceptance
- One command runs prepare → embed (smoke) → ground truth → smoke profile → JSON → Markdown summary.
- Accuracy metrics identical across two consecutive runs (N1).
- Acceptance 1 (≤ ~15 min) measured on the reference machine once available.

## Milestone
**M1 — smoke path proven on the unmodified store** (handoff §5 step 1).
