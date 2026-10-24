# Commit hash map (history rewrite, 2026-10-02)

On 2026-10-02 the commit messages of `arcadedb-rag-benchmark` (from `7de83f54` onwards) were rewritten to remove AI attribution trailers (`Co-Authored-By: Claude …`, `Claude-Session: …`); file contents are byte-identical (every commit's tree is unchanged). Every commit hash changed. Documents in this repository were updated to the new hashes; this table maps the old hashes, which are still recorded in local `result.json` files (`git.commit`) and logs, to the new ones.

| Old | New | Commit |
|---|---|---|
| `3840e0dd` | `6dc23f01` | docs: add PRD and handoff for ArcadeDB RAG benchmark (fork-only) |
| `374ee214` | `fc37c6ca` | docs: break ArcadeDB RAG benchmark PRD into issues (fork-only) |
| `5810800c` | `85289a7f` | docs: amend ArcadeDB benchmark plans for lower-spec machines and full-tier deferral |
| `fec0f763` | `6edbdadc` | feat(benchmark): add RAG benchmark module skeleton (B01) |
| `a1964f05` | `56729f3a` | feat(benchmark): BEIR dataset loader, tier sampler and bucket metadata (B02) |
| `7eedfb57` | `265c651f` | feat(benchmark): resumable embedding cache for passages and queries (B03) |
| `80c0cf3f` | `abf54c7c` | feat(benchmark): exact brute-force ground truth, plain and filtered (B04) |
| `10c4ecb7` | `c0e81c56` | feat(benchmark): metrics library (B05) |
| `a718ca47` | `e15c5d4d` | feat(benchmark): store-neutral BenchmarkTarget and embedded ArcadeDB target (B06) |
| `30daf7a6` | `33cb2a40` | feat(benchmark): runner, smoke profile, JSON results and Markdown summary (B07) |
| `6e9f3a20` | `8ffe00ae` | feat(benchmark): remote Docker ArcadeDB target (B08) |
| `ab31a411` | `1fc0b046` | docs(benchmark): GPU embedding task (B09a) and PRD amendment A4 |
| `cc4f55b1` | `071bae91` | docs(benchmark): first smoke run (M1) and GPU embedding gate results |
| `f950540c` | `c54660be` | fix(benchmark): remote readiness, run metadata and ArcadeDB logging; GPU TunableOp results |
| `d79c463b` | `d12823e1` | feat(benchmark): GPU embedding backend for passages (B09a) |
| `d139a3c6` | `1c0353bb` | docs(benchmark): standard-tier embeddings and ground truth (B09) |
| `e33ee241` | `5f2e6426` | feat(benchmark): baseline profile for B10 (smoke + standard, embedded + remote, 3 repetitions) |
| `6f053a75` | `c8e2abaa` | fix(benchmark): checkpoint results per load, isolate failed loads, bound remote probes |
| `42d716e5` | `2731bd8c` | fix(benchmark): never overlap remote readiness probes; --tier override |
| `8ed8251d` | `4ea752e3` | docs(benchmark): baseline on the unmodified store (B10) |
| `4a69814d` | `2d3aca60` | feat(benchmark): RAG_BENCH_JAVA_OPTS for extra JVM flags (e.g. --add-modules jdk.incubator.vector) |
| `c486fcd6` | `b3726c13` | docs(benchmark): smoke tier on ArcadeDB 26.9.1 and with the Java Vector API |
| `0d97bc77` | `f141fe42` | docs(benchmark): target ArcadeDB 26.9.1 for the store fixes; S10 outcome |

The S10 fix branch `fix/arcadedb-version-compat` was amended the same way before it was ever pushed: `09d7bea3` → `269dc68c`.

## Formatting rebuild (2026-10-24)

Every fix-stack commit was rebuilt with its tree formatted by spotless (palantir-java-format): formatting only — whitespace, line breaks, import order, one `//` comment re-wrapped; verified token-identical per file. Messages, authors and author dates are unchanged. Plans and reports written before this date cite the old hashes. The originals are kept as `refs/backup/pre-format/<branch>` (local only).

| Branch | Old | New | Commit |
|---|---|---|---|
| fix/arcadedb-version-compat | `269dc68c` | `05fa102a` | fix(arcadedb): keep an existing vector index when re-opening a remote store on ArcadeDB 26.8.1+ |
| fix/arcadedb-remote-top-k | `48f97664` | `6435c1a7` | fix(arcadedb): return the nearest neighbours in remote dense search |
| fix/arcadedb-remote-parameters | `eca574f6` | `4b620e99` | fix(arcadedb): bind values as parameters instead of inlining them into SQL |
| fix/arcadedb-batched-remote-ingestion | `196917df` | `9695edd6` | feat(arcadedb): batch remote addAll into one request and transaction per batch |
| fix/arcadedb-fulltext-query-sanitising | `c4b49d25` | `4cf3b060` | fix(arcadedb): escape Lucene syntax in hybrid queries and fall back to dense search |
| fix/arcadedb-batched-embedded-ingestion | `7d70c9f0` | `296aed1a` | feat(arcadedb): insert embeddings in one transaction per batch in embedded mode |
| chore/arcadedb-26.10.2 | `dc35cc79` | `21dc0e53` | chore(arcadedb): update ArcadeDB to 26.10.2 |
| fix/arcadedb-embedded-id-reuse | `f9e93e07` | `3bcdbea2` | fix(arcadedb): make addAll an upsert by id in both modes |
| feat/arcadedb-hybrid-options | `35f2debc` | `96cf533a` | feat(arcadedb): bounded full-text source, fusion options and full-text index settings for hybrid search |
| fix/arcadedb-efsearch | `e4df5e74` | `059b6cde` | feat(arcadedb): efSearch option; leaner remote dense and hybrid queries |
| feat/arcadedb-index-options | `0676b5b7` | `e095bcc9` | feat(arcadedb): similarity function and quantization options for the vector index |
| (inside S9 branch) | `b4122c8c` | `6467419b` | feat(arcadedb): server-side metadata filtering with an adaptive strategy |
| feat/arcadedb-server-side-filter | `4d8aca81` | `280e4557` | perf(arcadedb): cheaper allow-lists for filtered search |
| fix/arcadedb-scan-fallback | `17e89ca8` | `8f10de33` | fix(arcadedb): drop the full-scan supplement for unreachable vectors |
