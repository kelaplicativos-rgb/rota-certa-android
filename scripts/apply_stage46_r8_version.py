#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
s = BUILD.read_text(encoding='utf-8')
if s.count('versionCode = 5509') != 1:
    raise SystemExit(f'expected versionCode 5509 once, got {s.count("versionCode = 5509")}')
if s.count('versionName = "0.1.225"') != 1:
    raise SystemExit('expected versionName 0.1.225 exactly once')
s = s.replace('versionCode = 5509', 'versionCode = 5510', 1)
s = s.replace('versionName = "0.1.225"', 'versionName = "0.1.226"', 1)
BUILD.write_text(s, encoding='utf-8')
print('stage46_r8_version=PASS versionName=0.1.226 versionCode=5510')
