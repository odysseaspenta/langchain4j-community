# RAG retrieval benchmark (fork-only)

Accuracy and speed benchmark for a basic RAG retrieval pipeline on the LangChain4j ArcadeDB embedding store.
Requirements: [`plans/arcadedb-rag-benchmark-prd.md`](../../plans/arcadedb-rag-benchmark-prd.md); work items: [`plans/issues/`](../../plans/issues/README.md).

This module lives only on the `arcadedb-rag-benchmark` branch of the fork. It is never released, deployed or proposed upstream.

## Build

Requires JDK 21. The module is only in the reactor with the `benchmarks` profile:

```shell
./mvnw -Pbenchmarks -pl benchmarks/langchain4j-community-rag-benchmark -am clean package
$JAVA_HOME/bin/java -jar benchmarks/langchain4j-community-rag-benchmark/target/langchain4j-community-rag-benchmark-*.jar --help
```

`package` produces the jar plus its runtime dependencies in `target/lib/`. Run it with a JDK 21 `java` (the one on `PATH` may be older).

### Choosing the ArcadeDB version

One property selects the ArcadeDB version for both modes (PRD F13):

```shell
./mvnw -Pbenchmarks -Darcadedb.version=26.9.1 -pl benchmarks/langchain4j-community-rag-benchmark -am clean package
```

`-am` rebuilds the store module against that version, so API incompatibilities show up as compile failures (record them as findings, S10). At runtime the benchmark reads the version from the ArcadeDB jar on the classpath and uses the Docker image `arcadedata/arcadedb:<same version>` for remote mode. Check with `rag-bench info`. Use `clean` when switching versions so `target/lib/` does not accumulate jars from several versions.

## Running measurements

Use the launcher, which applies the PRD's JVM isolation settings (pinned heap, G1, GC log copied into the results):

```shell
RAG_BENCH_HEAP=8g benchmarks/langchain4j-community-rag-benchmark/rag-bench --machine-class reference run --profile smoke
```

Results land in `benchmarks/results/<yyyy-MM-dd>-arcadedb-<version>-<profile>/` (`result.json`, `report.md`, `gc.log`). Runs on anything but the reference machine should keep the default `--machine-class dev`; their reports say they are not comparable.

## Configuration

| Setting | CLI | System property | Environment | Default |
|---|---|---|---|---|
| Data directory (datasets, caches, databases; must be outside the repo) | `--data-dir` | `rag.bench.dataDir` | `RAG_BENCH_DATA_DIR` | `~/.cache/langchain4j-rag-benchmark` |
| Results directory | `--results-dir` | `rag.bench.resultsDir` | `RAG_BENCH_RESULTS_DIR` | `<repo>/benchmarks/results` |
| Seed | `--seed` | `rag.bench.seed` | `RAG_BENCH_SEED` | `42` |
| Machine class (`reference` or `dev`) | `--machine-class` | `rag.bench.machineClass` | `RAG_BENCH_MACHINE_CLASS` | `dev` |

Precedence: CLI, then system property, then environment, then default. Options can go before or after the subcommand.

Raw results under `benchmarks/results/` are git-ignored; only `report.md` and `*.svg` files there can be committed (curated reports).

## Commands

| Command | Status |
|---|---|
| `info` | Prints resolved configuration and the ArcadeDB version under test |
| `prepare [--dataset nq]` | Downloads and verifies the dataset (resumable), builds tier id lists |
| `embed [--tier t \| --rows n] [--threads n] [--verify n]` | Embeds passages (priority order, resumable) and test queries into the embedding cache |
| `ground-truth [--tier smoke,standard,...] [--k 100]` | Exact top-k per query and tier, unfiltered and `bucket < 1/10/50`, in one pass |
| `run --profile smoke [--load-time-cap-minutes n] [--keep-databases]` | Full path: prepare → embed → ground truth → load → scenarios → `result.json` + `report.md` (canonical/extended: B16/B20) |
| `compare <result.json>...` | Pending — B15 |

Pending commands exit with code 3 and name their issue.

## Data directory layout

```
datasets/nq/raw/                    corpus.jsonl, queries.jsonl, qrels/test.tsv (mteb/nq, pinned revision + SHA-256)
datasets/nq/manifest.json           counts and file checksums (no paths/timestamps; its checksum identifies the data)
datasets/nq/tiers/seed-42/          priority.ids, smoke.ids, standard.ids, full.ids, manifest.json
embeddings/nq/seed-42/bge-small-en-v1.5/  passages.{f32,ids,json}, queries.{f32,ids,json} (little-endian float32 rows)
groundtruth/nq/seed-42/bge-small-en-v1.5/k100/  <tier>[-bucket-lt-<t>].{rows,scores,json} (cache rows, best first)
```

`priority.ids` is the embedding-cache order: relevant passages first, then the rest shuffled; each tier is a prefix of it, so `smoke ⊂ standard ⊂ full`. `<tier>.ids` lists the same members shuffled again: this is the order passages are loaded into a store. See `TierSampler` for the exact algorithm.

## Packages

Boundaries follow PRD §8.1: `dataset`, `embedding`, `groundtruth`, `metrics` and `report` know nothing about ArcadeDB; everything ArcadeDB-specific lives under `targets.arcadedb`.
