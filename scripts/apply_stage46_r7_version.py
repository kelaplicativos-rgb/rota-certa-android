#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
s = BUILD.read_text(encoding='utf-8')

if s.count('versionCode = 5508') != 1:
    raise SystemExit(f'expected versionCode 5508 once, got {s.count("versionCode = 5508")}')
if s.count('versionName = "0.1.224"') != 1:
    raise SystemExit('expected versionName 0.1.224 exactly once')
s = s.replace('versionCode = 5508', 'versionCode = 5509', 1)
s = s.replace('versionName = "0.1.224"', 'versionName = "0.1.225"', 1)
BUILD.write_text(s, encoding='utf-8')
print('stage46_r7_version=PASS versionName=0.1.225 versionCode=5509')