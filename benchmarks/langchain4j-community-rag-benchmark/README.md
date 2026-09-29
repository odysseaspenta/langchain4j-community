# RAG retrieval benchmark (fork-only)

Accuracy and speed benchmark for a basic RAG retrieval pipeline on the LangChain4j ArcadeDB embedding store.
Requirements: [`plans/arcadedb-rag-benchmark-prd.md`](../../plans/arcadedb-rag-benchmark-prd.md); work items: [`plans/issues/`](../../plans/issues/README.md).

This module lives only on the `arcadedb-rag-benchmark` branch of the fork. It is never released, deployed or proposed upstream.

## Build

Requires JDK 21. The module is only in the reactor with the `benchmarks` profile:

```shell
./mvnw -Pbenchmarks -pl benchmarks/langchain4j-community-rag-benchmark -am clean package
java -jar benchmarks/langchain4j-community-rag-benchmark/target/langchain4j-community-rag-benchmark-*.jar --help
```

`package` produces the jar plus its runtime dependencies in `target/lib/`.

### Choosing the ArcadeDB version

One property selects the ArcadeDB version for both modes (PRD F13):

```shell
./mvnw -Pbenchmarks -Darcadedb.version=26.9.1 -pl benchmarks/langchain4j-community-rag-benchmark -am clean package
```

`-am` rebuilds the store module against that version, so API incompatibilities show up as compile failures (record them as findings, S10). At runtime the benchmark reads the version from the ArcadeDB jar on the classpath and uses the Docker image `arcadedata/arcadedb:<same version>` for remote mode. Check with `rag-bench info`. Use `clean` when switching versions so `target/lib/` does not accumulate jars from several versions.

## Configuration

| Setting | CLI | System property | Environment | Default |
|---|---|---|---|---|
| Data directory (datasets, caches, databases; must be outside the repo) | `--data-dir` | `rag.bench.dataDir` | `RAG_BENCH_DATA_DIR` | `~/.cache/langchain4j-rag-benchmark` |
| Results directory | `--results-dir` | `rag.bench.resultsDir` | `RAG_BENCH_RESULTS_DIR` | `<repo>/benchmarks/results` |
| Seed | `--seed` | `rag.bench.seed` | `RAG_BENCH_SEED` | `42` |

Precedence: CLI, then system property, then environment, then default. Options can go before or after the subcommand.

Raw results under `benchmarks/results/` are git-ignored; only `report.md` and `*.svg` files there can be committed (curated reports).

## Commands

| Command | Status |
|---|---|
| `info` | Prints resolved configuration and the ArcadeDB version under test |
| `prepare` | Pending — B02 |
| `embed` | Pending — B03 |
| `ground-truth` | Pending — B04 |
| `run --profile smoke\|canonical\|extended` | Pending — B07 |
| `compare <result.json>...` | Pending — B15 |

Pending commands exit with code 3 and name their issue.

## Packages

Boundaries follow PRD §8.1: `dataset`, `embedding`, `groundtruth`, `metrics` and `report` know nothing about ArcadeDB; everything ArcadeDB-specific lives under `targets.arcadedb`.
