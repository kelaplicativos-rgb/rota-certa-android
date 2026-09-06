#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')

s = BUILD.read_text(encoding='utf-8')
if s.count('versionCode = 5503') != 1 or s.count('versionName = "0.1.219"') != 1:
    raise SystemExit('Stage46 R2 expected exact Stage46 R1 version 0.1.219/5503')
BUILD.write_text(
    s.replace('versionCode = 5503', 'versionCode = 5504', 1)
     .replace('versionName = "0.1.219"', 'versionName = "0.1.220"', 1),
    encoding='utf-8',
)
replace_once(
    TESTS / 'FarolStage34Test.kt',
    'assertTrue(s.contains("versionCode = 5503")); assertTrue(s.contains("versionName = \\"0.1.219\\""))',
    'assertTrue(s.contains("versionCode = 5504")); assertTrue(s.contains("versionName = \\"0.1.220\\""))',
    'Stage34 inherited R2 version assertion',
)
replace_once(
    TESTS / 'FarolStage36RuntimeTest.kt',
    'assertTrue(b.contains("versionCode = 5503"));assertTrue(b.contains("versionName = \\"0.1.219\\""))',
    'assertTrue(b.contains("versionCode = 5504"));assertTrue(b.contains("versionName = \\"0.1.220\\""))',
    'Stage36 inherited R2 version assertion',
)
print('stage46_r2_version=PASS versionName=0.1.220 versionCode=5504 inherited_version_assertions=2')
