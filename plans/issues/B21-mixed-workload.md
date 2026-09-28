# B21 — `mixed` read/write scenario (optional)

| | |
|---|---|
| Phase | 7 — Extended |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B16 |
| PRD | D10, §7.4 `mixed`, §12 |

## Scope
- Queries at a fixed QPS while inserting new vectors (standard tier); latency and recall over time, rebuild events.
- Define insert rate and QPS (PRD §12 open question) before implementing; insert vectors from the `full \ standard` set so ground truth can be extended. Only the inserted passages need embedding (append them to the cache, B03), so this does not pull the full-tier embedding forward.

## Acceptance
- Parameters documented; one run recorded on each compared version.
