#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / "app/build.gradle.kts"
text = BUILD.read_text(encoding="utf-8")
if text.count("versionCode = 5523") != 1 or text.count('versionName = "0.1.230"') != 1:
    raise SystemExit("Step6 expected Stage47 R3 predecessor 0.1.230/5523")
text = text.replace("versionCode = 5523", "versionCode = 5524", 1)
text = text.replace('versionName = "0.1.230"', 'versionName = "0.1.231"', 1)
BUILD.write_text(text, encoding="utf-8")
print("stage47_r4_step6_version=PASS version=0.1.231/5524")
