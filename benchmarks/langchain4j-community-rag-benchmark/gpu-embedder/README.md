# GPU embedding server (AMD ROCm)

Optional helper for bulk **passage** embedding (PRD amendment A4, issue [B09a](../../../plans/issues/B09a-gpu-embedding-backend.md)). It serves `BAAI/bge-small-en-v1.5` at revision `5c38ec7c…`, whose `onnx/model.onnx` and `tokenizer.json` are byte-identical to the files LangChain4j's in-process model uses, in fp32 with the model's own CLS pooling and normalisation. Queries are always embedded in-process by the benchmark.

```shell
RAG_BENCH_DATA_DIR=/data/rag-bench benchmarks/langchain4j-community-rag-benchmark/gpu-embedder/run_container.sh   # returns once /health answers
RAG_BENCH_DATA_DIR=/data/rag-bench benchmarks/langchain4j-community-rag-benchmark/rag-bench embed --tier standard --backend server
docker stop bge-embed             # before measured runs
```

- **Pinned:** image `rocm/pytorch@sha256:a3867e22…`, model revision, Python packages (`requirements.txt`). These passed the compliance gate; change them only together with a new gate run (B09a).
- **API:** `GET /health` (model, revision, device, versions); `POST /v1/embeddings` (OpenAI-compatible). Texts over 512 tokens are refused with HTTP 422 and their indexes, never truncated — LangChain4j splits and averages such texts, so the benchmark embeds them in-process (~0.17% of NQ passages).
- **Before writing,** `embed --backend server` checks the revision via `/health` and compares 64 passages (half random, half the longest) with the in-process model: cosine ≥ 0.999 or it stops. The cache manifest (`passages.json`, `segments`) records which rows came from the server, its `/health`, and how many were embedded in-process. `embed --verify n` then checks in-process rows for bit equality and server rows for cosine ≥ 0.999.
- **Throughput** on an RX 7800 XT: ~290 passages/s with TunableOp after one tuning pass (the first pass tunes and runs at ~75/s); ~185/s without. The benchmark sorts each window of 8,192 passages by length and sends one request of 128 at a time; the server also serialises GPU work, since concurrent fp32 batches ran the 16 GB card out of memory.
- **State** lives in `$RAG_BENCH_DATA_DIR/embed-server/` (Hugging Face cache, `tunableop_results0.csv`); keep it so restarts skip the tuning pass. The repository directory is mounted read-only. The script is idempotent: it does nothing if `bge-embed` runs, and replaces a stopped `bge-embed` container.
- **Stop the server during measured runs** (`docker stop bge-embed`): while idle it keeps one CPU core at 100% (a spinning thread, likely the ROCm runtime), which doubled in-process query-embedding latency in a test. Also stop it before embedding queries.
- **Other GPUs:** the same `server.py` runs on CUDA with a PyTorch CUDA image (drop the `/dev/kfd` / `/dev/dri` / group options, use `--gpus all`); run the compliance gate again before trusting it.
