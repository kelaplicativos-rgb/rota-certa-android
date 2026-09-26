#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolStableFinalLatchStage46R4.kt'

if not HELPER.exists():
    raise SystemExit('missing Stage46 R4 helper')
(PKG / HELPER.name).write_text(HELPER.read_text(encoding='utf-8'), encoding='utf-8')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


s = SERVICE.read_text(encoding='utf-8')

# Stage46 R4: Stage44's generic "ambiguous means revoke" is too destructive once a final
# Green/Red exists. Physical 0.1.221 proof: a com.android.systemui event changed a valid
# inDrive Red into Default/Yellow while the confirmed target remained the active root.
# Preserve the final on foreign churn; when the confirmed target itself mutates, verify via
# the already-existing event-driven OCR path WITHOUT changing the public color first.
old = '''        // Only now is a different/ambiguous visual state proven. Revoke the old final and enter Yellow
        // before processing the new candidate or demanding OCR. This prevents duplicate events from
        // producing Red->Yellow flicker while retaining fail-closed behavior for genuine card changes.
        invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_PROVEN_CARD_CHANGE_INVALIDATE", eventPackageStage19, cycleId = cycleIdStage20,
            details = "candidate=${evaluationStage19 != null}; oldFinal=${finalLeaseStage44.activeFinal}; oldSignature=${finalLeaseStage44.addressSignature.orEmpty()}; newSignature=${evaluationStage19?.addressSignature.orEmpty()}; snapshotHash=${collectionStage26.snapshot.hash}; admissionGeneration=${admissionStage26.visualGeneration}",
        )

'''
new = '''        val stablePresenceStage46R4 = observeTargetSurfaceStage46R3(stage46TargetSourcePackage)
        val stableActionStage46R4 = FarolStableFinalLatchStage46R4.ambiguousAction(
            finalLeaseStage44.activeFinal,
            evaluationStage19 != null,
            stage46TargetSourcePackage,
            currentRootPackageName(),
            eventPackageStage19,
            packageName,
            stablePresenceStage46R4,
        )
        if (stableActionStage46R4 == FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_NO_VERIFY) {
            // Foreign/SystemUI/host churn has zero authority over a confirmed final that still owns
            // the visible surface. Keep the exact Green/Red+km physically unchanged and do no OCR.
            stage19VisualVerificationPending = false
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R4_FINAL_LATCH_PRESERVED_FOREIGN", eventPackageStage19, cycleId = cycleIdStage20,
                details = "color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=${stablePresenceStage46R4.windowId}; root=${currentRootPackageName().orEmpty()}; active=${stablePresenceStage46R4.active}; focused=${stablePresenceStage46R4.focused}; noYellow=true; noOcr=true",
            )
            FarolCausalLatencyStage28.Metrics.increment("stage46R4FinalLatchPreservedForeign")
            return true
        }

        val verifyWithoutBlinkStage46R4 = stableActionStage46R4 ==
            FarolStableFinalLatchStage46R4.AmbiguousAction.PRESERVE_AND_VERIFY
        if (verifyWithoutBlinkStage46R4) {
            // The concrete confirmed surface itself changed but Accessibility has not yet proved a
            // different card. Keep the final visible while OCR verifies the current frame. If OCR
            // proves a new two-address card, processUniversalVisualStage19 replaces it; if R2/R3
            // prove disappearance/handoff, those paths already clear immediately.
            stage19VisualVerificationPending = true
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R4_FINAL_LATCH_VERIFY_WITHOUT_BLINK", eventPackageStage19, cycleId = cycleIdStage20,
                details = "color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; target=${stage46TargetSourcePackage.orEmpty()}; targetWindow=${stablePresenceStage46R4.windowId}; root=${currentRootPackageName().orEmpty()}; noYellow=true; ocrMayVerify=true",
            )
            FarolCausalLatencyStage28.Metrics.increment("stage46R4FinalLatchVerifyWithoutBlink")
        } else {
            // A different candidate or a surface no longer owned by the confirmed target is real
            // proof. Clear immediately to Yellow/no-km before processing the replacement.
            invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_PROVEN_CARD_CHANGE_INVALIDATE", eventPackageStage19, cycleId = cycleIdStage20,
                details = "candidate=${evaluationStage19 != null}; oldFinal=${finalLeaseStage44.activeFinal}; oldSignature=${finalLeaseStage44.addressSignature.orEmpty()}; newSignature=${evaluationStage19?.addressSignature.orEmpty()}; snapshotHash=${collectionStage26.snapshot.hash}; admissionGeneration=${admissionStage26.visualGeneration}; stage46R4=true",
            )
        }

'''
s = once(s, old, new, 'R4 stable final latch before Stage44 invalidation')

SERVICE.write_text(s, encoding='utf-8')
print('stage46_stable_final_latch_r4=PASS foreign_churn_no_yellow=true same_surface_verify_without_blink=true proven_change_clears=true')
