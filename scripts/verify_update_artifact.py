"""Check Room's reflective constructor in the actual minified APK, not debug classes."""
from pathlib import Path
import argparse
import re
import subprocess

DATABASE = "androidx.work.impl.WorkDatabase_Impl"


def verify_update_database(apk: Path, analyzer: Path):
    code = subprocess.check_output(
        [str(analyzer), "dex", "code", "--class", DATABASE, str(apk)],
        text=True, encoding="utf-8",
    )
    assert f"L{DATABASE.replace('.', '/')};" in code, "Update database class missing from APK"
    assert re.search(r"^\.method public constructor <init>\(\)V\s*$", code, re.MULTILINE), (
        "Update database public no-argument constructor missing from minified APK; "
        "Room initialization would throw NoSuchMethodException"
    )
    return {"database": DATABASE, "publicNoArgumentConstructor": True, "checkedArtifact": apk.name}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--apkanalyzer", required=True, type=Path)
    args = parser.parse_args()
    print(verify_update_database(args.apk.resolve(), args.apkanalyzer.resolve()))
