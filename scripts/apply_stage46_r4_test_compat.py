#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'

# Stage46 R1/R2/R3 assertions inherit the immediately previous Stage46 version.
stage46_files = (
    'FarolStage46VisualEpochNoResultTest.kt',
    'FarolStage46TargetSurfaceR2Test.kt',
    'FarolStage46AcquisitionSurfaceR3Test.kt',
)
for name in stage46_files:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count('versionCode = 5505') != 1 or s.count('versionName = \\"0.1.221\\"') != 1:
        raise SystemExit(f'{name}: inherited R3 version assertion not found exactly once')
    s = s.replace('versionCode = 5505', 'versionCode = 5506', 1)
    s = s.replace('versionName = \\"0.1.221\\"', 'versionName = \\"0.1.222\\"', 1)
    s = s.replace('version_is_stage46_r3_0_1_221_5505', 'version_is_stage46_r4_0_1_222_5506', 1)
    path.write_text(s, encoding='utf-8')

# Stage34/Stage36 build-identity assertions are progressively rebased by every later version patch.
# Stage46 R3 explicitly leaves both at 0.1.221/5505 immediately before R4 is materialized.
legacy_exact = (
    (
        'FarolStage34Test.kt',
        'assertTrue(s.contains("versionCode = 5505")); assertTrue(s.contains("versionName = \\"0.1.221\\""))',
        'assertTrue(s.contains("versionCode = 5506")); assertTrue(s.contains("versionName = \\"0.1.222\\""))',
    ),
    (
        'FarolStage36RuntimeTest.kt',
        'assertTrue(b.contains("versionCode = 5505"));assertTrue(b.contains("versionName = \\"0.1.221\\""))',
        'assertTrue(b.contains("versionCode = 5506"));assertTrue(b.contains("versionName = \\"0.1.222\\""))',
    ),
)
for name, old, new in legacy_exact:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count(old) != 1:
        raise SystemExit(f'{name}: exact R3 inherited version assertion not found exactly once; count={s.count(old)}')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

print('stage46_r4_test_compat=PASS inherited_stage46_files=3 r3_materialized_legacy_files=2 version=0.1.222/5506')
