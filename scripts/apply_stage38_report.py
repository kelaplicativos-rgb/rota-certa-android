#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
PATCH_ROOT = Path(__file__).resolve().parents[1]

def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

def insert_before(text: str, anchor: str, addition: str, label: str) -> str:
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 anchor, got {count}')
    return text.replace(anchor, addition + anchor, 1)

report = PKG / 'ManualTechnicalReportBuilder.kt'
r = report.read_text()
r = once(
    r,
    '            appendLine(FarolForensicTraceStage20.exportReport())\n',
    '            appendLine(FarolMaximumForensicsStage38.exportReport())\n'
    '            appendLine()\n'
    '            appendLine(FarolForensicTraceStage20.exportReport())\n',
    'report Stage38',
)
report.write_text(r)

build = ROOT / 'app/build.gradle.kts'
b = build.read_text()
b = once(b, 'versionCode = 5493', 'versionCode = 5494', 'Stage38 versionCode')
b = once(b, 'versionName = "0.1.209"', 'versionName = "0.1.210"', 'Stage38 versionName')
build.write_text(b)

print('stage38_report=PASS')
