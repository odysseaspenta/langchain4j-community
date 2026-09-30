#!/usr/bin/env bash
# Starts the GPU embedding server (PRD A4, issue B09a) on an AMD GPU with ROCm, listening on 127.0.0.1:8080.
#   RAG_BENCH_DATA_DIR  holds embed-server/ (Hugging Face cache, TunableOp results); default ~/.cache/langchain4j-rag-benchmark
#   EMBED_SERVER_PORT   host port (default 8080)
# Waits until /health answers (up to 10 min: first start downloads packages and the model), then exits.
# Stop it with: docker stop bge-embed  (stop it during measured runs: the idle server spins one CPU core).
# Then: rag-bench embed --tier standard --backend server
set -euo pipefail
name=bge-embed
port="${EMBED_SERVER_PORT:-8080}"
if [[ "$(docker inspect --format '{{.State.Running}}' "$name" 2>/dev/null || true)" == "true" ]]; then
  echo "$name is already running"; exit 0
fi
# A stopped container may come from an older configuration: replace it rather than restart it.
docker rm "$name" >/dev/null 2>&1 || true
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
state="${RAG_BENCH_DATA_DIR:-$HOME/.cache/langchain4j-rag-benchmark}/embed-server"
mkdir -p "$state/hf-cache"

# Image and model revision that passed the compliance gate; change only together with a new gate run.
image="rocm/pytorch@sha256:a3867e22ca44c458b1feda92026c6dc289e7e447093cf55914d1fd56ef6d7372"
revision="5c38ec7c405ec4b44b94cc5a9bb96e735b38267a"

docker run -d --name "$name" \
  --device /dev/kfd --device /dev/dri \
  --group-add "$(getent group render | cut -d: -f3)" \
  --group-add "$(getent group video | cut -d: -f3)" \
  --security-opt seccomp=unconfined --ipc=host \
  -p "127.0.0.1:$port:8080" \
  -v "$here":/app:ro -w /app \
  -v "$state":/state \
  -v "$state/hf-cache":/root/.cache/huggingface \
  -e MODEL_REVISION="$revision" \
  -e PYTHONDONTWRITEBYTECODE=1 \
  -e PYTORCH_TUNABLEOP_ENABLED=1 \
  -e PYTORCH_TUNABLEOP_FILENAME=/state/tunableop_results.csv \
  "$image" \
  bash -c "pip install -r requirements.txt && uvicorn server:app --host 0.0.0.0 --port 8080" >/dev/null

for _ in $(seq 1 300); do
  if curl -sf "http://127.0.0.1:$port/health"; then echo; exit 0; fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$name")" != "true" ]]; then
    docker logs --tail 30 "$name" >&2; echo "$name exited" >&2; exit 1
  fi
  sleep 2
done
echo "$name not ready after 10 min; see: docker logs $name" >&2
exit 1
