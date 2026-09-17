#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolTargetSurfaceStage46R2.kt'

if not HELPER.exists():
    raise SystemExit('missing Stage46 R2 helper')
(PKG / HELPER.name).write_text(HELPER.read_text(encoding='utf-8'), encoding='utf-8')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


s = SERVICE.read_text(encoding='utf-8')

s = once(
    s,
    '    private var stage46LastHardBoundaryGeneration = Long.MIN_VALUE\n',
    '    private var stage46LastHardBoundaryGeneration = Long.MIN_VALUE\n'
    '    private var stage46TargetSourcePackage: String? = null\n'
    '    private var stage46TargetWindowId: Int = 0\n',
    'Stage46 R2 target fields',
)

old_boundary = '''        val hardBoundaryStage46 = FarolVisualEpochNoResultStage46.isHardWindowBoundary(
            eventTypeStage20,
            cheapSignalStage26.structuralSignature,
            cheapSignalStage26.ownOverlay,
            admissionStage26.heavyCollect,
        )
        if (hardBoundaryStage46 && admissionStage26.visualGeneration != stage46LastHardBoundaryGeneration) {
            advanceHardVisualEpochStage46(
                admissionStage26.visualGeneration,
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                cheapSignalStage26.structuralSignature,
            )
        }
'''
new_boundary = '''        // Stage46 R2: WINDOWS_CHANGED is only a trigger to re-observe the concrete target window.
        // A foreign overlay (e.g. inDrive while a 99 card is still visible) cannot revoke that target.
        val previousTargetWindowStage46 = stage46TargetWindowId
        val observedTargetWindowStage46 = observeTargetWindowIdStage46(stage46TargetSourcePackage)
        val targetReplacementStage46 = FarolTargetSurfaceStage46R2.isTargetWindowReplacement(
            eventTypeStage20,
            cheapSignalStage26.structuralSignature,
            cheapSignalStage26.ownOverlay,
            admissionStage26.heavyCollect,
            stage46TargetSourcePackage,
            previousTargetWindowStage46,
            observedTargetWindowStage46,
        )
        if (targetReplacementStage46 && admissionStage26.visualGeneration != stage46LastHardBoundaryGeneration) {
            advanceHardVisualEpochStage46(
                admissionStage26.visualGeneration,
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                cheapSignalStage26.structuralSignature,
            )
        } else if (observedTargetWindowStage46 > 0 && stage46TargetSourcePackage != null) {
            stage46TargetWindowId = observedTargetWindowStage46
            if (eventTypeStage20 == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R2_FOREIGN_WINDOW_PRESERVED", eventPackageStage19,
                    details = "target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=$stage46TargetWindowId; eventWindow=$eventWindowIdStage20; structural=${cheapSignalStage26.structuralSignature.take(300)}; epoch=$stage46VisualEpoch",
                )
            }
        }
'''
s = once(s, old_boundary, new_boundary, 'replace raw hard window authority with target-window observation')

# Capture OCR work against the already-owned target when one exists. A foreign event cannot steal it.
old_capture = '''        val surfaceTokenStage46 = FarolVisualEpochNoResultStage46.captureSurface(
            currentRootPackageName(), eventPackageStage19, visualWindowIdStage19, stage46VisualEpoch,
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_OCR_SURFACE_CAPTURED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
            details = "surfacePackage=${surfaceTokenStage46.packageName.orEmpty()}; surfaceWindow=${surfaceTokenStage46.windowId}; visualEpoch=${surfaceTokenStage46.visualEpoch}; root=${currentRootPackageName().orEmpty()}",
        )
'''
new_capture = '''        val targetPackageForOcrStage46 = FarolTargetSurfaceStage46R2.chooseTargetPackage(
            stage46TargetSourcePackage, currentRootPackageName(), eventPackageStage19, packageName,
        )
        val observedTargetWindowForOcrStage46 = observeTargetWindowIdStage46(targetPackageForOcrStage46)
        val targetWindowForOcrStage46 = observedTargetWindowForOcrStage46.takeIf { it > 0 } ?: visualWindowIdStage19
        val surfaceTokenStage46 = FarolVisualEpochNoResultStage46.captureSurface(
            targetPackageForOcrStage46, null, targetWindowForOcrStage46, stage46VisualEpoch,
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_OCR_SURFACE_CAPTURED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
            details = "surfacePackage=${surfaceTokenStage46.packageName.orEmpty()}; surfaceWindow=${surfaceTokenStage46.windowId}; visualEpoch=${surfaceTokenStage46.visualEpoch}; root=${currentRootPackageName().orEmpty()}; ownedTarget=${stage46TargetSourcePackage.orEmpty()}",
        )
'''
s = once(s, old_capture, new_capture, 'Stage46 R2 OCR target-surface capture')

# Stage44 raw-duplicate preservation must not win over proven disappearance of the CURRENT target.
raw_duplicate_anchor = '''        if (!visualDecisionStage23.process) {
            // Stage44: exact raw duplicate proves that the structural event did not change this visual frame.
'''
target_empty = '''        val targetEmptyProofStage46 = FarolTargetSurfaceStage46R2.provesCurrentTargetEmpty(
            eventTypeStage20,
            eventPackageStage19,
            currentRootPackageName(),
            stage46TargetSourcePackage,
            packageName,
            cheapSignalStage26.ownOverlay,
            finalLeaseStage44.activeFinal,
            collectionStage26.blocks.size,
        )
        if (targetEmptyProofStage46) {
            revokeEmptyTargetStage46(
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                collectionStage26.snapshot.hash,
            )
            return true
        }

'''
s = once(s, raw_duplicate_anchor, target_empty + raw_duplicate_anchor, 'target-empty proof before Stage44 raw duplicate')

# Candidate confirmation, not an arbitrary event, owns/moves the target surface.
accessibility_candidate_anchor = '''        if (evaluationStage19 != null &&
            FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(finalLeaseStage44, evaluationStage19.addressSignature)
        ) {
'''
accessibility_candidate = '''        if (evaluationStage19 != null) {
            bindCandidateTargetSurfaceStage46(eventPackageStage19, eventWindowIdStage20, "accessibility")
        }

'''
s = once(s, accessibility_candidate_anchor, accessibility_candidate + accessibility_candidate_anchor, 'bind accessibility candidate target')

ocr_fresh_anchor = '''                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleAfterEvaluate")
'''
ocr_candidate = '''                                if (evaluationStage19 != null) {
                                    bindCandidateTargetSurfaceStage46(eventPackageStage19, visualWindowIdStage19, "ocr")
                                }

'''
s = once(s, ocr_fresh_anchor, ocr_candidate + ocr_fresh_anchor, 'bind OCR candidate target')

# Replace Stage46 R1 package+epoch freshness with concrete target-window freshness.
old_ocr_fresh = '''        val surfaceFreshStage46 = FarolVisualEpochNoResultStage46.surfaceFresh(
            surfaceStage46, currentRootPackageName(), stage46VisualEpoch,
        )
'''
new_ocr_fresh = '''        val currentTargetWindowStage46 = observeTargetWindowIdStage46(surfaceStage46.packageName)
        val surfaceFreshStage46 = FarolTargetSurfaceStage46R2.surfaceFresh(
            surfaceStage46, currentRootPackageName(), currentTargetWindowStage46, stage46VisualEpoch,
        )
'''
if s.count(old_ocr_fresh) != 2:
    raise SystemExit(f'Stage46 R2 expected 2 R1 surfaceFresh calls, got {s.count(old_ocr_fresh)}')
s = s.replace(old_ocr_fresh, new_ocr_fresh)

# Route/final tokens are bound to the candidate-owned target surface, not the root package.
old_route_surface = '''        stage46BindingSurfaceToken[keyStage46] = FarolVisualEpochNoResultStage46.captureSurface(
            currentRootPackageName(), null, stage19ActiveWindowId ?: 0, stage46VisualEpoch,
        )
'''
new_route_surface = '''        val routeTargetPackageStage46 = stage46TargetSourcePackage ?: currentRootPackageName()
        val routeTargetWindowStage46 = observeTargetWindowIdStage46(routeTargetPackageStage46).takeIf { it > 0 }
            ?: stage46TargetWindowId.takeIf { it > 0 }
            ?: stage19ActiveWindowId
            ?: 0
        stage46BindingSurfaceToken[keyStage46] = FarolVisualEpochNoResultStage46.captureSurface(
            routeTargetPackageStage46, null, routeTargetWindowStage46, stage46VisualEpoch,
        )
'''
s = once(s, old_route_surface, new_route_surface, 'route/final target-surface binding')

# Insert target-window helpers immediately before the existing Stage46 hard-boundary helper.
helper_anchor = '    private fun advanceHardVisualEpochStage46(\n'
helpers = '''    private fun observeTargetWindowIdStage46(targetPackageStage46: String?): Int {
        val expectedStage46 = FarolTargetSurfaceStage46R2.normalizePackage(targetPackageStage46) ?: return 0
        val observedStage46 = runCatching { windows }.getOrNull().orEmpty()
        observedStage46.forEach { windowStage46 ->
            val windowPackageStage46 = runCatching { windowStage46.root?.packageName?.toString() }.getOrNull()
            if (FarolTargetSurfaceStage46R2.normalizePackage(windowPackageStage46) == expectedStage46) {
                val idStage46 = runCatching { windowStage46.id }.getOrDefault(0)
                if (idStage46 > 0) return idStage46
            }
        }
        val rootStage46 = runCatching { rootInActiveWindow }.getOrNull()
        val rootPackageStage46 = FarolTargetSurfaceStage46R2.normalizePackage(rootStage46?.packageName?.toString())
        return if (rootPackageStage46 == expectedStage46) runCatching { rootStage46?.windowId ?: 0 }.getOrDefault(0) else 0
    }

    private fun bindCandidateTargetSurfaceStage46(candidatePackageStage46: String?, candidateWindowStage46: Int, sourceStage46: String) {
        val targetPackageStage46 = FarolTargetSurfaceStage46R2.chooseCandidateTargetPackage(
            currentRootPackageName(), candidatePackageStage46, packageName,
        ) ?: return
        val observedWindowStage46 = observeTargetWindowIdStage46(targetPackageStage46)
        val resolvedWindowStage46 = observedWindowStage46.takeIf { it > 0 }
            ?: candidateWindowStage46.takeIf { it > 0 }
            ?: runCatching { rootInActiveWindow?.windowId ?: 0 }.getOrDefault(0)
        val changedStage46 = targetPackageStage46 != stage46TargetSourcePackage ||
            (resolvedWindowStage46 > 0 && stage46TargetWindowId > 0 && resolvedWindowStage46 != stage46TargetWindowId)
        stage46TargetSourcePackage = targetPackageStage46
        if (resolvedWindowStage46 > 0) stage46TargetWindowId = resolvedWindowStage46
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R2_TARGET_BOUND", candidatePackageStage46,
            details = "source=$sourceStage46; target=$targetPackageStage46; targetWindow=$stage46TargetWindowId; candidateWindow=$candidateWindowStage46; root=${currentRootPackageName().orEmpty()}; changed=$changedStage46; epoch=$stage46VisualEpoch",
        )
    }

    private fun revokeEmptyTargetStage46(
        eventStartedNsStage46: Long,
        eventPackageStage46: String?,
        eventWindowStage46: Int,
        snapshotHashStage46: Long,
    ) {
        val previousEpochStage46 = stage46VisualEpoch
        stage46VisualEpoch += 1L
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease("stage46_r2_target_empty")
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        universalRouteJob?.cancel(); universalRouteJob = null
        stage19OcrSerial += 1L
        stage19OcrRerunRequested = false
        stage36BindingWorkToken.clear()
        stage46BindingSurfaceToken.clear()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalActiveAddressSignature = null
        lastAnalyzedHash = null
        currentDistanceKm = null
        stage19VisualVerificationPending = true
        showOverlay(RadarColor.Default, distanceKm = null)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_TARGET_EMPTY_FINAL_REVOKED", eventPackageStage46,
            details = "fromEpoch=$previousEpochStage46; toEpoch=$stage46VisualEpoch; target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=$stage46TargetWindowId; eventWindow=$eventWindowStage46; snapshotHash=$snapshotHashStage46; oldWorkCancelled=true; yellowCommitted=true",
        )
        FarolCausalLatencyStage28.Metrics.increment("stage46TargetEmptyFinalRevoked")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToStage46TargetEmptyRevoked",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage46,
        )
    }

'''
s = once(s, helper_anchor, helpers + helper_anchor, 'Stage46 R2 service helpers')

SERVICE.write_text(s, encoding='utf-8')
print('stage46_target_surface_r2=PASS raw_event_authority_removed=true target_window_freshness=true target_empty_revokes=true')
