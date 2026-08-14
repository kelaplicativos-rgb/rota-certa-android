#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / 'app/build.gradle.kts'
s = BUILD.read_text()
if s.count('versionCode = 5496') != 1:
    raise SystemExit('Stage41 expected versionCode 5496 exactly once')
if s.count('versionName = "0.1.212"') != 1:
    raise SystemExit('Stage41 expected versionName 0.1.212 exactly once')
s = s.replace('versionCode = 5496', 'versionCode = 5497', 1)
s = s.replace('versionName = "0.1.212"', 'versionName = "0.1.213"', 1)
BUILD.write_text(s)
print('stage41_version=0.1.213/5497')
