#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'
files = (
    'FarolStage46VisualEpochNoResultTest.kt',
    'FarolStage46TargetSurfaceR2Test.kt',
    'FarolStage46AcquisitionSurfaceR3Test.kt',
)
for name in files:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count('versionCode = 5505') != 1 or s.count('versionName = \\"0.1.221\\"') != 1:
        raise SystemExit(f'{name}: inherited R3 version assertion not found exactly once')
    s = s.replace('versionCode = 5505', 'versionCode = 5506', 1)
    s = s.replace('versionName = \\"0.1.221\\"', 'versionName = \\"0.1.222\\"', 1)
    s = s.replace('version_is_stage46_r3_0_1_221_5505', 'version_is_stage46_r4_0_1_222_5506', 1)
    path.write_text(s, encoding='utf-8')
print('stage46_r4_test_compat=PASS inherited_stage46_files=3 version=0.1.222/5506')
