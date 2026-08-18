#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'

b = BUILD.read_text(encoding='utf-8')
if b.count('versionCode = 5510') != 1 or b.count('versionName = "0.1.226"') != 1:
    raise SystemExit('expected exact R8 version 0.1.226/5510')
b = b.replace('versionCode = 5510', 'versionCode = 5521', 1)
b = b.replace('versionName = "0.1.226"', 'versionName = "0.1.228"', 1)
BUILD.write_text(b, encoding='utf-8')

code_changes = 0
name_changes = 0
for path in TESTS.glob('*.kt'):
    text = path.read_text(encoding='utf-8')
    c = text.count('versionCode = 5510')
    if c:
        text = text.replace('versionCode = 5510', 'versionCode = 5521')
        code_changes += c
    escaped = text.count('versionName = \\"0.1.226\\"')
    if escaped:
        text = text.replace('versionName = \\"0.1.226\\"', 'versionName = \\"0.1.228\\"')
        name_changes += escaped
    plain = text.count('versionName = "0.1.226"')
    if plain:
        text = text.replace('versionName = "0.1.226"', 'versionName = "0.1.228"')
        name_changes += plain
    path.write_text(text, encoding='utf-8')

if code_changes < 8 or name_changes < 8:
    raise SystemExit(f'unexpected R8 version assertion inventory: code={code_changes} name={name_changes}')
print(f'error1_version=PASS version=0.1.228/5521 test_version_assertions_code={code_changes} name={name_changes}')
