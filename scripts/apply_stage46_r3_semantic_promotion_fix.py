#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
SERVICE = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
s = SERVICE.read_text(encoding='utf-8')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

# Keep bounded, epoch-scoped provenance for OCR surfaces. This is acquisition metadata only;
# it never grants final authority and is consumed only after Stage21 semantic acceptance.
s = once(
    s,
    '    private var stage46TargetWindowId: Int = 0\n',
    '    private var stage46TargetWindowId: Int = 0\n'
    '    private val stage46AcquisitionSurfaceByWindowId = LinkedHashMap<Int, Pair<Long, String>>()\n',
    'R3 acquisition provenance map field',
)

# Accessibility candidates must not move confirmed target before Stage21 validates them.
pre_accessibility = '''        if (evaluationStage19 != null) {
            bindCandidateTargetSurfaceStage46(eventPackageStage19, eventWindowIdStage20, "accessibility")
        }

'''
if s.count(pre_accessibility) != 1:
    raise SystemExit(f'R2 accessibility pre-semantic bind expected 1, got {s.count(pre_accessibility)}')
s = s.replace(pre_accessibility, '', 1)

# OCR order-fix made the result fresh before binding, but R3 requires semantic acceptance too.
pre_ocr = '''                                    // Stage46 R2: only a fresh OCR result may move target ownership.
                                    bindCandidateTargetSurfaceStage46(eventPackageStage19, visualWindowIdStage19, "ocr")
'''
if s.count(pre_ocr) != 1:
    raise SystemExit(f'R2 OCR pre-semantic bind expected 1, got {s.count(pre_ocr)}')
s = s.replace(pre_ocr, '', 1)

# Tie every OCR-generated evaluation to the concrete acquisition window captured at request time,
# not to the legacy active/root window id. Both normal OCR clusters and bounded recovery use it.
ocr_start = s.index('    private fun requestUniversalScreenshotStage19(')
ocr_end = s.index('    private suspend fun processUniversalVisualStage19(', ocr_start)
ocr = s[ocr_start:ocr_end]
legacy_window_count = ocr.count('windowId = visualWindowIdStage19,')
if legacy_window_count < 2:
    raise SystemExit(f'R3 expected >=2 OCR legacy window bindings, got {legacy_window_count}')
ocr = ocr.replace('windowId = visualWindowIdStage19,', 'windowId = surfaceTokenStage46.windowId,')
s = s[:ocr_start] + ocr + s[ocr_end:]

# Remember the captured acquisition package by window+epoch so semantic promotion can resolve a
# popup even if currentRootPackageName() is Launcher at the later process step.
capture_anchor = '''        val surfaceTokenStage46 = FarolVisualEpochNoResultStage46.captureSurface(
            targetPackageForOcrStage46, null, targetWindowForOcrStage46, stage46VisualEpoch,
        )
'''
capture_new = capture_anchor + '''        val acquisitionPackageStage46R3 = FarolAcquisitionSurfaceStage46R3.normalizePackage(surfaceTokenStage46.packageName)
        if (surfaceTokenStage46.windowId > 0 && acquisitionPackageStage46R3 != null) {
            if (stage46AcquisitionSurfaceByWindowId.size >= 16) {
                val firstStage46R3 = stage46AcquisitionSurfaceByWindowId.keys.firstOrNull()
                if (firstStage46R3 != null) stage46AcquisitionSurfaceByWindowId.remove(firstStage46R3)
            }
            stage46AcquisitionSurfaceByWindowId[surfaceTokenStage46.windowId] = stage46VisualEpoch to acquisitionPackageStage46R3
        }
'''
s = once(s, capture_anchor, capture_new, 'R3 remember OCR acquisition provenance')

# OCR freshness itself follows the acquisition surface. A background-listed old window is no longer
# enough; the source must still be current root or an active/focused overlay.
old_fresh = '''        val currentTargetWindowStage46 = observeTargetWindowIdStage46(surfaceStage46.packageName)
        val surfaceFreshStage46 = FarolTargetSurfaceStage46R2.surfaceFresh(
            surfaceStage46, currentRootPackageName(), currentTargetWindowStage46, stage46VisualEpoch,
        )
'''
new_fresh = '''        val acquisitionPresenceStage46R3 = observeTargetSurfaceStage46R3(surfaceStage46.packageName)
        val surfaceFreshStage46 = FarolAcquisitionSurfaceStage46R3.acquisitionSurfaceFresh(
            surfaceStage46, currentRootPackageName(), acquisitionPresenceStage46R3, stage46VisualEpoch,
        )
'''
# Two R2 surfaceFresh blocks exist overall: OCR and route. Replace only the one inside OCR helper.
fresh_fn_start = s.index('    private fun isStage46OcrWorkFresh(')
fresh_fn_end = s.index('    private fun requestUniversalScreenshotStage19(', fresh_fn_start)
fresh_fn = s[fresh_fn_start:fresh_fn_end]
if fresh_fn.count(old_fresh) != 1:
    raise SystemExit(f'R3 OCR R2 freshness block expected 1, got {fresh_fn.count(old_fresh)}')
fresh_fn = fresh_fn.replace(old_fresh, new_fresh, 1)
s = s[:fresh_fn_start] + fresh_fn + s[fresh_fn_end:]

# Resolve a semantically accepted candidate's package from the concrete window. First inspect live
# windows; then use current-epoch OCR acquisition provenance; finally use root only as a safe fallback.
helper_anchor = '    private fun observeTargetSurfaceStage46R3(targetPackageStage46: String?): FarolAcquisitionSurfaceStage46R3.SurfacePresence {\n'
helper = '''    private fun observePackageForWindowIdStage46R3(windowIdStage46R3: Int): String? {
        if (windowIdStage46R3 > 0) {
            runCatching { windows }.getOrNull().orEmpty().forEach { windowStage46R3 ->
                if (runCatching { windowStage46R3.id }.getOrDefault(0) == windowIdStage46R3) {
                    val packageStage46R3 = runCatching { windowStage46R3.root?.packageName?.toString() }.getOrNull()
                    val normalizedStage46R3 = FarolAcquisitionSurfaceStage46R3.normalizePackage(packageStage46R3)
                    if (normalizedStage46R3 != null && !FarolAcquisitionSurfaceStage46R3.isSystemOrOwn(normalizedStage46R3, packageName)) {
                        return normalizedStage46R3
                    }
                }
            }
            val rememberedStage46R3 = stage46AcquisitionSurfaceByWindowId[windowIdStage46R3]
            if (rememberedStage46R3 != null && rememberedStage46R3.first == stage46VisualEpoch) {
                return rememberedStage46R3.second
            }
        }
        return FarolAcquisitionSurfaceStage46R3.normalizePackage(currentRootPackageName())
            ?.takeUnless { FarolAcquisitionSurfaceStage46R3.isSystemOrOwn(it, packageName) }
    }

'''
s = once(s, helper_anchor, helper + helper_anchor, 'R3 candidate package resolver')

# Stage21's semantic reject branch returns before this anchor. Therefore promotion here is impossible
# for an unvalidated candidate and happens before cache/Google/binding work proceeds.
semantic_anchor = '        val previousBindingStage20 = currentStage20BindingSnapshot()\n'
semantic_new = '''        val candidatePackageStage46R3 = observePackageForWindowIdStage46R3(evaluationStage19.windowId)
        bindCandidateTargetSurfaceStage46(
            candidatePackageStage46R3,
            evaluationStage19.windowId,
            "stage21_semantic_accepted",
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_TARGET_PROMOTED_AFTER_STAGE21", candidatePackageStage46R3, cycleId = cycleIdStage20,
            details = "window=${evaluationStage19.windowId}; signature=${evaluationStage19.addressSignature}; source=$sourceStage19; epoch=$stage46VisualEpoch",
        )
''' + semantic_anchor
s = once(s, semantic_anchor, semantic_new, 'R3 semantic promotion after Stage21')

# Releasing final ownership also clears acquisition provenance, so old window IDs cannot be reused
# across a later card/surface epoch.
release_anchor = '''        stage46TargetSourcePackage = null
        stage46TargetWindowId = 0
        stage46LastHardBoundaryGeneration = Long.MIN_VALUE
'''
release_new = '''        stage46TargetSourcePackage = null
        stage46TargetWindowId = 0
        stage46LastHardBoundaryGeneration = Long.MIN_VALUE
        stage46AcquisitionSurfaceByWindowId.clear()
'''
s = once(s, release_anchor, release_new, 'R3 release acquisition provenance')

SERVICE.write_text(s, encoding='utf-8')
print(f'stage46_r3_semantic_promotion=PASS ocr_window_bindings={legacy_window_count} promotion_after_stage21=true acquisition_freshness=true')
