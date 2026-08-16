#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'

stage46_files = (
    'FarolStage46VisualEpochNoResultTest.kt',
    'FarolStage46TargetSurfaceR2Test.kt',
    'FarolStage46AcquisitionSurfaceR3Test.kt',
    'FarolStage46StableFinalLatchR4Test.kt',
)
for name in stage46_files:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count('versionCode = 5506') != 1 or s.count('versionName = \\"0.1.222\\"') != 1:
        raise SystemExit(f'{name}: inherited R4 version assertion not found exactly once')
    s = s.replace('versionCode = 5506', 'versionCode = 5507', 1)
    s = s.replace('versionName = \\"0.1.222\\"', 'versionName = \\"0.1.223\\"', 1)
    s = s.replace('version_is_stage46_r4_0_1_222_5506', 'version_is_stage46_r5_0_1_223_5507', 1)
    path.write_text(s, encoding='utf-8')

legacy_exact = (
    (
        'FarolStage34Test.kt',
        'assertTrue(s.contains("versionCode = 5506")); assertTrue(s.contains("versionName = \\"0.1.222\\""))',
        'assertTrue(s.contains("versionCode = 5507")); assertTrue(s.contains("versionName = \\"0.1.223\\""))',
    ),
    (
        'FarolStage36RuntimeTest.kt',
        'assertTrue(b.contains("versionCode = 5506"));assertTrue(b.contains("versionName = \\"0.1.222\\""))',
        'assertTrue(b.contains("versionCode = 5507"));assertTrue(b.contains("versionName = \\"0.1.223\\""))',
    ),
)
for name, old, new in legacy_exact:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count(old) != 1:
        raise SystemExit(f'{name}: exact R4 inherited version assertion not found exactly once; count={s.count(old)}')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

print('stage46_r5_test_compat=PASS inherited_stage46_files=4 r4_materialized_legacy_files=2 version=0.1.223/5507')
