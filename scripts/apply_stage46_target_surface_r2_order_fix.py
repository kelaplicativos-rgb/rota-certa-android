#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
SERVICE = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
s = SERVICE.read_text(encoding='utf-8')

pre = '''                                if (evaluationStage19 != null) {
                                    bindCandidateTargetSurfaceStage46(eventPackageStage19, visualWindowIdStage19, "ocr")
                                }

                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
'''
post = '''                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
'''
if s.count(pre) != 1:
    raise SystemExit(f'pre-fresh OCR bind anchor expected 1, got {s.count(pre)}')
s = s.replace(pre, post, 1)

anchor = '''                                stage19VisualVerificationPending = false
                                if (evaluationStage19 != null) {
'''
replacement = '''                                stage19VisualVerificationPending = false
                                if (evaluationStage19 != null) {
                                    // Stage46 R2: only a fresh OCR result may move target ownership.
                                    bindCandidateTargetSurfaceStage46(eventPackageStage19, visualWindowIdStage19, "ocr")
'''
if s.count(anchor) != 1:
    raise SystemExit(f'post-fresh OCR bind anchor expected 1, got {s.count(anchor)}')
s = s.replace(anchor, replacement, 1)

SERVICE.write_text(s, encoding='utf-8')
print('stage46_target_surface_r2_order=PASS stale_ocr_cannot_rebind_target=true')
