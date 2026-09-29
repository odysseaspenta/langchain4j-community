# B09a — GPU embedding backend (AMD ROCm) for bulk passage embedding

| | |
|---|---|
| Phase | 2 — Scale-up |
| Branch | `arcadedb-rag-benchmark` |
| Depends on | B03 (embedding cache) |
| Blocks | B09 (standard tier), B22 (full tier) — optional speed-up, not a correctness dependency |
| PRD | D6, D14, F6–F9, N1, amendment **A4** (approved 2026-09-29) |

## Why
CPU embedding of `bge-small-en-v1.5` runs at ~90–95 passages/s on the 8-core i7-9700K (smoke tier: 100k in 18 min). That puts the `standard` tier (1M, B09) at ~3 h and the `full` tier (2.68M, B22) at ~8–9 h. The reference machine has an AMD Radeon RX 7800 XT (16 GB, gfx1101) that can embed the same model far faster.

## PRD amendment A4 (approved 2026-09-29, in PRD §5.1)
D6 / D14 say embedding is in-process ONNX and the toolchain is all-Java. A4: *bulk **passage** embedding may use an external GPU server running the same model weights and tokenizer, provided each vector matches the in-process ONNX model within the tolerance below and the cache manifest records the backend. Query embedding, and query-embedding latency (F8), stay in-process on the CPU.* The benchmark remains all-Java; the server is a pinned, containerised tool, like the ArcadeDB image.

## Server (already deployed, 2026-09-29)
- Image `rocm/pytorch:latest` = `rocm/pytorch@sha256:a3867e22ca44c458b1feda92026c6dc289e7e447093cf55914d1fd56ef6d7372` (torch `2.13.0+rocm7.14.0`, HIP `7.14.60850`); started with `--device /dev/kfd --device /dev/dri`, numeric `render`/`video` group ids, `127.0.0.1:8080`.
- `server.py`: sentence-transformers, `BAAI/bge-small-en-v1.5` at revision **`5c38ec7c405ec4b44b94cc5a9bb96e735b38267a`**, fp32, CLS pooling + normalize (from the model's own `modules.json` / `1_Pooling`), `max_seq_length` 512. OpenAI-compatible `POST /v1/embeddings`; `GET /health` reports model, revision, device, torch and HIP versions. Texts over 512 tokens are **refused** (HTTP 422 with their indexes), never truncated.
- Why that revision: its `onnx/model.onnx` is byte-identical to the ONNX file in `langchain4j-embeddings-bge-small-en-v15` (SHA-256 `828e1496…40cf35`, the cache's `modelSha256`), its `tokenizer.json` is the same git blob (`688882a7…`), and the PyTorch weights in the same commit are the ones that file was exported from.
- Currently lives outside the repo in `/sysnet/rag-bench/embed-server/` (`server.py`, `requirements.txt`, `run_container.sh`).

## Scope
- Move the server files into the module (e.g. `benchmarks/langchain4j-community-rag-benchmark/gpu-embedder/`) with a README; pin the image by digest and `requirements.txt` exactly (`pip freeze` from the running container) instead of `sentence-transformers==3.*` / `latest`.
- `embedding` package: an HTTP backend for **passages only** (`embed --backend http --endpoint http://127.0.0.1:8080`), batching requests (e.g. 256 texts) and writing into the existing append-only, checkpointed cache (F7, F9) so an interrupted run resumes and CPU- and GPU-written chunks can coexist.
- **Long texts:** LangChain4j's `OnnxBertBiEncoder` does not truncate: it splits texts over the limit (`partition`), embeds the parts and takes a token-weighted average (`weightedAverage`, then `normalize`). The server truncates-by-refusal instead, so passages it rejects are embedded in-process on the CPU in the same run. Count them and record the count.
- On start, call `/health` and fail unless the model, revision and the embedding cache's `modelSha256`-linked revision agree.
- Cache manifest: record per chunk (or per run) the backend (`onnx-cpu` / `http-gpu`), endpoint `/health` output, image digest, and CPU-fallback count. Checksums unchanged in meaning (N1: the cache is the artefact; copy it, don't regenerate).
- Queries: unchanged — in-process CPU, latency measured as today (F8).

## Compliance gate (must pass before the backend may write to a cache)
Run against the existing CPU-built **smoke** cache (100k):
1. Re-embed all smoke passages over HTTP (CPU fallback for over-long ones) into a scratch cache.
2. Per-vector cosine vs the CPU cache: **min ≥ 0.999** (expected ≈ 1 − 1e-6).
3. Recompute exact top-100 ground truth for the test queries from the GPU vectors and compare with the CPU ground truth: top-10 sets identical for ≥ 99.9% of queries, and nDCG@10 / Recall@100 of the exact neighbours unchanged to 4 decimals.
4. Report GPU throughput (passages/s) and the over-long fraction.
Record the numbers in this issue's *Outcome*.

## Evidence so far (light check, 2026-09-29)
- `/health`: `BAAI/bge-small-en-v1.5`, revision `5c38ec7c…`, `AMD Radeon RX 7800 XT`.
- 316 passages (296 random + the 20 longest among the first 30k corpus lines) vs the CPU cache: cosine 1.000000 for all, max `1 − cos` 4.1e-7; max per-component difference 3.3e-7 (64 vectors). Float32-rounding level.
- 4 of those 20 longest were refused as > 512 tokens (`doc15133` 600 words, `doc20158` 469, `doc21045` 459, `doc29894` 373) — the CPU-fallback path is needed.

## Acceptance
- Compliance gate passes on the smoke tier; numbers recorded.
- `embed --tier standard --backend http` builds the standard cache resumably; the manifest shows the backend, server details and CPU-fallback count.
- B09's ground truth computed from that cache; B09 records that the cache was GPU-built.

## Compliance gate result (2026-09-29, smoke tier, standalone script)
Re-embedded all 100,000 smoke passages through the server (length-sorted batches of 128, one request at a time) and compared with the CPU cache and the Java ground truth:

| Check | Result | Threshold |
|---|---|---|
| Passages refused as > 512 tokens (keep CPU vectors) | 168 (0.17%) | — |
| Per-vector cosine GPU vs CPU | min 0.9999214, p0.1 0.9999995, median 1.0000000 | min ≥ 0.999 ✅ |
| Max per-component difference | 2.6e-3 | — |
| Script sanity: numpy CPU top-10 vs Java ground truth | identical for 100% of queries | — |
| Exact top-10 set, GPU vs CPU | identical for **100%** of 3,452 queries; top-100 mean overlap 0.99999 | ≥ 99.9% ✅ |
| Exact top-10 order | identical for 99.97% (one near-tie swap) | — |
| Exact-neighbour nDCG@10 / Recall@100 | CPU 0.8584 / 0.9876, GPU 0.8584 / 0.9876 | unchanged to 4 dp ✅ |

**The accuracy gate passes.** GPU vectors are interchangeable with the CPU ones.

**Throughput is disappointing: 185 passages/s overall** (~950/s on the shortest passages, falling to ~200/s on the longest), about 2× the 8-core CPU (95/s). Observations:
- Two concurrent requests of 256 texts ran the 16 GB GPU out of memory (fp32 attention at 512 tokens); the server has no request serialisation, so the client must send one request at a time (or the server should hold a lock around `encode`).
- Random batches pad to the longest text and ran at ~75/s; sorting by length before batching gave the 185/s above.
- After the OOM, PyTorch's cache holds all 16 GB of VRAM; a server restart may help.
- Options to try before B09 (each re-checked against this gate): restart the server; `PYTORCH_TUNABLEOP_ENABLED=1` (GEMM tuning on ROCm); larger batches for short texts; running the CPU and GPU backends side by side (~280/s combined). fp16 would be much faster but departs from "full precision" (D6) — owner decision, and it must pass the gate.
- At 185/s the standard tier (1M) takes ~1.5 h on the GPU vs ~2.9 h on the CPU.
