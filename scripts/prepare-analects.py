"""Rebuild Analects assets from the pinned source and project-authored retrieval glosses.

Offline by default. The original JSON is retained verbatim, including its paragraph
boundaries; numbering follows this edition and can differ from other editions.
"""
from pathlib import Path
import hashlib
import json
import re
import shutil
import unicodedata

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
DATA = ASSETS / "analects"
REVISION = "fa1814a8044db4fb0c7a9e0a0449974e8c3463b7"
SOURCE_SHA256 = "bc336a3839706377892719db44c48a09794a493190535a42bf1c7d4c4233e96c"
COUNTS = [16,24,26,26,28,30,38,21,31,27,26,24,30,44,42,14,26,11,25,3]

def sha(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()

def main():
    source = DATA / "lunyu.json"
    assert sha(source) == SOURCE_SHA256, "Pinned original source changed"
    chapters = json.loads(source.read_text(encoding="utf-8"))
    glosses = {}
    for line in (DATA / "search-glosses.txt").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        header = re.fullmatch(r"\[(\d+)\]", line)
        if header:
            chapter = int(header[1])
            assert chapter not in glosses
            glosses[chapter] = []
        else:
            assert "\t" not in line
            glosses[chapter].append(line)
    assert len(chapters) == 20 and set(glosses) == set(range(1,21))
    mapping = dict(line.split("\t") for line in (ASSETS / "t2s.tsv").read_text(encoding="utf-8").splitlines())
    def normalize(text):
        return "".join(mapping.get(char, char) for char in unicodedata.normalize("NFKC", text)).lower()
    rows = []
    for number, item in enumerate(chapters, 1):
        assert len(item["paragraphs"]) == len(glosses[number]) == COUNTS[number - 1], f"Wrong gloss count in chapter {number}"
        for verse, (text, gloss) in enumerate(zip(item["paragraphs"], glosses[number]), 1):
            assert text and not any(c in text for c in "\t\r\n")
            normalized = "".join(c for c in normalize(text) if c.isalnum())
            rows.append(f"68\t{number}\t{verse}\t{text}\t{normalized}\t{gloss}\n")
    corpus = ASSETS / "bibles/analects.tsv"
    corpus.write_text("".join(rows), encoding="utf-8", newline="\n")
    shutil.copyfile(ROOT / "third_party/chinese-poetry-LICENSE.txt", ASSETS / "chinese-poetry-LICENSE.txt")
    manifest = {
        "schema": 1, "source": "chinese-poetry/chinese-poetry", "revision": REVISION,
        "url": f"https://github.com/chinese-poetry/chinese-poetry/blob/{REVISION}/论语/lunyu.json",
        "license": "MIT", "chapters": 20, "verses": len(rows), "verseCounts": COUNTS,
        "numbering": "One source paragraph per chapter/verse key; other editions may divide paragraphs differently.",
        "retrievalGlosses": "Project-authored modern Chinese search aids; not original text or an authoritative translation.",
        "files": {p.name: {"bytes": p.stat().st_size, "sha256": sha(p)} for p in [source, DATA / "search-glosses.txt", corpus]},
    }
    (DATA / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {len(chapters)} chapters / {len(rows)} original paragraphs and modern Chinese retrieval glosses")

if __name__ == "__main__":
    main()
