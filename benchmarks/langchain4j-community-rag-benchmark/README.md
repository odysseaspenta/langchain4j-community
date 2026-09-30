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

### Remote mode (Docker)

`--mode embedded,remote` (or `--mode remote`) overrides the profile's deployment modes; `smoke` is embedded-only by default. Remote mode starts `arcadedata/arcadedb:<version>` with the `docker` CLI, once per (tier, load), and removes the container afterwards:

```shell
RAG_BENCH_CLIENT_CPUS=0-3 RAG_BENCH_HEAP=8g benchmarks/langchain4j-community-rag-benchmark/rag-bench \
  run --profile smoke --mode embedded,remote --server-cpus 4-7 --server-heap 8g
```

- **CPU pinning (PRD F17):** the container gets `--cpuset-cpus` from `--server-cpus`; by default the CPUs the client is not pinned to, or the upper half when the client is unpinned. `RAG_BENCH_CLIENT_CPUS` makes the launcher pin the client JVM with `taskset`. Overlap is warned about and recorded.
- **Server JVM:** `--server-heap` (default 4g, `-Xms = -Xmx`), ZGC (the image default, made explicit), GC log in the server directory. Client heap: `RAG_BENCH_HEAP`.
- **Server directory:** `<dataDir>/databases/arcadedb-<version>/<tier>-remote/` holds `databases/`, `log/` (server log, `gc.log`) and the logging configuration (per-search INFO off, F18). Deleted after the run unless `--keep-databases`. The container runs as uid 1000; the host user should be uid 1000 or able to delete such files.
- **Time to searchable:** from the end of loading until (1) a probe `vector.neighbors` query succeeds — on 26.7.2 the first query blocks until the pending graph build is done — and (2) the server is idle: ≥ 20 s after the last insert (past 26.7.2's 15 s inactivity rebuild) and 3 consecutive `docker stats` samples below 10% of a core. The server exposes no build state, and `REBUILD INDEX` does not build the graph on 26.7.2, so the harness does not use it. Details per load under `readiness` in `result.json`; the probe is harness SQL, not via the store API.
- **Query timeout:** once searchable, `ALTER DATABASE arcadedb.command.timeout` (`--query-timeout-seconds`, default 25, at most 29) so a runaway query is killed on the server instead of stealing CPU from the next ones. The cap exists because the ArcadeDB 26.7.2 Java client always uses HTTP/2 and the server closes an HTTP/2 connection 30 s into a request: any remote call over 30 s fails with `EOFException` regardless of timeouts.
- **Stored text:** line breaks become spaces, because the unmodified store's remote `addAll` cannot insert them (store bug, noted as S11). Embeddings are unchanged; full-text tokenisation is the same.

### Aborted scenarios

A pass stops after `--failure-streak-limit` (default 10) consecutive failed or empty queries, or after `--pass-time-cap-minutes`. Remaining queries are skipped, count as empty rankings in the accuracy metrics, and the scenario is marked `aborted` in `result.json` and the report. This keeps remote `dense` on the unmodified store (every query runs into the timeout, S3) from running for days.

Results land in `benchmarks/results/<yyyy-MM-dd>-arcadedb-<version>-<profile>/` (`result.json`, `report.md`, `gc.log`). The git commit and dirty flags in `result.json` are taken when the run starts. Embedded ArcadeDB logs to `<dataDir>/logs/` (the benchmark writes its own `java.util.logging` configuration unless `-Djava.util.logging.config.file` is given), never into the working directory. Load stats report both the live heap after a full GC and the peak used heap including uncollected garbage. Runs on anything but the reference machine should keep the default `--machine-class dev`; their reports say they are not comparable.

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
| `embed [--tier t \| --rows n] [--threads n] [--verify n] [--backend in-process\|server]` | Embeds passages (priority order, resumable) and test queries into the embedding cache; `--backend server` uses the GPU embedding server in [`gpu-embedder/`](gpu-embedder/README.md) for passages (PRD A4) |
| `ground-truth [--tier smoke,standard,...] [--k 100]` | Exact top-k per query and tier, unfiltered and `bucket < 1/10/50`, in one pass |
| `run --profile smoke [--mode embedded,remote] [--load-time-cap-minutes n] [--keep-databases]` | Full path: prepare → embed → ground truth → load → scenarios → `result.json` + `report.md` (canonical/extended: B16/B20) |
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
