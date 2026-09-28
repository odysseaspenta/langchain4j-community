# B17 — Version comparison run + report

| | |
|---|---|
| Phase | 6 — Versions |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B15, B16, S10 (merged into benchmark branch) |
| PRD | Goal 1, 4; acceptance 5, 6; §4 maintainer use case |

## Scope
- `canonical` on 26.7.2 and on the latest release, both modes, same machine, same caches.
- `compare` → curated Markdown + SVG report committed on the benchmark branch; call out version-specific behaviour (adaptive efSearch fix #6494, BM25 IDF across buckets #5267, graph build/rebuild changes, logging).

## Acceptance
- Acceptance 6 met; acceptance 5 met for the standard-only canonical (full-tier re-check in B22); report committed.
