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
if s.count('versionCode = 5501') != 1:
    raise SystemExit('expected Stage44 versionCode 5501 exactly once')
if s.count('versionName = "0.1.217"') != 1:
    raise SystemExit('expected Stage44 versionName 0.1.217 exactly once')
BUILD.write_text(
    s.replace('versionCode = 5501', 'versionCode = 5502', 1)
     .replace('versionName = "0.1.217"', 'versionName = "0.1.218"', 1),
    encoding='utf-8',
)

replace_once(
    TESTS / 'FarolStage34Test.kt',
    'assertTrue(s.contains("versionCode = 5501")); assertTrue(s.contains("versionName = \\"0.1.217\\""))',
    'assertTrue(s.contains("versionCode = 5502")); assertTrue(s.contains("versionName = \\"0.1.218\\""))',
    'Stage34 inherited version assertion',
)
replace_once(
    TESTS / 'FarolStage36RuntimeTest.kt',
    'assertTrue(b.contains("versionCode = 5501"));assertTrue(b.contains("versionName = \\"0.1.217\\""))',
    'assertTrue(b.contains("versionCode = 5502"));assertTrue(b.contains("versionName = \\"0.1.218\\""))',
    'Stage36 inherited version assertion',
)

print('stage45_version=PASS versionName=0.1.218 versionCode=5502 inherited_version_assertions=2')
