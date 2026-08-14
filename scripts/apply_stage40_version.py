#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
path = root / "app/build.gradle.kts"
text = path.read_text(encoding="utf-8")

old_code = "versionCode = 5494"
old_name = 'versionName = "0.1.210"'
new_code = "versionCode = 5495"
new_name = 'versionName = "0.1.211"'

if old_code not in text or old_name not in text:
    raise SystemExit("Stage40 version materializer expected exact Stage38 version 0.1.210 / 5494")
if new_code in text or new_name in text:
    raise SystemExit("Stage40 version already materialized unexpectedly")

text = text.replace(old_code, new_code, 1).replace(old_name, new_name, 1)
path.write_text(text, encoding="utf-8")

check = path.read_text(encoding="utf-8")
assert new_code in check
assert new_name in check
assert old_code not in check
assert old_name not in check
print("stage40_version=PASS versionName=0.1.211 versionCode=5495")
