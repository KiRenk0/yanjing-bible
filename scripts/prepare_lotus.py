"""Extract T0262's 28 pin divisions without commentary or apparatus.

The pinned CBETA XML and its intact header accompany the derived text. Run this
before prepare-classics.mjs; ordinary Android builds use the committed assets.
"""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
NS = {"t": "http://www.tei-c.org/ns/1.0", "cb": "http://www.cbeta.org/ns/1.0"}
SOURCE_NAME = "cbeta-T09n0262.xml"


def extract(source):
    root = ET.fromstring(source)

    def text(element):
        # Notes include mantra sequence numbers and phonetic commentary. Their
        # tails remain scripture. Gaiji already have Unicode content in this XML.
        if element.tag in {f"{{{NS['t']}}}note", f"{{{NS['cb']}}}yin"}:
            return ""
        result = element.text or ""
        for child in element:
            result += text(child) + (child.tail or "")
            if child.tag in {f"{{{NS['t']}}}l", f"{{{NS['t']}}}caesura"}:
                result += "　"
        return result

    chapters = []
    for pin in root.findall(".//t:body/cb:div[@type='pin']", NS):
        index = len(chapters) + 1
        mulu = pin.find("cb:mulu", NS)
        if mulu is None or int(mulu.attrib["n"]) != index:
            raise ValueError("Missing or unordered Lotus Sutra chapter")
        title = re.sub(r"^\d+\s*", "", "".join(mulu.itertext())).strip()
        paragraphs = []
        for block in pin:
            if block.tag not in {f"{{{NS['t']}}}p", f"{{{NS['t']}}}lg"}:
                continue
            value = re.sub(r"[\r\n\t]", "", text(block)).strip()
            if not value:
                raise ValueError(f"Empty scripture block in chapter {index}")
            paragraphs.append(value)
        if not paragraphs:
            raise ValueError(f"Empty chapter {index}")
        chapters.append({"title": title, "paragraphs": paragraphs})
    if len(chapters) != 28 or chapters[0]["title"] != "序品" or chapters[-1]["title"] != "普賢菩薩勸發品":
        raise ValueError("Expected complete T0262, 28 chapters")
    return chapters


def main():
    sources = json.loads((ROOT / "scripts/classic-sources.json").read_text(encoding="utf-8"))
    metadata = next(s for s in sources if s["file"] == SOURCE_NAME)
    source = (ROOT / "data/classics/sources" / SOURCE_NAME).read_bytes()
    if hashlib.sha256(source).hexdigest() != metadata["sha256"]:
        raise ValueError("CBETA source hash mismatch")
    chapters = extract(source)
    (ROOT / "data/classics/lotus-sutra.json").write_text(json.dumps(chapters, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # Preserve every byte inside the source header rather than reserializing XML.
    header_end = source.index(b"</teiHeader>") + len(b"</teiHeader>")
    (ROOT / "app/src/main/assets/classics/lotus-source-header.xml").write_bytes(source[:header_end] + b"\n</TEI>\n")
    print(f"Lotus Sutra: {len(chapters)} chapters, {sum(len(c['paragraphs']) for c in chapters)} paragraphs")


if __name__ == "__main__":
    main()
