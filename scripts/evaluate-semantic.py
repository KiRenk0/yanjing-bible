"""Inspect actual model retrieval over the complete corpus; no expression lexicon is used."""
from pathlib import Path
import json
import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

root = Path(__file__).resolve().parent.parent
assets = root / "app/src/main/assets"
rows = [line.rstrip("\n").split("\t", 4) for line in (assets / "bibles/cuvs.tsv").open(encoding="utf-8")]
tokenizer = Tokenizer.from_file(str(root / ".tools/semantic/tokenizer.json"))
tokenizer.enable_truncation(max_length=256)
options = ort.SessionOptions(); options.intra_op_num_threads = 2
session = ort.InferenceSession(str(assets / "semantic/bge-small-zh-int8.onnx"), sess_options=options, providers=["CPUExecutionProvider"])
vectors = np.memmap(root / ".tools/semantic/embeddings.f32", dtype=np.float32, mode="r", shape=(len(rows), 512))
queries = [
    "我忙得喘不过气，想歇一会儿", "总想着还没发生的事，心里七上八下", "别人伤害了我，我不想再记恨他",
    "说话先过脑子，少发脾气", "想从别人那里得到善意，就先善待别人", "做错了就坦白承认，不要掩饰",
    "我需要一杯水解渴", "做事情别半途而废", "不要以貌取人", "有困难的时候朋友才最重要",
    "帮忙把眼前的任务做好，别三心二意", "有些东西用钱也买不到", "自己不愿意的事也别强加给别人",
    "一味追求财富会不会让我失去更重要的东西", "Python怎么读取CSV文件", "电脑显卡驱动怎么安装",
]
results = []
for query in queries:
    token = tokenizer.encode("为这个句子生成表示以用于检索相关文章：" + query)
    inputs = {"input_ids": np.array([token.ids], dtype=np.int64), "attention_mask": np.array([token.attention_mask], dtype=np.int64), "token_type_ids": np.array([token.type_ids], dtype=np.int64)}
    embedding = session.run(None, inputs)[0][0,0]; embedding /= np.linalg.norm(embedding)
    scores = vectors @ embedding
    indices = np.argsort(-scores)[:5]
    top = [{"key": ":".join(rows[i][:3]), "text": rows[i][3], "cosine": round(float(scores[i]), 5)} for i in indices]
    results.append({"query": query, "top": top})
    print(query, "=>", [(v["key"],v["cosine"],v["text"][:40]) for v in top], flush=True)
(root / ".tools/semantic/evaluation.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
