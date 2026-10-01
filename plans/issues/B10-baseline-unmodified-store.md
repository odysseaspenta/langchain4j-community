# B10 — Baseline run on the unmodified store

| | |
|---|---|
| Phase | 3 — Baseline |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B07, B08, B09 |
| PRD | D8, §6 preamble, R1, R3, acceptance 7 |

## Scope
- Store at `7de83f5` (no S-fixes), ArcadeDB 26.7.2.
- `smoke` tier fully: embedded + remote, `dense` (store default efSearch) + `hybrid-asis`.
- `standard` tier with a recorded load time cap (embedded + remote); if the cap is hit, record partial count and extrapolated throughput and mark the baseline as partial.
- Record time-to-searchable and rebuild behaviour (R3).
- Commit a curated baseline report (`benchmarks/results/.../report.md`) on the benchmark branch.

## Acceptance
- JSON results for all of the above; curated baseline report committed.
- This is the "before" referenced by every S1–S9 before/after.

## Attempt 1 (2026-09-30, aborted) — `run --profile baseline`, client heap 14 GB, server heap 10 GB
- Smoke embedded, smoke remote and standard embedded completed (standard embedded: load 1M in 595.7 s = **1,679 docs/s**, flat vs 100k — no stall from 26.7.2's rebuilds; graph build **728 s**; hybrid ≈ 0.8–0.9 s/query at 1M, estimated from the timeline).
- **Standard remote failed — finding:** after loading 1M vectors (≈ 540 docs/s), the ArcadeDB 26.7.2 server ran out of its **10 GB heap** in `LSMVectorIndex.rebuildGraphBeforeSearch` (21:41–21:43 UTC), then failed to persist the graph (`ConcurrentModificationException … Failed to persist graph … nodes=1000000`). The readiness probe then hung on a query that never returned. Embedded mode built the same graph inside a 14 GB client heap. Logs: `/sysnet/rag-bench/b10-attempt1.log`, `/sysnet/rag-bench/b10-attempt1-server.log`.
- **Harness bugs, fixed the same day:** results were only written at the very end and one failed load aborted the run, so all measurements were lost; a probe attempt could block for 6 h. Now: `result.json` / `report.md` are rewritten after every load (`status: running`), a failed load is recorded (`error`) and the run continues (`status: incomplete`, exit code 1), probe attempts time out after ≤ 10 min within the readiness deadline, and a server `OutOfMemoryError` in the server log fails the load immediately.
- **Rerun plan:** two invocations so each side gets the memory it needs on the 46 GB machine — `--mode embedded --label embedded` (client heap 14 GB) and `--mode remote --label remote` (client heap 4 GB, server heap 24 GB); the curated baseline report combines both result directories.

## Attempt 2 (2026-09-30/10-01) — two invocations
- **Embedded: complete** (`benchmarks/results/2026-09-30-arcadedb-26.7.2-baseline-embedded/`, 20:12–01:12).
- **Remote: incomplete** (`…/2026-10-01-arcadedb-26.7.2-baseline-remote/`): smoke complete; **standard failed again with a 24 GB server heap** (`OutOfMemoryError` on query threads, `PostQueryHandler`, ~37 min after loading started). The new fail-fast worked (no hang, results written).
- **Cause (most likely the harness's own probing) — finding about 26.7.2:** at 1M the post-load graph build outlasts the 30 s HTTP/2 cut-off, so each probe lost its connection and the harness sent another every ~30 s (15 attempts). An abandoned query keeps running on the server, and each query that finds the graph stale runs its own full rebuild (`rebuildGraphBeforeSearch`), so ~15 concurrent 1M-vector builds exhausted the heap (attempt 1: 4 probes, 10 GB). At 100k the build finishes within ~60 s and at most two probes overlap. An application that queries right after a bulk load would hit the same; candidate for an ArcadeDB report, check 26.9.1 (#6655: rebuild no longer blocks the first query).
- **Fix:** after a failed probe the harness waits until the server's CPU is idle (the build continues server-side) before probing again, so probes never overlap. `--tier` override added to rerun only the failed load.
- **Attempt 3:** `--mode remote --tier standard --label remote-standard`, server heap 24 GB.

## Attempt 3 and outcome (2026-10-01) — done
- `--mode remote --tier standard --label remote-standard` (server heap 24 GB): load 1M in 1,805 s (554 docs/s); first probe dropped at 30 s, harness waited for the server to go idle, next probe answered — searchable 907 s after loading, **no OOM**. This confirms the overlapping probes caused attempt 2's OOM. `dense` aborted (S3); `hybrid-asis` nDCG@10 0.3222, p50 777 ms.
- **Curated baseline report:** [`benchmarks/results/2026-10-01-arcadedb-26.7.2-baseline/report.md`](../../benchmarks/results/2026-10-01-arcadedb-26.7.2-baseline/report.md), combining the embedded run, the remote run (smoke) and the remote-standard rerun. Load time cap (60 min) not reached anywhere, so the baseline is not partial.
- Headline: dense nDCG@10 0.853 (smoke) / 0.623 (standard), hybrid-asis 0.453 / 0.321 with p50 1.0 s at 1M embedded; remote dense unusable; remote ingestion ~550 vs ~1,700 docs/s embedded. Noise bands (p50 across 3 repetitions) < 0.5 %.
- Harness: the git dirty check now ignores `benchmarks/results/` (earlier runs' untracked `report.md` files had marked the remote runs dirty).
- R3 (rebuild behaviour): embedded load rate flat 100k → 1M (no stall); post-load graph build 786 s embedded / ~907 s remote at 1M; remote concurrent-rebuild OOM documented above.
