# Embedding server for bulk passage embedding on an AMD GPU (ROCm), PRD amendment A4 / issue B09a.
# Serves BAAI/bge-small-en-v1.5 at a pinned Hub revision whose onnx/model.onnx and tokenizer.json are the files
# LangChain4j's in-process model uses; fp32, CLS pooling + normalize (from the model's own sentence-transformers
# config). The benchmark checks /health and compares a sample against the in-process model before using it.
import os
import threading

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

MODEL = "BAAI/bge-small-en-v1.5"
REVISION = os.environ.get("MODEL_REVISION")  # pinned Hub commit
MAX_TOKENS = 512                              # including [CLS] and [SEP]

# ROCm PyTorch exposes the AMD GPU as "cuda".
model = SentenceTransformer(MODEL, revision=REVISION, device="cuda").float()  # fp32, no fp16
model.max_seq_length = MAX_TOKENS
tokenizer = model.tokenizer

# One encode at a time: two concurrent fp32 batches of 256 x 512 tokens ran a 16 GB GPU out of memory.
gpu = threading.Lock()

app = FastAPI()


class EmbeddingRequest(BaseModel):
    input: list[str] | str
    model: str | None = None


@app.get("/health")
def health():
    return {"model": MODEL, "revision": REVISION, "device": torch.cuda.get_device_name(0),
            "torch": torch.__version__, "hip": torch.version.hip,
            "tunableop": os.environ.get("PYTORCH_TUNABLEOP_ENABLED", "0")}


@app.post("/v1/embeddings")  # OpenAI-compatible
def embeddings(request: EmbeddingRequest):
    texts = [request.input] if isinstance(request.input, str) else request.input
    # Refuse rather than silently truncate: LangChain4j splits long texts and averages the parts instead, so the
    # benchmark embeds refused texts in-process.
    lengths = [len(ids) for ids in tokenizer(texts, add_special_tokens=True)["input_ids"]]
    too_long = [i for i, n in enumerate(lengths) if n > MAX_TOKENS]
    if too_long:
        raise HTTPException(422, {"too_long": too_long})
    with gpu, torch.inference_mode():
        vectors = model.encode(texts, batch_size=256, normalize_embeddings=True, convert_to_numpy=True)
    return {"object": "list", "model": MODEL,
            "data": [{"object": "embedding", "index": i, "embedding": v.tolist()}
                     for i, v in enumerate(vectors)]}
