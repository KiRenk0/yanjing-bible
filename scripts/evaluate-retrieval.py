"""Measure retrieval against explicit example judgments, using the shipped ONNX/indexes.

The dev/holdout split is fixed before ranking changes. These are limited examples,
not comprehensive human relevance labels or a claim about all user queries.
"""
from pathlib import Path
import argparse
import hashlib
import json
import struct
import unicodedata
from ranking_reference import Ranker
import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
CORPORA = {"bible":("cuvs","verses"), "quran":("quran_zh","quran"), "analects":("analects","analects")}

def load_index(name,directory):
    data = (directory / f"{name}.i8").read_bytes()
    count, dimensions, _ = struct.unpack_from("<III",data,8)
    rows = np.frombuffer(data,dtype=np.dtype([("key","<u4"),("norm","<f4"),("vector","i1",(dimensions,))]),count=count,offset=20)
    vectors = rows["vector"].astype(np.float32) * rows["norm"][:,None]
    return rows["key"],vectors

def key_string(key):
    return f"{key//1000000}:{key//1000%1000}:{key%1000}"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / ".tools/semantic/retrieval-baseline.json")
    parser.add_argument("--mode", choices=["baseline","refined"], default="baseline")
    parser.add_argument("--split", choices=["dev","holdout","all"], default="all")
    parser.add_argument("--index-dir", type=Path, default=ASSETS / "semantic")
    parser.add_argument("--export-baseline", type=Path)
    args = parser.parse_args()
    tokenizer = Tokenizer.from_file(str(ROOT / ".tools/semantic/tokenizer.json"))
    tokenizer.enable_truncation(max_length=256)
    options=ort.SessionOptions(); options.intra_op_num_threads=2; options.inter_op_num_threads=1
    model=ort.InferenceSession(str(ASSETS / "semantic/bge-small-zh-int8.onnx"),sess_options=options,providers=["CPUExecutionProvider"])
    indexes = {kind:load_index(index,args.index_dir) for kind,(_,index) in CORPORA.items()}
    rankers = {kind:Ranker(version) for kind,(version,_) in CORPORA.items()} if args.mode=="refined" else {}
    mapping = dict(line.split('\t') for line in (ASSETS / "t2s.tsv").read_text(encoding="utf-8").splitlines())
    cache_file = ROOT / f".tools/semantic/evaluation-query-cache-{ort.__version__}-nfkc-v1.json"
    cache = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}
    results=[]
    for line in (ROOT / "data/semantic-evaluation.tsv").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"): continue
        split,corpus,query,expected=line.split("\t")
        if args.split!="all" and split!=args.split: continue
        if query not in cache:
            # Match SemanticRepository/TextNormalizer.forSemantic exactly, including punctuation.
            normalized=''.join(mapping.get(c,c) for c in unicodedata.normalize('NFKC',query))
            tokens=tokenizer.encode("为这个句子生成表示以用于检索相关文章："+normalized)
            inputs={"input_ids":np.array([tokens.ids],dtype=np.int64),"attention_mask":np.array([tokens.attention_mask],dtype=np.int64),"token_type_ids":np.array([tokens.type_ids],dtype=np.int64)}
            vector=model.run(None,inputs)[0][0,0]; vector/=np.linalg.norm(vector)
            cache[query]=vector.tolist()
        vector=np.array(cache[query],dtype=np.float32)
        for kind in CORPORA if corpus=="all" else [corpus]:
            keys,vectors=indexes[kind]; scores=vectors@vector
            best={}
            for i in np.argsort(-scores):
                key=key_string(int(keys[i]))
                if key not in best: best[key]=float(scores[i])
            ranked=list(best)
            candidates=ranked[:120]
            if args.mode=="refined":
                candidates=list(dict.fromkeys(candidates+rankers[kind].seeds(query)))
                refined=rankers[kind].rank(query,[{"key":key,"cosine":best[key]} for key in candidates])
                actual=[h['key'] for h in refined]
            else:
                actual=[key for key in ranked if best[key]>=0.5][:60]
            relevant=set() if expected=="-" else set(expected.split("|"))
            result={"split":split,"corpus":kind,"query":query,"expected":sorted(relevant),"rank":next((i+1 for i,key in enumerate(actual) if key in relevant),None),
                    "baselineHits":len(actual),"candidates":[{"key":key,"cosine":round(best[key],7)} for key in candidates],
                    "top5":actual[:5]}
            results.append(result)
    cache_file.write_text(json.dumps(cache,ensure_ascii=False),encoding="utf-8")
    metrics={}
    for split in ("dev","holdout","all"):
        cases=[r for r in results if split=="all" or r["split"]==split]
        positive=[r for r in cases if r["expected"]]; negative=[r for r in cases if not r["expected"]]
        metrics[split]={"positiveQueries":len(positive),"top1":sum(r["rank"]==1 for r in positive),"top5":sum(r["rank"] is not None and r["rank"]<=5 for r in positive),
                       "mrr":round(sum(1/r["rank"] if r["rank"] else 0 for r in positive)/max(len(positive),1),4),"outOfScopeQueries":len(negative),"correctAbstentions":sum(r["baselineHits"]==0 for r in negative)}
    report={"mode":args.mode,"onnxRuntime":ort.__version__,"baseline":"v1.3.0 cosine >= 0.5; max passage per original; at most 60 results","datasetSha256":hashlib.sha256((ROOT / "data/semantic-evaluation.tsv").read_bytes()).hexdigest(),"metrics":metrics,"results":results}
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    if args.export_baseline:
        assert args.mode == "baseline" and args.split == "all"
        rows=["# datasetSha256="+report["datasetSha256"]+"\n",
              "# v1.3.0 indexes; ONNX Runtime 1.25.1; identical Android NFKC/traditional conversion; explicit example judgments.\n",
              "# split\tcorpus\tquery\texpected-rank-or--\treturned-hits\ttop-five-keys\n"]
        for result in results:
            rows.append('\t'.join([result['split'],result['corpus'],result['query'],str(result['rank'] or '-'),str(result['baselineHits']),'|'.join(result['top5']) or '-'])+'\n')
        args.export_baseline.write_text(''.join(rows),encoding="utf-8",newline="\n")
    print(json.dumps(metrics,ensure_ascii=False,indent=2))
    print("Evaluation saved: "+str(args.output))

if __name__ == "__main__": main()
