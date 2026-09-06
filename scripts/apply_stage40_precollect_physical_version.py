#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
path = root / "app/build.gradle.kts"
text = path.read_text(encoding="utf-8")

old_code = "versionCode = 5495"
old_name = 'versionName = "0.1.211"'
new_code = "versionCode = 5496"
new_name = 'versionName = "0.1.212"'

if old_code not in text or old_name not in text:
    raise SystemExit("Stage40 bounded physical version expected exact authority version 0.1.211 / 5495")
if new_code in text or new_name in text:
    raise SystemExit("Stage40 bounded physical version already materialized unexpectedly")

text = text.replace(old_code, new_code, 1).replace(old_name, new_name, 1)
path.write_text(text, encoding="utf-8")

tests = root / "app/src/test/java/br/com/mapeiaia/rotacerta"
version_assertions = [
    (
        tests / "FarolStage34Test.kt",
        'assertTrue(s.contains("versionCode = 5495")); assertTrue(s.contains("versionName = \\"0.1.211\\""))',
        'assertTrue(s.contains("versionCode = 5496")); assertTrue(s.contains("versionName = \\"0.1.212\\""))',
        "Stage34 inherited Stage40 version assertion",
    ),
    (
        tests / "FarolStage36RuntimeTest.kt",
        'assertTrue(b.contains("versionCode = 5495"));assertTrue(b.contains("versionName = \\"0.1.211\\""))',
        'assertTrue(b.contains("versionCode = 5496"));assertTrue(b.contains("versionName = \\"0.1.212\\""))',
        "Stage36 inherited Stage40 version assertion",
    ),
]

for test_path, old, new, label in version_assertions:
    test_text = test_path.read_text(encoding="utf-8")
    count = test_text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 0.1.211 assertion, got {count}")
    test_path.write_text(test_text.replace(old, new, 1), encoding="utf-8")

check = path.read_text(encoding="utf-8")
assert new_code in check
assert new_name in check
assert old_code not in check
assert old_name not in check
for test_path, _, new, label in version_assertions:
    assert test_path.read_text(encoding="utf-8").count(new) == 1, label

print("stage40_precollect_physical_version=PASS versionName=0.1.212 versionCode=5496 inherited_version_assertions=2")
