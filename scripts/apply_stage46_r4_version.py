#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
s = BUILD.read_text(encoding='utf-8')

if s.count('versionCode = 5505') != 1:
    raise SystemExit(f'expected versionCode 5505 once, got {s.count("versionCode = 5505")}')
if s.count('versionName = "0.1.221"') != 1:
    raise SystemExit(f'expected versionName 0.1.221 once, got {s.count(chr(118)+"ersionName = "+chr(34)+"0.1.221"+chr(34))}')
s = s.replace('versionCode = 5505', 'versionCode = 5506', 1)
s = s.replace('versionName = "0.1.221"', 'versionName = "0.1.222"', 1)
BUILD.write_text(s, encoding='utf-8')
print('stage46_r4_version=PASS versionName=0.1.222 versionCode=5506')
