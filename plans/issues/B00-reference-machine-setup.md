# B00 — Reference machine setup

| | |
|---|---|
| Phase | 0 — Setup |
| Branch | none (ops) |
| Depends on | — |
| PRD | N2, N3; handoff §4 |

## Scope
Prepare the machine that produces all `standard` / `canonical` numbers (and later `full`, B22).

- [ ] Linux, ≥16 physical cores, ≥64 GB RAM, local NVMe, ≥200 GB free.
- [ ] JDK 21 + `./mvnw`; confirm `jdk21-modules` profile activates (ArcadeDB module builds).
- [ ] Docker; pre-pull `arcadedata/arcadedb:26.7.2` and `arcadedata/arcadedb:26.9.1` (plus 26.8.1 if an intermediate point is wanted).
- [ ] Outbound HTTPS to `huggingface.co` and Maven Central.
- [ ] Data directory outside the repo (e.g. `/data/rag-bench`) with room for: raw dataset (~1.5 GB), embedding cache (~1.5 GB for standard, ~4.1 GB once B22 extends it to full), ground truth, ArcadeDB databases (several GB per tier × version × config).
- [ ] `git remote add upstream https://github.com/langchain4j/langchain4j-community.git`; fetch.
- [ ] Record CPU model, physical cores, RAM, disk model (the harness also captures these — B07).
- [ ] Optional: ArcadeDB source checkout at tags `26.7.2`, `26.8.1`, `26.9.1`.

## Acceptance
- `./mvnw -pl embedding-stores/langchain4j-community-arcadedb verify` passes on the machine.
- Both Docker images pulled; data dir exists and is writable.

## Notes
Phase 1 (B01–B07) does not need this machine; it can be developed on the current 8-core / 46 GB box.
