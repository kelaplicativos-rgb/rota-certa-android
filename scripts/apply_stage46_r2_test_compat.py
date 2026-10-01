#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TEST = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46VisualEpochNoResultTest.kt'
s = TEST.read_text(encoding='utf-8')

old = '        assertTrue(s.contains("surfaceStage46, currentRootPackageName(), stage46VisualEpoch"))\n'
new = (
    '        assertTrue(s.contains("FarolTargetSurfaceStage46R2.surfaceFresh("))\n'
    '        assertTrue(s.contains("observeTargetWindowIdStage46(surfaceStage46.packageName)"))\n'
)
if s.count(old) != 1:
    raise SystemExit(f'R1 freshness assertion expected 1, got {s.count(old)}')
s = s.replace(old, new, 1)

old = '''    @Test fun version_is_stage46_0_1_219_5503() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5503"))
        assertTrue(b.contains("versionName = \\"0.1.219\\""))
    }
'''
new = '''    @Test fun version_is_stage46_r2_0_1_220_5504() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5504"))
        assertTrue(b.contains("versionName = \\"0.1.220\\""))
    }
'''
if s.count(old) != 1:
    raise SystemExit(f'R1 version assertion expected 1, got {s.count(old)}')
s = s.replace(old, new, 1)

TEST.write_text(s, encoding='utf-8')
print('stage46_r2_test_compat=PASS inherited_stage46_tests_updated=true')
