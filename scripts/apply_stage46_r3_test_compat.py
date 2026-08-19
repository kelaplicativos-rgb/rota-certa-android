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

# R2 allowed a fresh OCR result to move target ownership immediately after the stale check.
# R3 deliberately strengthens that contract: freshness only allows the candidate to continue;
# the target is promoted later, and only after Stage21 semantic acceptance.
r2 = TESTS / 'FarolStage46TargetSurfaceR2Test.kt'
s = r2.read_text(encoding='utf-8')
old = '''    @Test fun stale_ocr_is_checked_before_ocr_candidate_can_move_target() {
        val s = source("LiveRideAccessibilityService.kt")
        val staleCheck = s.indexOf("if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46))")
        val postFresh = s.indexOf("stage19VisualVerificationPending = false", staleCheck)
        val bind = s.indexOf("bindCandidateTargetSurfaceStage46(eventPackageStage19, visualWindowIdStage19, \\"ocr\\")", postFresh)
        assertTrue(staleCheck >= 0 && postFresh > staleCheck && bind > postFresh)
    }
'''
new = '''    @Test fun stale_ocr_is_checked_before_semantic_candidate_can_move_target() {
        val s = source("LiveRideAccessibilityService.kt")
        val staleCheck = s.indexOf("if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46))")
        val postFresh = s.indexOf("stage19VisualVerificationPending = false", staleCheck)
        val processStart = s.indexOf("private suspend fun processUniversalVisualStage19(")
        val processEnd = s.indexOf("private fun stage20BindingSnapshot(", processStart)
        val process = s.substring(processStart, processEnd)
        val semanticReject = process.indexOf("if (!semanticStage21.accepted)")
        val promotion = process.indexOf("S46_R3_TARGET_PROMOTED_AFTER_STAGE21")
        assertTrue(staleCheck >= 0 && postFresh > staleCheck)
        assertFalse(s.substring(staleCheck, processStart).contains("bindCandidateTargetSurfaceStage46(eventPackageStage19, visualWindowIdStage19, \\"ocr\\")"))
        assertTrue(semanticReject >= 0 && promotion > semanticReject)
        assertFalse(process.substring(0, semanticReject).contains("bindCandidateTargetSurfaceStage46("))
    }
'''
count = s.count(old)
if count != 1:
    raise SystemExit(f'R3 inherited R2 stale OCR contract expected 1 occurrence, got {count}')
r2.write_text(s.replace(old, new, 1), encoding='utf-8')

print('stage46_r3_test_compat=PASS inherited_r1_r2_version_assertions=2 stale_before_semantic=true promotion_after_stage21=true')
