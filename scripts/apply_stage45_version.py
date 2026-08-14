#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
s = BUILD.read_text(encoding='utf-8')
if s.count('versionCode = 5501') != 1:
    raise SystemExit('expected Stage44 versionCode 5501 exactly once')
if s.count('versionName = "0.1.217"') != 1:
    raise SystemExit('expected Stage44 versionName 0.1.217 exactly once')
s = s.replace('versionCode = 5501', 'versionCode = 5502', 1)
s = s.replace('versionName = "0.1.217"', 'versionName = "0.1.218"', 1)
BUILD.write_text(s, encoding='utf-8')
print('stage45_version=PASS versionName=0.1.218 versionCode=5502')
