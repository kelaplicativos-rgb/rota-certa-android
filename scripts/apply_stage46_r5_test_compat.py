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

# The R5 source-contract test must search for the Kotlin interpolation text literally. The source
# template intentionally contains `${stage46TargetSourcePackage == null}` inside a runtime details
# string; leaving that sequence unescaped inside the TEST string makes the test compiler try to
# resolve a private service field. Replace only the assertion representation, not production code.
r5_test = TESTS / 'FarolStage46AtomicTransitionR5Test.kt'
s = r5_test.read_text(encoding='utf-8')
old_assert = '        assertTrue(block.contains("oldTargetReleased=${stage46TargetSourcePackage == null}"))\n'
new_assert = (
    '        assertTrue(block.contains("oldTargetReleased="))\n'
    '        assertTrue(block.contains("stage46TargetSourcePackage == null"))\n'
)
if s.count(old_assert) != 1:
    raise SystemExit(f'R5 literal source assertion expected exactly once, got {s.count(old_assert)}')
s = s.replace(old_assert, new_assert, 1)

# The busy flag is intentionally captured immediately BEFORE the REQUESTED forensic marker. The
# original test started its substring at that marker and therefore excluded the very assignment it
# wanted to verify. Start at the proven target-empty branch instead; keep all three assertions.
old_busy = '''    @Test fun busy_screenshot_is_coalesced_not_dropped() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("S46_R5_ATOMIC_CLEAR_REARM_REQUESTED")
        val b = s.indexOf("return true", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("screenshotAlreadyRunningStage46R5 = screenshotInProgress.get()"))
        assertTrue(block.contains("coalesced_rerun"))
        assertTrue(block.contains("immediate_screenshot"))
    }
'''
new_busy = '''    @Test fun busy_screenshot_is_coalesced_not_dropped() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("if (targetEmptyProofStage46)")
        val b = s.indexOf("return true", a)
        val block = s.substring(a, b)
        assertTrue(block.contains("screenshotAlreadyRunningStage46R5 = screenshotInProgress.get()"))
        assertTrue(block.contains("coalesced_rerun"))
        assertTrue(block.contains("immediate_screenshot"))
        assertTrue(block.contains("requestUniversalScreenshotStage19(eventPackageStage19)"))
    }
'''
if s.count(old_busy) != 1:
    raise SystemExit(f'R5 busy coalescer test window expected exactly once, got {s.count(old_busy)}')
s = s.replace(old_busy, new_busy, 1)
r5_test.write_text(s, encoding='utf-8')

print('stage46_r5_test_compat=PASS inherited_stage46_files=4 r4_materialized_legacy_files=2 literal_source_assertion_fixed=true busy_coalescer_window_fixed=true version=0.1.223/5507')
