#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BUILD = ROOT / "app/build.gradle.kts"

text = BUILD.read_text(encoding="utf-8")
if text.count("versionCode = 5520") != 1 or text.count('versionName = "0.1.227"') != 1:
    raise SystemExit("Stage47 update compatibility expected 0.1.227/5520 after Agenda materialization")

text = text.replace("versionCode = 5520", "versionCode = 5522", 1)
text = text.replace('versionName = "0.1.227"', 'versionName = "0.1.229"', 1)
BUILD.write_text(text, encoding="utf-8")

print(
    "stage47_update_compat_version=PASS "
    "version=0.1.229/5522 predecessor_known=0.1.228/5521 monotonic_update=true"
)
