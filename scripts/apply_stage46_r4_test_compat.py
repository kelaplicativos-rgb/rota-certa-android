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

# Stage34/Stage36 runtime tests are progressively rebased by every later version patch. At the R4
# materialization point Stage45 has already moved both expectations to 0.1.218/5502. Update only
# those final build-identity assertions; their runtime/causal assertions remain untouched.
legacy_versions = (
    ('FarolStage34Test.kt', 'versionCode = 5502', 'versionName = \\"0.1.218\\"'),
    ('FarolStage36RuntimeTest.kt', 'versionCode = 5502', 'versionName = \\"0.1.218\\"'),
)
for name, old_code, old_name in legacy_versions:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count(old_code) != 1 or s.count(old_name) != 1:
        raise SystemExit(f'{name}: Stage45-materialized version assertion not found exactly once; code={s.count(old_code)} name={s.count(old_name)}')
    s = s.replace(old_code, 'versionCode = 5506', 1)
    s = s.replace(old_name, 'versionName = \\"0.1.222\\"', 1)
    path.write_text(s, encoding='utf-8')

print('stage46_r4_test_compat=PASS inherited_stage46_files=3 stage45_materialized_legacy_files=2 version=0.1.222/5506')
