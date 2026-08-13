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

# Inherited functional tests remain intact; only exact successor build-version assertions move.
tests = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'
stage34_test = tests / 'FarolStage34Test.kt'
if stage34_test.exists():
    t = stage34_test.read_text()
    old34 = 'assertTrue(s.contains("versionCode = 5492")); assertTrue(s.contains("versionName = \\"0.1.208\\""))'
    new34 = 'assertTrue(s.contains("versionCode = 5494")); assertTrue(s.contains("versionName = \\"0.1.210\\""))'
    t = once(t, old34, new34, 'Stage38 inherited Stage34 version assertion')
    stage34_test.write_text(t)
stage36_test = tests / 'FarolStage36RuntimeTest.kt'
if stage36_test.exists():
    t = stage36_test.read_text()
    old36 = 'assertTrue(b.contains("versionCode = 5493"));assertTrue(b.contains("versionName = \\"0.1.209\\""))'
    new36 = 'assertTrue(b.contains("versionCode = 5494"));assertTrue(b.contains("versionName = \\"0.1.210\\""))'
    t = once(t, old36, new36, 'Stage38 inherited Stage36 version assertion')
    stage36_test.write_text(t)

print('stage38_report=PASS')
