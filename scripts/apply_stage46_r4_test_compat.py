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

# Full-suite version contracts from Stage34/Stage36 are historical build assertions. The runtime
# contracts remain unchanged, but the materialized physical APK now legitimately carries the R4
# version. Keep those tests in the suite while updating only their expected build identity.
legacy_versions = (
    ('FarolStage34Test.kt', 'versionCode = 5492', 'versionName = \\"0.1.208\\"'),
    ('FarolStage36RuntimeTest.kt', 'versionCode = 5493', 'versionName = \\"0.1.209\\"'),
)
for name, old_code, old_name in legacy_versions:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    if s.count(old_code) != 1 or s.count(old_name) != 1:
        raise SystemExit(f'{name}: legacy version assertion not found exactly once')
    s = s.replace(old_code, 'versionCode = 5506', 1)
    s = s.replace(old_name, 'versionName = \\"0.1.222\\"', 1)
    path.write_text(s, encoding='utf-8')

print('stage46_r4_test_compat=PASS inherited_stage46_files=3 legacy_version_files=2 version=0.1.222/5506')
