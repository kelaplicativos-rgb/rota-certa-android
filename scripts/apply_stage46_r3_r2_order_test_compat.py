#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TEST = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46TargetSurfaceR2Test.kt'
s = TEST.read_text(encoding='utf-8')

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
    raise SystemExit(f'R2 stale OCR test compat expected 1 old contract, got {count}')
TEST.write_text(s.replace(old, new, 1), encoding='utf-8')
print('stage46_r3_r2_order_test_compat=PASS stale_before_semantic=true promotion_after_stage21=true')
