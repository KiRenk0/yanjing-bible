"""Build the offline BGE index from the bundled, unmodified CUV verse texts.

Use the same int8 ONNX model, CLS pooling and normalization as the Android client.
No queries or verse texts are sent to a service. Ordinary Android builds use the
committed assets and do not need this script or Python.
"""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import time
import argparse
import re
import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".tools/semantic"
OUT = ROOT / "app/src/main/assets/semantic"
REVISION = "75c43b069aac4d136ba6bc1122f995fedcfd2781"
PREFIX = "为这个句子生成表示以用于检索相关文章："
MAX_LENGTH = 256
DIMENSION = 512

def sha(path):
    return hashlib.file_digest(path.open("rb"), "sha256").hexdigest()

def main():
    global OUT
    parser = argparse.ArgumentParser()
    parser.add_argument('--corpus', choices=['bible','quran','analects'], default='bible')
    parser.add_argument('--batch-size', type=int, default=1)
    parser.add_argument('--output', type=Path, default=OUT)
    args = parser.parse_args()
    assert args.batch_size > 0
    if ort.__version__ != "1.25.1":
        raise RuntimeError("Install scripts/semantic-requirements.txt: index generation must use ONNX Runtime 1.25.1, matching Android")
    corpus_kind = args.corpus
    OUT = args.output
    OUT.mkdir(parents=True, exist_ok=True)
    version = {'bible': 'cuvs', 'quran': 'quran_zh', 'analects': 'analects'}[corpus_kind]
    corpus = ROOT / f"app/src/main/assets/bibles/{version}.tsv"
    prefix = f"{corpus_kind}-ort{ort.__version__}-batch{args.batch_size}-"
    rows = [line.rstrip("\n").split("\t", 5) for line in corpus.open(encoding="utf-8") if line.strip() and not line.startswith('#')]
    source_verses = len(rows)
    if corpus_kind in ('quran', 'analects'):
        passages = []
        for row in rows:
            texts = [row[3]] + ([row[5]] if corpus_kind == 'analects' else [])
            candidates = []
            for text in texts:
                candidates += [text] if len(text) <= 220 else [text[i:i+200] for i in range(0,len(text),160)]
                candidates += [part.strip() for part in re.split('[。；！？]',text) if len(part.strip()) >= 8]
            for part in dict.fromkeys(candidates):
                passages.append(row[:3] + [part] + row[4:])
        rows = passages
    texts = [r[3] for r in rows]
    tokenizer = Tokenizer.from_file(str(CACHE / "tokenizer.json"))
    tokenizer.enable_truncation(max_length=MAX_LENGTH)
    tokenizer.enable_padding(pad_id=0, pad_token="[PAD]")
    options = ort.SessionOptions()
    # Match the Android encoder: one sequence per call, two CPU threads. Dynamic
    # activation quantization can change embeddings when unrelated rows share a batch.
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(CACHE / "model.onnx"), sess_options=options, providers=["CPUExecutionProvider"])
    cache_key = sha(corpus) + sha(CACHE / "model.onnx") + f'{corpus_kind}-passages-v1-ort{ort.__version__}-batch{args.batch_size}-threads2'
    cache_file = CACHE / (prefix + "embedding-cache-key.txt")
    shape = (len(rows), DIMENSION)
    reusable = cache_file.exists() and cache_file.read_text() == cache_key
    vectors = np.memmap(CACHE / (prefix + "embeddings.f32"), dtype=np.float32, mode="r+" if reusable else "w+", shape=shape)
    done = np.memmap(CACHE / (prefix + "embeddings.done"), dtype=np.uint8, mode="r+" if reusable else "w+", shape=(len(rows),))
    if not reusable:
        done[:] = 0
        cache_file.write_text(cache_key)
    order = sorted((i for i in range(len(rows)) if not done[i]), key=lambda i: len(texts[i]))
    started = time.monotonic()
    for start in range(0, len(order), args.batch_size):
        batch_indices = order[start:start + args.batch_size]
        encoded = tokenizer.encode_batch([texts[i] for i in batch_indices])
        inputs = {
            "input_ids": np.array([e.ids for e in encoded], dtype=np.int64),
            "attention_mask": np.array([e.attention_mask for e in encoded], dtype=np.int64),
            "token_type_ids": np.array([e.type_ids for e in encoded], dtype=np.int64),
        }
        embedding = session.run(["last_hidden_state"], inputs)[0][:, 0, :]
        embedding /= np.maximum(np.linalg.norm(embedding, axis=1, keepdims=True), 1e-12)
        vectors[batch_indices] = embedding
        done[batch_indices] = 1
        if start % 512 == 0:
            vectors.flush(); done.flush()
            print(f"Embedded {int(done.sum())}/{len(rows)} verses ({time.monotonic() - started:.1f}s)", flush=True)
    vectors.flush(); done.flush()
    index_name = "verses.i8" if corpus_kind == 'bible' else f"{corpus_kind}.i8"
    with (OUT / index_name).open("wb") as output:
        output.write(b"YJSEM001")
        output.write(struct.pack("<III", len(rows), DIMENSION, MAX_LENGTH))
        for row, embedding in zip(rows, vectors):
            quantized = np.rint(embedding / max(float(np.abs(embedding).max()), 1e-12) * 127).clip(-127, 127).astype(np.int8)
            inverse_norm = 1.0 / float(np.linalg.norm(quantized.astype(np.float32)))
            key = int(row[0]) * 1_000_000 + int(row[1]) * 1000 + int(row[2])
            output.write(struct.pack("<If", key, inverse_norm))
            output.write(quantized.tobytes())
    shutil.copyfile(CACHE / "model.onnx", OUT / "bge-small-zh-int8.onnx")
    shutil.copyfile(CACHE / "vocab.txt", OUT / "vocab.txt")
    if OUT.resolve() == (ROOT / "app/src/main/assets/semantic").resolve():
        for file in ["bge-LICENSE.txt", "onnxruntime-LICENSE.txt", "onnxruntime-ThirdPartyNotices.txt"]:
            shutil.copyfile(ROOT / "third_party" / file, ROOT / "app/src/main/assets" / file)
    manifest = {
        "schema": 1, "model": "BAAI/bge-small-zh-v1.5", "onnxRepository": "Xenova/bge-small-zh-v1.5",
        "revision": REVISION, "license": "MIT", "pooling": "CLS", "normalization": "L2",
        "dimensions": DIMENSION, "maxTokens": MAX_LENGTH, "queryPrefix": PREFIX,
        "embeddingBatchSize": args.batch_size, "intraOpThreads": 2,
        "onnxRuntime": ort.__version__,
        "sourceVersion": version, "verses": source_verses, "passages": len(rows), "corpusSha256": sha(corpus),
        "files": {p.name: {"bytes": p.stat().st_size, "sha256": sha(p)} for p in sorted(OUT.iterdir()) if p.name in (index_name, "bge-small-zh-int8.onnx", "vocab.txt")},
    }
    manifest_name = "manifest.json" if corpus_kind == 'bible' else f"{corpus_kind}-manifest.json"
    (OUT / manifest_name).write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    tokenizer.no_padding()
    samples = ["为这个句子生成表示以用于检索相关文章：我忙得喘不过气，想歇一会儿", "我想要喝水", "ＡＢＣ  Café\t你好，世界！", "[CLS]中文[SEP]", "help me 123🙂𠮷"]
    fixtures = [{"text": t, "ids": tokenizer.encode(t).ids} for t in samples]
    if OUT.resolve() == (ROOT / "app/src/main/assets/semantic").resolve():
        (ROOT / "app/src/test/resources").mkdir(parents=True, exist_ok=True)
        (ROOT / "app/src/test/resources/tokenizer-fixtures.json").write_text(json.dumps(fixtures, ensure_ascii=False), encoding="utf-8")
    print(f"Index ready: {len(rows)} verses, {(OUT / index_name).stat().st_size} bytes", flush=True)

if __name__ == "__main__":
    main()
