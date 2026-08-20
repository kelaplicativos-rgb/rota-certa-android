#!/usr/bin/env python3
from pathlib import Path
import sys

source = Path(sys.argv[1]).resolve()
build = source / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")

old_code = "versionCode = 5524"
old_name = 'versionName = "0.1.231"'
new_code = "versionCode = 5525"
new_name = 'versionName = "0.1.232"'

if text.count(old_code) != 1 or text.count(old_name) != 1:
    raise SystemExit("Step7 version source is not the validated Step6 0.1.231/5524 state")

build.write_text(text.replace(old_code, new_code, 1).replace(old_name, new_name, 1), encoding="utf-8")
print("stage47_r4_step7_version=PASS version=0.1.232/5525")
