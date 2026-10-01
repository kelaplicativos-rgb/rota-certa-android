#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'

s = BUILD.read_text()
if s.count('versionCode = 5496') != 1:
    raise SystemExit('Stage41 expected versionCode 5496 exactly once')
if s.count('versionName = "0.1.212"') != 1:
    raise SystemExit('Stage41 expected versionName 0.1.212 exactly once')
s = s.replace('versionCode = 5496', 'versionCode = 5497', 1)
s = s.replace('versionName = "0.1.212"', 'versionName = "0.1.213"', 1)
BUILD.write_text(s)

version_assertions = [
    (
        TESTS / 'FarolStage34Test.kt',
        'assertTrue(s.contains("versionCode = 5496")); assertTrue(s.contains("versionName = \\"0.1.212\\""))',
        'assertTrue(s.contains("versionCode = 5497")); assertTrue(s.contains("versionName = \\"0.1.213\\""))',
        'Stage34 inherited Stage41 version assertion',
    ),
    (
        TESTS / 'FarolStage36RuntimeTest.kt',
        'assertTrue(b.contains("versionCode = 5496"));assertTrue(b.contains("versionName = \\"0.1.212\\""))',
        'assertTrue(b.contains("versionCode = 5497"));assertTrue(b.contains("versionName = \\"0.1.213\\""))',
        'Stage36 inherited Stage41 version assertion',
    ),
]

for path, old, new, label in version_assertions:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 inherited 0.1.212 assertion, got {count}')
    path.write_text(text.replace(old, new, 1))

check = BUILD.read_text()
assert 'versionCode = 5497' in check
assert 'versionName = "0.1.213"' in check
for path, _, new, label in version_assertions:
    assert path.read_text().count(new) == 1, label

print('stage41_version=PASS versionName=0.1.213 versionCode=5497 inherited_version_assertions=2')
