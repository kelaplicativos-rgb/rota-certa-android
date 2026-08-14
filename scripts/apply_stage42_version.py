#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(sys.argv[1]).resolve(); BUILD=ROOT/'app/build.gradle.kts'; TESTS=ROOT/'app/src/test/java/br/com/mapeiaia/rotacerta'
s=BUILD.read_text()
if s.count('versionCode = 5497') != 1: raise SystemExit('Stage42 expected versionCode 5497 exactly once')
if s.count('versionName = "0.1.213"') != 1: raise SystemExit('Stage42 expected versionName 0.1.213 exactly once')
s=s.replace('versionCode = 5497','versionCode = 5498',1).replace('versionName = "0.1.213"','versionName = "0.1.214"',1)
BUILD.write_text(s)
checks=[
 (TESTS/'FarolStage34Test.kt','assertTrue(s.contains("versionCode = 5497")); assertTrue(s.contains("versionName = \\"0.1.213\\""))','assertTrue(s.contains("versionCode = 5498")); assertTrue(s.contains("versionName = \\"0.1.214\\""))','Stage34 version'),
 (TESTS/'FarolStage36RuntimeTest.kt','assertTrue(b.contains("versionCode = 5497"));assertTrue(b.contains("versionName = \\"0.1.213\\""))','assertTrue(b.contains("versionCode = 5498"));assertTrue(b.contains("versionName = \\"0.1.214\\""))','Stage36 version'),
]
for p,o,n,label in checks:
 t=p.read_text(); c=t.count(o)
 if c!=1: raise SystemExit(f'{label}: expected inherited assertion once, got {c}')
 p.write_text(t.replace(o,n,1))
print('stage42_version=PASS versionName=0.1.214 versionCode=5498 inherited_version_assertions=2')
