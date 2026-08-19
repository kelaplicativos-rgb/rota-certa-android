#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolAcquisitionSurfaceStage46R3.kt'

if not HELPER.exists():
    raise SystemExit('missing Stage46 R3 helper')
(PKG / HELPER.name).write_text(HELPER.read_text(encoding='utf-8'), encoding='utf-8')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


def insert_before_function_end(text: str, signature: str, addition: str, label: str) -> str:
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f'{label}: function signature missing')
    candidates = [x for x in (
        text.find('\n    private fun ', start + len(signature)),
        text.find('\n    private suspend fun ', start + len(signature)),
        text.find('\n    override fun ', start + len(signature)),
    ) if x >= 0]
    end = min(candidates) if candidates else len(text)
    segment = text[start:end]
    close = segment.rfind('\n    }')
    if close < 0:
        raise SystemExit(f'{label}: function closing brace missing')
    absolute = start + close
    return text[:absolute] + '\n' + addition.rstrip('\n') + text[absolute:]


s = SERVICE.read_text(encoding='utf-8')

# -----------------------------------------------------------------------------
# R3 acquisition: never pin a new OCR request to a stale confirmed target.
# The R2 confirmed target remains authoritative for an actually interactive popup,
# but a background-listed old app (physical com.comuto -> com.app99.driver) cannot
# block acquisition from the current foreground root.
# -----------------------------------------------------------------------------
old_capture = '''        val targetPackageForOcrStage46 = FarolTargetSurfaceStage46R2.chooseTargetPackage(
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
new_capture = '''        val confirmedPresenceStage46R3 = observeTargetSurfaceStage46R3(stage46TargetSourcePackage)
        val acquisitionStage46R3 = FarolAcquisitionSurfaceStage46R3.chooseAcquisitionPackage(
            stage46TargetSourcePackage,
            currentRootPackageName(),
            eventPackageStage19,
            packageName,
            confirmedPresenceStage46R3,
        )
        val targetPackageForOcrStage46 = acquisitionStage46R3.packageName
        val observedTargetWindowForOcrStage46 = observeTargetWindowIdStage46(targetPackageForOcrStage46)
        val targetWindowForOcrStage46 = observedTargetWindowForOcrStage46.takeIf { it > 0 } ?: visualWindowIdStage19
        val surfaceTokenStage46 = FarolVisualEpochNoResultStage46.captureSurface(
            targetPackageForOcrStage46, null, targetWindowForOcrStage46, stage46VisualEpoch,
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_ACQUISITION_SURFACE_CAPTURED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
            details = "surfacePackage=${surfaceTokenStage46.packageName.orEmpty()}; surfaceWindow=${surfaceTokenStage46.windowId}; visualEpoch=${surfaceTokenStage46.visualEpoch}; root=${currentRootPackageName().orEmpty()}; confirmedTarget=${stage46TargetSourcePackage.orEmpty()}; confirmedWindow=${confirmedPresenceStage46R3.windowId}; confirmedActive=${confirmedPresenceStage46R3.active}; confirmedFocused=${confirmedPresenceStage46R3.focused}; reason=${acquisitionStage46R3.reason}",
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_OCR_SURFACE_CAPTURED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
            details = "surfacePackage=${surfaceTokenStage46.packageName.orEmpty()}; surfaceWindow=${surfaceTokenStage46.windowId}; visualEpoch=${surfaceTokenStage46.visualEpoch}; root=${currentRootPackageName().orEmpty()}; ownedTarget=${stage46TargetSourcePackage.orEmpty()}; acquisitionReason=${acquisitionStage46R3.reason}",
        )
'''
s = once(s, old_capture, new_capture, 'R3 acquisition surface capture')

# -----------------------------------------------------------------------------
# Observe whether the confirmed window is merely listed or is still actually
# active/focused. R2 window-id observation remains intact for inherited freshness.
# -----------------------------------------------------------------------------
observe_anchor = '    private fun observeTargetWindowIdStage46(targetPackageStage46: String?): Int {\n'
observe_helper = '''    private fun observeTargetSurfaceStage46R3(targetPackageStage46: String?): FarolAcquisitionSurfaceStage46R3.SurfacePresence {
        val expectedStage46 = FarolAcquisitionSurfaceStage46R3.normalizePackage(targetPackageStage46)
            ?: return FarolAcquisitionSurfaceStage46R3.SurfacePresence()
        val matchesStage46 = runCatching { windows }.getOrNull().orEmpty().mapNotNull { windowStage46 ->
            val windowPackageStage46 = runCatching { windowStage46.root?.packageName?.toString() }.getOrNull()
            if (FarolAcquisitionSurfaceStage46R3.normalizePackage(windowPackageStage46) != expectedStage46) {
                null
            } else {
                val idStage46 = runCatching { windowStage46.id }.getOrDefault(0)
                if (idStage46 <= 0) null else FarolAcquisitionSurfaceStage46R3.SurfacePresence(
                    windowId = idStage46,
                    active = runCatching { windowStage46.isActive }.getOrDefault(false),
                    focused = runCatching { windowStage46.isFocused }.getOrDefault(false),
                    layer = runCatching { windowStage46.layer }.getOrDefault(Int.MIN_VALUE),
                )
            }
        }
        val bestStage46 = matchesStage46.maxWithOrNull(
            compareBy<FarolAcquisitionSurfaceStage46R3.SurfacePresence> { if (it.active) 1 else 0 }
                .thenBy { if (it.focused) 1 else 0 }
                .thenBy { it.layer },
        )
        if (bestStage46 != null) return bestStage46

        val rootStage46 = runCatching { rootInActiveWindow }.getOrNull()
        val rootPackageStage46 = FarolAcquisitionSurfaceStage46R3.normalizePackage(rootStage46?.packageName?.toString())
        return if (rootPackageStage46 == expectedStage46) {
            FarolAcquisitionSurfaceStage46R3.SurfacePresence(
                windowId = runCatching { rootStage46?.windowId ?: 0 }.getOrDefault(0),
                active = true,
                focused = true,
                layer = Int.MAX_VALUE,
            )
        } else FarolAcquisitionSurfaceStage46R3.SurfacePresence()
    }

'''
s = once(s, observe_anchor, observe_helper + observe_anchor, 'R3 interactive target observation')

# -----------------------------------------------------------------------------
# Structural foreground handoff: if the old final target is no longer interactive
# and Android has moved to another real foreground application, yellow/no-km wins
# before Stage44 can lease the old final. Continue the same heavy cycle so the new
# surface can be acquired immediately.
# -----------------------------------------------------------------------------
lease_anchor = '        val finalLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(\n'
handoff = '''        val handoffLeaseStage46R3 = FarolSemanticFinalLeaseStage44.capture(
            currentRadarColor.name,
            currentDistanceKm,
            universalActiveAddressSignature,
        )
        val handoffPresenceStage46R3 = observeTargetSurfaceStage46R3(stage46TargetSourcePackage)
        if (FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
                eventTypeStage20,
                admissionStage26.heavyCollect,
                cheapSignalStage26.ownOverlay,
                handoffLeaseStage46R3.activeFinal,
                stage46TargetSourcePackage,
                currentRootPackageName(),
                packageName,
                handoffPresenceStage46R3,
            )
        ) {
            revokeForegroundSurfaceHandoffStage46R3(
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                admissionStage26.visualGeneration,
                currentRootPackageName(),
            )
        }

'''
s = once(s, lease_anchor, handoff + lease_anchor, 'R3 handoff before Stage44 lease')

# -----------------------------------------------------------------------------
# Shared confirmed-target release. A revoked final must never leave stale package /
# window ownership behind, which was the physical R2 deadlock seen on 99.
# -----------------------------------------------------------------------------
revoke_anchor = '    private fun revokeEmptyTargetStage46(\n'
release_helpers = '''    private fun releaseConfirmedTargetStage46R3(reasonStage46R3: String, eventPackageStage46R3: String?) {
        val oldPackageStage46R3 = stage46TargetSourcePackage
        val oldWindowStage46R3 = stage46TargetWindowId
        stage46TargetSourcePackage = null
        stage46TargetWindowId = 0
        stage46LastHardBoundaryGeneration = Long.MIN_VALUE
        if (oldPackageStage46R3 != null || oldWindowStage46R3 > 0) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_TARGET_RELEASED", eventPackageStage46R3,
                details = "reason=$reasonStage46R3; oldTarget=${oldPackageStage46R3.orEmpty()}; oldWindow=$oldWindowStage46R3; epoch=$stage46VisualEpoch",
            )
        }
    }

    private fun revokeForegroundSurfaceHandoffStage46R3(
        eventStartedNsStage46R3: Long,
        eventPackageStage46R3: String?,
        eventWindowStage46R3: Int,
        admissionGenerationStage46R3: Long,
        newRootStage46R3: String?,
    ) {
        val previousEpochStage46R3 = stage46VisualEpoch
        val oldTargetStage46R3 = stage46TargetSourcePackage
        val oldWindowStage46R3 = stage46TargetWindowId
        stage46VisualEpoch += 1L
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease("stage46_r3_foreground_handoff")
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
        releaseConfirmedTargetStage46R3("foreground_handoff", eventPackageStage46R3)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R3_FOREGROUND_HANDOFF_FINAL_REVOKED", eventPackageStage46R3,
            details = "fromEpoch=$previousEpochStage46R3; toEpoch=$stage46VisualEpoch; oldTarget=${oldTargetStage46R3.orEmpty()}; oldWindow=$oldWindowStage46R3; newRoot=${newRootStage46R3.orEmpty()}; eventWindow=$eventWindowStage46R3; admissionGeneration=$admissionGenerationStage46R3; yellowCommitted=true; acquisitionContinuesSameCycle=true",
        )
        FarolCausalLatencyStage28.Metrics.increment("stage46R3ForegroundHandoffRevoked")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToStage46R3ForegroundHandoffRevoked",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage46R3,
        )
    }

'''
s = once(s, revoke_anchor, release_helpers + revoke_anchor, 'R3 release helpers')

# Empty-target R2 revocation now releases confirmed ownership after preserving old
# target/window in the forensic record.
s = once(
    s,
    '''        val previousEpochStage46 = stage46VisualEpoch
        stage46VisualEpoch += 1L
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease("stage46_r2_target_empty")
''',
    '''        val previousEpochStage46 = stage46VisualEpoch
        val releasedTargetPackageStage46R3 = stage46TargetSourcePackage
        val releasedTargetWindowStage46R3 = stage46TargetWindowId
        stage46VisualEpoch += 1L
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease("stage46_r2_target_empty")
''',
    'R3 preserve old target for empty forensic record',
)
s = once(
    s,
    '''        showOverlay(RadarColor.Default, distanceKm = null)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_TARGET_EMPTY_FINAL_REVOKED", eventPackageStage46,
            details = "fromEpoch=$previousEpochStage46; toEpoch=$stage46VisualEpoch; target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=$stage46TargetWindowId; eventWindow=$eventWindowStage46; snapshotHash=$snapshotHashStage46; oldWorkCancelled=true; yellowCommitted=true",
        )
''',
    '''        showOverlay(RadarColor.Default, distanceKm = null)
        releaseConfirmedTargetStage46R3("target_empty", eventPackageStage46)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_TARGET_EMPTY_FINAL_REVOKED", eventPackageStage46,
            details = "fromEpoch=$previousEpochStage46; toEpoch=$stage46VisualEpoch; target=${releasedTargetPackageStage46R3.orEmpty()}; targetWindow=$releasedTargetWindowStage46R3; eventWindow=$eventWindowStage46; snapshotHash=$snapshotHashStage46; oldWorkCancelled=true; yellowCommitted=true; targetReleased=true",
        )
''',
    'R3 release after empty target',
)

# R1/R2 hard replacement also terminates confirmed ownership.
s = once(
    s,
    '''        stage19VisualVerificationPending = true
        showOverlay(RadarColor.Default, distanceKm = null)

        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_HARD_VISUAL_BOUNDARY", eventPackageStage46,
''',
    '''        stage19VisualVerificationPending = true
        showOverlay(RadarColor.Default, distanceKm = null)
        releaseConfirmedTargetStage46R3("hard_visual_boundary", eventPackageStage46)

        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_HARD_VISUAL_BOUNDARY", eventPackageStage46,
''',
    'R3 hard boundary releases target',
)

# Manual/functional OFF must not leave target ownership waiting for the next ON.
s = insert_before_function_end(
    s,
    '    private fun applyReadingOffStage26(',
    '        releaseConfirmedTargetStage46R3("reading_off", null)\n',
    'R3 manual off releases target',
)

SERVICE.write_text(s, encoding='utf-8')
print('stage46_acquisition_surface_r3=PASS acquisition_decoupled=true target_release=true foreground_handoff=true manual_off_release=true')
