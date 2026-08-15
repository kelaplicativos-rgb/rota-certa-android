#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TESTS = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta'

for name in ('FarolStage46VisualEpochNoResultTest.kt', 'FarolStage46TargetSurfaceR2Test.kt'):
    path = TESTS / name
    s = path.read_text(encoding='utf-8')
    count_code = s.count('versionCode = 5504')
    count_name = s.count('versionName = \\"0.1.220\\"')
    if count_code != 1 or count_name != 1:
        raise SystemExit(f'{name}: expected one inherited R2 version assertion, got code={count_code} name={count_name}')
    s = s.replace('versionCode = 5504', 'versionCode = 5505', 1)
    s = s.replace('versionName = \\"0.1.220\\"', 'versionName = \\"0.1.221\\"', 1)
    s = s.replace('version_is_stage46_r2_0_1_220_5504', 'version_is_stage46_r3_0_1_221_5505', 1)
    path.write_text(s, encoding='utf-8')

print('stage46_r3_test_compat=PASS inherited_r1_r2_version_assertions=2')
