# B01 — Benchmark module skeleton and build wiring

| | |
|---|---|
| Phase | 1 — Smoke path |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | — |
| PRD | D9, D13, D14, F13, F21, N5, §8.1, acceptance 8 |

## Scope
- New module `benchmarks/langchain4j-community-rag-benchmark`, added to the reactor only by a `benchmarks` profile (`-Pbenchmarks`) that also requires JDK 21 (the ArcadeDB module is only in the `jdk21-modules` profile, root `pom.xml:342-349`).
- Not deployed / released: skip deploy, javadoc, sources, BOM; no new dependencies leak into published modules.
- Dependencies: `langchain4j-community-arcadedb` (project version), `langchain4j-embeddings-bge-small-en-v15`, Testcontainers (for B08), JSON library, CLI parser (or hand-rolled).
- `arcadedb.version` property: one value drives the ArcadeDB jars (embedded) and the Docker tag (remote, B08). Document how to build the store module against a non-default version (`./mvnw -Pbenchmarks -Darcadedb.version=X ...` must recompile the store, so compile failures surface — F13).
- Package layout reflecting §8.1 boundaries: `dataset`, `embedding`, `groundtruth`, `target` (interface + `arcadedb` impl), `runner`, `metrics`, `report`.
- CLI entry point skeleton with subcommands (names are an implementation choice), e.g. `prepare`, `embed`, `ground-truth`, `run --profile`, `compare`.
- Config: data directory (outside the repo), seeds, profile; overridable by CLI / system property.
- `.gitignore`: `benchmarks/results/**` (except curated reports), any local data/cache paths.

## Acceptance
- `./mvnw verify` (no profile) does not build the module; `./mvnw -pl embedding-stores/langchain4j-community-arcadedb verify` unaffected (acceptance 8).
- `./mvnw -Pbenchmarks -pl benchmarks/langchain4j-community-rag-benchmark -am package` builds and the CLI prints help.
- `./mvnw -Pbenchmarks -Darcadedb.version=26.9.1 ...` compiles (or fails visibly) the store against that version.
