# B09 — Standard-tier embedding cache + ground truth (run)

| | |
|---|---|
| Phase | 2 — Scale-up |
| Branch | `arcadedb-rag-benchmark` (no code expected; ops) |
| Depends on | B00, B03, B04 |
| PRD | F7, F9, F10, F11, R5 |

## Scope
- Embed the 1,000,000 `standard` passages + 3,452 queries on the reference machine (checkpointed; ~1–3 h at the handoff's estimated throughput).
- Compute ground truth for `smoke` and `standard`; filtered ground truth for `standard` at 1/10/50%.
- Record wall time and throughput (used to size B22); archive the cache with checksums (backup copy outside the data dir).

## Acceptance
- Manifests present and consistent; embedding checksum recorded in this issue.
- Ground-truth caches for smoke and standard load without recomputation.

## Notes
Start as soon as B03 works — it does not depend on the rest of the smoke path. Full-tier embedding and ground truth are deferred to B22.

## Outcome (2026-09-30) — done
Run on the reference machine (i7-9700K, 8 cores, 46 GB, RX 7800 XT), PRD A4 GPU backend for passages (B09a):
- **Passages:** rows 100,000–999,999 embedded through the GPU server (`embed --tier standard --backend server`, TunableOp on) in **74 min**, cumulative 202 passages/s (first windows ~130/s while TunableOp tuned new shapes, ~200–250/s later; tuning file grew to ~3,200 results). Start-up check min cosine 0.9999999999993. 1,559 over-long passages (0.17%) embedded in-process. Rows 0–99,999 are the in-process smoke cache from 2026-09-29, unchanged.
- **Checksums:** passages (1,000,000 rows) vectors `364af239a30c7f5144468c5890f51283e219e0f46c05178e8bcbdb4ef15e8650`; queries (3,452, in-process, p50 5.44 ms) `907785dd65f1d729baa6274d1d4f16b7d3fea284b8cf9ffcb14f1e017d497ca4`. Manifest `segments`: `[100000, 1000000) http` (implicit in-process before).
- **Verify** (server stopped, `--verify 50`): 45 server rows + 5 in-process rows, 0 mismatches, max |diff| 3.1e-7, min cosine 1.0000000.
- **Ground truth** smoke + standard, plain and bucket < 1/10/50, one pass of 3,452 × 1,000,000 on 8 threads: **115.7 s**. Smoke files byte-identical to 2026-09-29 (`rowsSha256` `cfdcdb4d…`). Standard `rowsSha256`: plain `a9042663…`, lt-1 `e7fda00f…`, lt-10 `ef45c6de…`, lt-50 `a790c1bf…`. A second `ground-truth` run reports "up to date" (acceptance).
- **Sizing for B22 (full tier, +1.68M passages):** ~2.3 h on the GPU at ~200/s (less once tuning is saturated), vs ~4.9 h in-process on the CPU; ground truth for 2.68M ≈ 5–6 min.
- Disk: embeddings 1.5 GB, ground truth 22 MB.

- [x] Backup copy outside the data dir (PRD N1: copy, don't regenerate): `/media/odysseas/Backups/rag-bench/2026-09-30-nq-seed42-standard/` on a separate disk (`/dev/sda6`) — `embeddings/`, `groundtruth/`, `datasets/nq/{manifest.json,tiers/}`, 1.6 GB, 36 files verified against `SHA256SUMS`. Raw NQ files are not copied (re-downloaded and checksum-verified by `prepare`). To restore: copy into the data dir, then `sha256sum -c SHA256SUMS` there.
