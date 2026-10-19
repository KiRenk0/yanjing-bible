"""Verify the built Release, source archive, corpus provenance and completed checks.

Usage: python scripts/verify-release.py --build-tools PATH_TO_ANDROID_BUILD_TOOLS
Run the full test/lint build and package-source.ps1 before this command.
"""
from pathlib import Path
import argparse
import hashlib
import gzip
import json
import os
import re
import struct
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from verify_update_artifact import verify_update_database

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
DIST = ROOT / "dist"
SIGNER = "35af01bb53bbbce5fcc462a8a9f2fd58e39bda2c27f94a7cf723ddb83c5473b2"

def sha(data):
    return hashlib.sha256(data).hexdigest()

def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--build-tools", required=True, type=Path)
    args = parser.parse_args()
    info = read_json(DIST / "update.json")
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    assert info["versionName"] == re.search(r'versionName\s*=\s*"([0-9.]+)"', gradle)[1]
    assert info["versionCode"] == int(re.search(r'versionCode\s*=\s*(\d+)', gradle)[1])
    apk_path = DIST / f"yanjing-{info['versionName']}.apk"
    source_path = DIST / f"yanjing-{info['versionName']}-source.zip"
    assert apk_path.stat().st_size == info["size"] and sha(apk_path.read_bytes()) == info["sha256"]
    arm_path = DIST / f"yanjing-{info['versionName']}-arm64-v8a.apk"
    assert arm_path.stat().st_size == info['arm64']['size'] and sha(arm_path.read_bytes()) == info['arm64']['sha256']
    assert (DIST / "SHA256SUMS.txt").read_text().strip() == f"{info['sha256']}  {apk_path.name}\n{info['arm64']['sha256']}  {arm_path.name}"
    tools = args.build_tools.resolve()
    analyzer = tools.parent.parent / "cmdline-tools/latest/bin" / ("apkanalyzer.bat" if os.name == "nt" else "apkanalyzer")
    # Both APKs are checked below to contain identical DEX files.
    update_database = verify_update_database(apk_path, analyzer)
    signature = subprocess.check_output([str(tools / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(apk_path)], text=True)
    assert f"certificate SHA-256 digest: {SIGNER}" in signature
    subprocess.run([str(tools / "zipalign.exe"), "-c", "-P", "16", "4", str(apk_path)], check=True, capture_output=True)
    badging = subprocess.check_output([str(tools / "aapt.exe"), "dump", "badging", str(apk_path)], text=True, encoding="utf-8")
    assert "application-debuggable" not in badging
    assert f"name='{info['applicationId']}' versionCode='{info['versionCode']}' versionName='{info['versionName']}'" in badging
    assert "sdkVersion:'26'" in badging
    arm_signature = subprocess.check_output([str(tools / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(arm_path)], text=True)
    assert f"certificate SHA-256 digest: {SIGNER}" in arm_signature
    subprocess.run([str(tools / "zipalign.exe"), "-c", "-P", "16", "4", str(arm_path)], check=True, capture_output=True)
    arm_badging = subprocess.check_output([str(tools / "aapt.exe"), "dump", "badging", str(arm_path)], text=True, encoding="utf-8")
    assert "application-debuggable" not in arm_badging
    assert f"name='{info['applicationId']}' versionCode='{info['versionCode']}' versionName='{info['versionName']}'" in arm_badging
    assert "native-code: 'arm64-v8a'" in arm_badging and "sdkVersion:'26'" in arm_badging
    assert arm_path.stat().st_size < apk_path.stat().st_size
    manifest_dump = subprocess.check_output([str(tools / "aapt.exe"), "dump", "xmltree", str(apk_path), "AndroidManifest.xml"], text=True, encoding="utf-8")
    assert re.search(r"extractNativeLibs.*0xffffffff",manifest_dump), "Compressed native libraries must be extracted by Android"
    assert "org.yanjing.app.StartupActivity" in manifest_dump
    assert "androidx.work.WorkManagerInitializer" not in manifest_dump, "Update scheduler must initialize on demand"
    assert "org.yanjing.app.updates" in manifest_dump and "android.support.FILE_PROVIDER_PATHS" in manifest_dump
    resources_dump = subprocess.check_output([str(tools / "aapt.exe"), "dump", "--values", "resources", str(apk_path)], text=True, encoding="utf-8")
    paths_file = re.search(r"resource 0x[0-9a-f]+ [^\n]*:xml/update_paths:[^\n]*\n[^\n]*\(string8\) \"([^\"]+)\"", resources_dump)
    assert paths_file, "Release must retain FileProvider path configuration"
    sharing_paths = subprocess.check_output([str(tools / "aapt.exe"), "dump", "xmltree", str(apk_path), paths_file[1]], text=True, encoding="utf-8")
    assert 'cache-path' in sharing_paths and 'diagnostics/' in sharing_paths
    mapping_path = DIST / f"yanjing-{info['versionName']}-mapping.txt.gz"
    mapping = gzip.decompress(mapping_path.read_bytes()).decode("utf-8")
    for name in ("ai.onnxruntime.OrtEnvironment","ai.onnxruntime.OrtSession","ai.onnxruntime.OnnxTensor",
                 "org.yanjing.app.update.CheckUpdateWorker","org.yanjing.app.update.DownloadUpdateWorker"):
        assert f"{name} -> {name}:" in mapping, f"R8 must retain JNI/previously scheduled worker name: {name}"

    with zipfile.ZipFile(apk_path) as apk:
        # Every shipped asset, including all original source files and licenses, must match.
        for path in ASSETS.rglob("*"):
            if path.is_file():
                relative = path.relative_to(ASSETS).as_posix()
                assert apk.read("assets/" + relative) == path.read_bytes(), relative
        for manifest_path in sorted((ASSETS / "semantic").glob("*manifest.json")):
            manifest = read_json(manifest_path)
            assert manifest["onnxRuntime"] == "1.25.1" and manifest["embeddingBatchSize"] == 1 and manifest["intraOpThreads"] == 2
            corpus = ASSETS / f"bibles/{manifest['sourceVersion']}.tsv"
            assert sha(corpus.read_bytes()) == manifest["corpusSha256"]
            for filename, expected in manifest["files"].items():
                data = apk.read("assets/semantic/" + filename)
                assert len(data) == expected["bytes"] and sha(data) == expected["sha256"]
        for manifest_name, hash_field in (("data-manifest.json", "assetSha256"), ("quran/manifest.json", "sha256")):
            for item in read_json(ASSETS / manifest_name)["versions"]:
                corpus = (ASSETS / f"bibles/{item['id']}.tsv").read_bytes()
                assert sha(corpus) == item[hash_field]
                assert sum(bool(line) and not line.startswith("#") for line in corpus.decode("utf-8").splitlines()) == item["verses"]
        native = []
        for entry in apk.namelist():
            if not entry.startswith("lib/") or not entry.endswith(".so"):
                continue
            data = apk.read(entry)
            assert apk.getinfo(entry).compress_type == zipfile.ZIP_DEFLATED, entry
            assert data[:4] == b"\x7fELF" and data[5] == 1
            is64 = data[4] == 2
            phoff = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
            stride, count = struct.unpack_from("<HH", data, 54 if is64 else 42)
            loads = 0
            for i in range(count):
                pos = phoff + stride * i
                if struct.unpack_from("<I", data, pos)[0] != 1:
                    continue
                offset, addr = struct.unpack_from("<QQ" if is64 else "<II", data, pos + (8 if is64 else 4))
                alignment = struct.unpack_from("<Q" if is64 else "<I", data, pos + (48 if is64 else 28))[0]
                assert alignment >= 16384 and offset % 16384 == addr % 16384, entry
                loads += 1
            assert loads > 0
            native.append(entry)
        assert {name.split('/')[1] for name in native} == {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
        for name in ("bge-small-zh-int8.onnx","verses.i8","quran.i8","analects.i8"):
            assert apk.getinfo("assets/semantic/"+name).compress_type == zipfile.ZIP_DEFLATED
        with zipfile.ZipFile(arm_path) as arm:
            assert {n.split('/')[1] for n in arm.namelist() if n.startswith('lib/') and n.endswith('.so')} == {'arm64-v8a'}
            for entry in arm.namelist():
                if entry.startswith(('assets/','lib/')) or entry.endswith('.dex'):
                    assert arm.read(entry) == apk.read(entry), entry

    classic_manifest = read_json(ASSETS / 'classics/manifest.json')
    for source in classic_manifest['sources']:
        assert sha((ROOT / 'data/classics/sources' / source['file']).read_bytes()) == source['sha256']
    classic_summary = []
    for work in classic_manifest['works']:
        corpus = ASSETS / f"bibles/{work['id']}.tsv"
        assert sha(corpus.read_bytes()) == work['sha256']
        rows = [line.split('\t') for line in corpus.read_text(encoding='utf-8').splitlines()]
        assert len(rows) == work['verses'] == sum(work['verseCounts'])
        assert [tuple(map(int,row[:3])) for row in rows] == [(work['book'],i,j) for i,n in enumerate(work['verseCounts'],1) for j in range(1,n+1)]
        index = read_json(ASSETS / f"semantic/{work['id']}-manifest.json")
        assert index['verses'] == len(rows)
        squash = lambda text: re.sub(r'\s+', '', text)
        raw_dir = ROOT / 'data/classics/sources'
        if work['id'] in ('daxue','zhongyong','mengzi'):
            original = read_json(raw_dir / f"chinese-poetry-{work['id']}.json")
            chapters = original if isinstance(original,list) else [original]
            assert [r[3] for r in rows] == [p for chapter in chapters for p in chapter['paragraphs']]
        elif work['id'] == 'tao_te_ching':
            original = read_json(raw_dir / 'banghuazhao-道德经_json.json')['道德经']
            assert [squash(r[3]) for r in rows] == [squash(c['章节内容']) for c in original]
        elif work['id'] == 'zhuangzi':
            original = [c for part in ('内篇_json','外篇','杂篇') for chapters in read_json(raw_dir / f'banghuazhao-庄子{part}.json').values() for c in chapters]
            assert squash(''.join(r[3] for r in rows)) == squash(''.join(c['章节内容'] for c in original))
        else:
            filename = 'hooosberg-zh-Hant.md' if work['id']=='heart_sutra' else 'hooosberg-zh-Hant_kumarajiva.md'
            body = (raw_dir / filename).read_text(encoding='utf-8').split('---',2)[2]
            body = '\n'.join(line for line in body.splitlines() if not line.startswith(('#','*')))
            assert squash(''.join(r[3] for r in rows)) == squash(body)
        classic_summary.append({'id':work['id'],'chapters':len(work['chapterNames']),'paragraphs':len(rows),'semanticPassages':index['passages']})
    latin_manifest = read_json(ASSETS / 'latin/manifest.json')
    assert sha((ROOT / 'data/latin/VulgClementine.json').read_bytes()) == latin_manifest['sourceSha256']
    assert sha((ROOT / 'data/latin/canon_vulg.h').read_bytes()) == latin_manifest['versificationSource']['sourceSha256']
    assert sha((ASSETS / 'latin/mapping.tsv').read_bytes()) == latin_manifest['mappingSha256']
    assert sha((ASSETS / 'bibles/vulgate.tsv').read_bytes()) == latin_manifest['assetSha256']
    raw = read_json(ROOT / 'data/latin/VulgClementine.json')
    latin_rows = [line.split('\t') for line in (ASSETS / 'bibles/vulgate.tsv').read_text(encoding='utf-8').splitlines()]
    original_rows = [(str(i+101),str(c['chapter']),str(v['verse']),re.sub(r'[\t\r\n]+',' ',v['text']).strip()) for i,b in enumerate(raw['books']) for c in b['chapters'] for v in c['verses'] if v['text'].strip()]
    assert len(latin_rows) == 35809 and len({r[0] for r in latin_rows}) == 73
    assert [tuple(r[:4]) for r in latin_rows] == original_rows

    manifest = read_json(ASSETS / "analects/manifest.json")
    for filename, expected in manifest["files"].items():
        path = ASSETS / ("bibles/" if filename == "analects.tsv" else "analects/") / filename
        assert path.stat().st_size == expected["bytes"] and sha(path.read_bytes()) == expected["sha256"]
    original = read_json(ASSETS / "analects/lunyu.json")
    rows = [line.split("\t") for line in (ASSETS / "bibles/analects.tsv").read_text(encoding="utf-8").splitlines()]
    assert len(original) == 20 and len(rows) == 512
    assert [r[3] for r in rows] == [text for chapter in original for text in chapter["paragraphs"]]
    expected_keys = [("68",str(i),str(j)) for i,chapter in enumerate(original,1) for j in range(1,len(chapter["paragraphs"])+1)]
    assert [tuple(r[:3]) for r in rows] == expected_keys and all(len(r) == 6 and r[5] for r in rows)
    semantic = read_json(ASSETS / "semantic/analects-manifest.json")
    with zipfile.ZipFile(source_path) as source:
        names = source.namelist()
        assert not any(part in {".tools", ".git", ".gradle", "build", "dist", "node_modules", "__pycache__"} for name in names for part in name.split("/"))
        assert not any(name.endswith((".jks", ".keystore", ".p12", "local.properties", "signing.properties")) for name in names)
        for name in names:
            if not name.endswith("/"):
                assert source.read(name) == (ROOT / name).read_bytes(), name
        for path in ASSETS.rglob("*"):
            if path.is_file():
                assert path.relative_to(ROOT).as_posix() in names
    suites = []
    for path in sorted((ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        suites.append({key: suite.attrib[key] if key == "name" else int(suite.attrib[key]) for key in ("name", "tests", "failures", "errors", "skipped")})
    assert len(suites) >= 10 and all(s["failures"] == s["errors"] == s["skipped"] == 0 for s in suites)
    assert any(s["name"].endswith("AnalectsSearchTest") and s["tests"] >= 6 for s in suites)
    assert any(s["name"].endswith("RetrievalEvaluationTest") and s["tests"] >= 1 for s in suites)
    assert any(s["name"].endswith("SemanticRankerTest") and s["tests"] >= 4 for s in suites)
    assert any(s["name"].endswith("Android17StartupTest") and s["tests"] >= 1 for s in suites)
    assert any(s["name"].endswith("StartupRecoveryTest") and s["tests"] >= 8 for s in suites)
    assert any(s['name'].endswith('ClassicsAndLatinSearchTest') and s['tests'] >= 4 for s in suites)
    assert any(s['name'].endswith('ClassicsRepositoryTest') and s['tests'] >= 2 for s in suites)
    assert any(s['name'].endswith('ArchitectureUpdateTest') and s['tests'] >= 2 for s in suites)
    assert any(s['name'].endswith('UpdateManagerTest') and s['tests'] >= 4 for s in suites)
    issues = ET.parse(ROOT / "app/build/reports/lint-results-debug.xml").getroot().findall("issue")
    assert not [i for i in issues if i.attrib["severity"] in ("Error", "Fatal")]
    warnings = [i.attrib["id"] for i in issues if i.attrib["severity"] == "Warning"]
    judgments = {}
    for line in (ROOT / "data/semantic-evaluation.tsv").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"): continue
        split,kind,query,expected=line.split("\t")
        for corpus in ("bible","quran","analects") if kind == "all" else (kind,):
            judgments[(split,corpus,query)] = expected != "-"
    eval_path = ROOT / "app/build/reports/semantic-evaluation.tsv"
    eval_rows = [line.split("\t") for line in eval_path.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")]
    assert len(eval_rows) == len(judgments) == 91
    positive = [r for r in eval_rows if judgments[tuple(r[:3])]]
    negative = [r for r in eval_rows if not judgments[tuple(r[:3])]]
    def metric(rows,column,cutoff):
        return sum(r[column] != "-" and int(r[column]) <= cutoff for r in rows)
    evaluation = {"positiveQueries":len(positive),"outOfScopeQueries":len(negative),
                  "top1Before":metric(positive,3,1),"top1After":metric(positive,4,1),
                  "top5Before":metric(positive,3,5),"top5After":metric(positive,4,5),
                  "correctAbstentionsBefore":sum(int(r[5])==0 for r in negative),"correctAbstentionsAfter":sum(int(r[6])==0 for r in negative),
                  "datasetSha256":sha((ROOT / "data/semantic-evaluation.tsv").read_bytes()),"resultsSha256":sha(eval_path.read_bytes()),
                  "limitations":"Hand-authored, incomplete relevance judgments; development and validation examples informed parameter selection; not a blind or exhaustive accuracy estimate"}
    assert evaluation["top1After"] >= evaluation["top1Before"] and evaluation["top5After"] >= evaluation["top5Before"] + 4
    assert evaluation["correctAbstentionsAfter"] >= evaluation["correctAbstentionsBefore"]
    previous_bytes = 181334116
    report = {
        "date": "2026-10-19", "applicationId": info["applicationId"], "version": info["versionName"], "versionCode": info["versionCode"],
        "minimumAndroid": "8.0 (API 26)", "targetSdk": 37,
        "tests": sum(s["tests"] for s in suites), "failures": 0, "suites": suites,
        "lint": {"errors": 0, "warnings": len(warnings), "warningIds": sorted(set(warnings))},
        "apkBytes": apk_path.stat().st_size, "apkSha256": info["sha256"],
        "apkSizeComparison":{"previousVersion":"1.3.0","previousBytes":previous_bytes,"currentBytes":apk_path.stat().st_size,
                             "reductionPercent":round(100*(1-apk_path.stat().st_size/previous_bytes),2),
                             "strategy":"R8/resource shrinking; compressed model, vectors and native libraries; all four ABIs retained; native libraries extracted at install"},
        "mappingSha256":sha(mapping_path.read_bytes()),"semanticEvaluation":evaluation,
        "classics":classic_summary,
        "latin":{'books':73,'verses':35809,'mappedVerses':latin_manifest['mappedVerses'],'mappingSha256':latin_manifest['mappingSha256'],
                 'numbering':'Native Vulgate book/chapter/verse keys; known shared references mapped with pinned CrossWire data; unmatched material is readable and searchable by original text'},
        "arm64":{'apkBytes':arm_path.stat().st_size,'apkSha256':info['arm64']['sha256'],'previousUniversalBytes':91014763,
                 'reductionFromPreviousUniversalPercent':round(100*(1-arm_path.stat().st_size/91014763),2),
                 'verification':'Same signer, version and assets/dex as universal; only arm64 native libraries; zipalign 16 KB check passed'},
        "sourceBytes": source_path.stat().st_size, "sourceSha256": sha(source_path.read_bytes()), "sourceFiles": len(names),
        "signerSha256": SIGNER, "signing": "apksigner verified; same certificate as previous releases; release is non-debuggable",
        "alignment": "zipalign -c -P 16 4 passed; all ELF LOAD segments align to at least 16 KB", "nativeLibraries": native,
        "analects": {"chapters": 20, "verses": 512, "modernRetrievalGlosses": 512, "semanticPassages": semantic["passages"], "revision": manifest["revision"],
                     "paraphraseChecks": "Twelve modern Chinese queries absent from the expression dictionary retrieve the expected original in the top five using the actual ONNX model",
                     "integrity": "All originals match pinned JSON paragraphs; all APK assets and source archive entries match workspace bytes"},
        "deviceTesting": "Robolectric API 35/36 Compose flows; API 37 direct framework/native-graphics startup; API 36/37 platform recovery and scheduler/palette failure injection; real ONNX inference; no physical Android device verification",
        "startupCompatibility": {"onDemandWorkManager": True, "platformRecoveryScreen": True,
                                 "reportedDevice": "Xiaomi 15 Pro; HyperOS 4.0.0.17.XOBCNXM.D01; Android 17",
                                 "feedback": "User confirmed 1.4.1 launches, then supplied 1.5.0 diagnostics showing Room's reflective constructor was missing; fixed in this release and checked in minified DEX; no agent-controlled physical-device testing"},
        "updateDatabase": update_database,
        "updateValidation": "Minified APK retains Room's reflective public no-argument constructor; metadata, resumable download, package/version/hash/signer rejection, READY state preservation and in-app update UI regression checks passed",
    }
    (DIST / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("version", "tests", "failures", "apkSha256", "lint")}))

if __name__ == "__main__":
    main()
