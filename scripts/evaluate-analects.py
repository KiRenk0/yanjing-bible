"""Inspect real quantized-index retrieval for modern paraphrases (no expression lookup)."""
from pathlib import Path
import json
import re
import struct
import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"

def main():
    data = (ASSETS / "semantic/analects.i8").read_bytes()
    count, dimensions, _ = struct.unpack_from("<III", data, 8)
    records = np.frombuffer(data, dtype=np.dtype([("key","<u4"),("norm","<f4"),("vector","i1",(dimensions,))]), count=count, offset=20)
    vectors = records["vector"].astype(np.float32) * records["norm"][:,None]
    tokenizer = Tokenizer.from_file(str(ROOT / ".tools/semantic/tokenizer.json"))
    tokenizer.enable_truncation(max_length=256)
    options = ort.SessionOptions(); options.intra_op_num_threads = 2
    model = ort.InferenceSession(str(ASSETS / "semantic/bge-small-zh-int8.onnx"), sess_options=options, providers=["CPUExecutionProvider"])
    test = (ROOT / "app/src/test/java/org/yanjing/app/core/AnalectsSearchTest.kt").read_text(encoding="utf-8")
    queries = re.findall(r'"([^"]+)" to VerseKey\(68,(\d+),(\d+)\)', test)
    report = []
    for query, chapter, verse in queries:
        tokens = tokenizer.encode("为这个句子生成表示以用于检索相关文章：" + query)
        inputs = {"input_ids": np.array([tokens.ids], dtype=np.int64), "attention_mask": np.array([tokens.attention_mask], dtype=np.int64), "token_type_ids": np.array([tokens.type_ids], dtype=np.int64)}
        vector = model.run(None, inputs)[0][0,0]; vector /= np.linalg.norm(vector)
        scores = vectors @ vector
        best = {}
        for i, score in enumerate(scores):
            key = int(records["key"][i]); best[key] = max(best.get(key, -1), float(score))
        ranked = sorted(best, key=best.get, reverse=True)
        expected = 68_000_000 + int(chapter) * 1000 + int(verse)
        hits = [{"key": f"{key//1000000}:{key//1000%1000}:{key%1000}", "cosine": round(best[key],5)} for key in ranked[:5]]
        result = {"query":query, "expectedRank":ranked.index(expected)+1, "top5":hits}
        report.append(result)
        print(json.dumps(result, ensure_ascii=False), flush=True)
    (ROOT / ".tools/semantic/analects-evaluation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

if __name__ == "__main__":
    main()
