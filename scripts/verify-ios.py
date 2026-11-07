"""Validate the actual unsigned arm64 IPA and its shared corpus before release upload."""
from pathlib import Path
import hashlib
import json
import plistlib
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / 'ios/build'
ipa = BUILD / 'yanjing-1.7.0-ios-unsigned.ipa'
simulator = BUILD / 'yanjing-1.7.0-ios-simulator.zip'
summary = json.loads(subprocess.check_output(['xcrun','xcresulttool','get','test-results','summary','--path',str(BUILD / 'Tests.xcresult')],text=True))
assert summary['failedTests'] == 0 and summary['passedTests'] >= 8, summary
sha = lambda data: hashlib.sha256(data).hexdigest()
prefix = 'Payload/YanJing.app/'
with zipfile.ZipFile(ipa) as archive:
    info = plistlib.loads(archive.read(prefix + 'Info.plist'))
    assert info['CFBundleIdentifier'] == 'org.yanjing.app'
    assert info['CFBundleShortVersionString'] == '1.7.0'
    assert info['MinimumOSVersion'] == '18.0'
    executable = archive.read(prefix + info['CFBundleExecutable'])
    assert executable[:4] == bytes.fromhex('cffaedfe') and struct.unpack_from('<I',executable,4)[0] == 0x0100000c, 'Device binary must be arm64 Mach-O'
    manifest = json.loads(archive.read(prefix + 'Corpus/bundle-manifest.json'))
    for item in manifest['files']:
        actual = archive.read(prefix + 'Corpus/' + item['path'])
        assert len(actual) == item['bytes'] and sha(actual) == item['sha256'], item['path']
        assert actual == (ROOT / 'app/src/main/assets' / item['path']).read_bytes(), item['path']
    for language in ('en','zh-Hans','zh-Hant'):
        assert prefix + language + '.lproj/Localizable.strings' in archive.namelist(), language
    assert len(archive.read(prefix + 'Corpus/Licenses.txt')) > 1000
    assert prefix + 'PrivacyInfo.xcprivacy' in archive.namelist()
report = {
    'version': '1.7.0', 'build': 13,
    'sourceCommit': subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
    'xcode': subprocess.check_output(['xcodebuild','-version'],text=True).strip(),
    'tests': summary['passedTests'], 'failures': summary['failedTests'],
    'signing': 'Unsigned arm64 device build; requires local signing with a sideload tool. No paid developer membership required for personal free-Apple-ID signing.',
    'deviceTesting': 'Automated iPhone simulator tests, actual local ONNX inference and source-byte validation. No physical-device sideload installation test.',
    'minimumIOS': '18.0', 'languages': ['en','zh-Hans','zh-Hant'],
    'corpusFilesVerified': len(manifest['files']),
    'artifacts': [{'name': p.name, 'bytes': p.stat().st_size, 'sha256': sha(p.read_bytes())} for p in (ipa,simulator)]
}
(BUILD / 'ios-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
(BUILD / 'ios-test-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
