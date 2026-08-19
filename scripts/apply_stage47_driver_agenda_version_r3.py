#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / "app/build.gradle.kts"

text = BUILD.read_text(encoding="utf-8")
if text.count("versionCode = 5522") != 1 or text.count('versionName = "0.1.229"') != 1:
    raise SystemExit("Stage47 R3 expected update-compatible predecessor 0.1.229/5522")
text = text.replace("versionCode = 5522", "versionCode = 5523", 1)
text = text.replace('versionName = "0.1.229"', 'versionName = "0.1.230"', 1)
BUILD.write_text(text, encoding="utf-8")
print("stage47_driver_agenda_version_r3=PASS version=0.1.230/5523 predecessor=0.1.229/5522 monotonic_update=true")
