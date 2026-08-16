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
    'FarolStage46AtomicTransitionR5Test.kt',
)
for name in stage46_files:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    code_count = s.count('versionCode = 5507')
    name_count = s.count('versionName = \\"0.1.223\\"')
    if code_count != 1 or name_count != 1:
        raise SystemExit(f'{name}: inherited R5 version assertion not found exactly once; code={code_count} name={name_count}')
    s = s.replace('versionCode = 5507', 'versionCode = 5508', 1)
    s = s.replace('versionName = \\"0.1.223\\"', 'versionName = \\"0.1.224\\"', 1)
    s = s.replace('version_is_stage46_r5_0_1_223_5507', 'version_is_stage46_r6_0_1_224_5508', 1)
    path.write_text(s, encoding='utf-8')

legacy_exact = (
    (
        'FarolStage34Test.kt',
        'assertTrue(s.contains("versionCode = 5507")); assertTrue(s.contains("versionName = \\"0.1.223\\""))',
        'assertTrue(s.contains("versionCode = 5508")); assertTrue(s.contains("versionName = \\"0.1.224\\""))',
    ),
    (
        'FarolStage36RuntimeTest.kt',
        'assertTrue(b.contains("versionCode = 5507"));assertTrue(b.contains("versionName = \\"0.1.223\\""))',
        'assertTrue(b.contains("versionCode = 5508"));assertTrue(b.contains("versionName = \\"0.1.224\\""))',
    ),
)
for name, old, new in legacy_exact:
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{name}: exact R5 inherited version assertion not found exactly once; count={count}')
    path.write_text(s.replace(old, new, 1), encoding='utf-8')

print('stage46_r6_test_compat=PASS inherited_stage46_files=5 r5_materialized_legacy_files=2 version=0.1.224/5508')
