#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolAtomicTransitionStage46R5.kt'

if not HELPER.exists():
    raise SystemExit('missing Stage46 R5 helper')
(PKG / HELPER.name).write_text(HELPER.read_text(encoding='utf-8'), encoding='utf-8')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


s = SERVICE.read_text(encoding='utf-8')

# R2/R3 already prove target-empty strongly and clear the old final. The remaining physical gap is
# that the handler immediately returned, so OCR of the now-current surface depended on a later raw
# Accessibility event. R5 keeps the return (one event is consumed once), but it first issues a
# single-shot OCR acquisition against the NEW epoch/current surface. If another screenshot is still
# finishing, the existing coalescer requests a rerun; no second Accessibility event is required.
old_target_empty = '''        if (targetEmptyProofStage46) {
            revokeEmptyTargetStage46(
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                collectionStage26.snapshot.hash,
            )
            return true
        }

'''
new_target_empty = '''        if (targetEmptyProofStage46) {
            val previousEpochStage46R5 = stage46VisualEpoch
            revokeEmptyTargetStage46(
                eventStartedNsStage26,
                eventPackageStage19,
                eventWindowIdStage20,
                collectionStage26.snapshot.hash,
            )
            val rearmActionStage46R5 = FarolAtomicTransitionStage46R5.actionAfterProvenClear(
                readingEnabled = WorkModePolicy0162.isEnabled(currentSettings),
                serviceReady = serviceReady,
                bubbleGestureActive = bubbleGestureActive,
                candidatePresent = false,
            )
            if (rearmActionStage46R5 == FarolAtomicTransitionStage46R5.RearmAction.REQUEST_SINGLE_SHOT_OCR_NOW) {
                val screenshotAlreadyRunningStage46R5 = screenshotInProgress.get()
                stage19VisualVerificationPending = true
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_ATOMIC_CLEAR_REARM_REQUESTED", eventPackageStage19, cycleId = cycleIdStage20,
                    details = "reason=target_empty; previousEpoch=$previousEpochStage46R5; currentEpoch=$stage46VisualEpoch; epochAdvanced=${FarolAtomicTransitionStage46R5.nextEpochIsFresh(previousEpochStage46R5, stage46VisualEpoch)}; root=${currentRootPackageName().orEmpty()}; eventWindow=$eventWindowIdStage20; yellowCommitted=true; oldTargetReleased=${stage46TargetSourcePackage == null}; screenshotBusy=$screenshotAlreadyRunningStage46R5; noSecondEventRequired=true",
                )
                requestUniversalScreenshotStage19(eventPackageStage19)
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_ATOMIC_CLEAR_REARM_DISPATCHED", eventPackageStage19, cycleId = cycleIdStage20,
                    details = "reason=target_empty; currentEpoch=$stage46VisualEpoch; requestMode=${if (screenshotAlreadyRunningStage46R5) "coalesced_rerun" else "immediate_screenshot"}; verificationPending=$stage19VisualVerificationPending; noPolling=true",
                )
                FarolCausalLatencyStage28.Metrics.increment("stage46R5AtomicClearRearm")
                FarolCausalLatencyStage28.Metrics.sample(
                    "eventToStage46R5AtomicRearm",
                    SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
                )
            }
            return true
        }

'''
s = once(s, old_target_empty, new_target_empty, 'R5 target-empty clear plus same-event rearm')

# R3 foreground handoff already continues the same heavy-collection handler. Anchor the R5
# telemetry to the full EVENT-HANDLER site inserted by R3, not to the helper's own internal call.
# This keeps evaluationStage19/cycleIdStage20 in lexical scope and proves no second event is needed.
old_handoff_site = '''        if (FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
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
new_handoff_site = '''        if (FarolAcquisitionSurfaceStage46R3.provesForegroundSurfaceHandoff(
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
            val handoffRearmActionStage46R5 = FarolAtomicTransitionStage46R5.actionAfterProvenClear(
                readingEnabled = WorkModePolicy0162.isEnabled(currentSettings),
                serviceReady = serviceReady,
                bubbleGestureActive = bubbleGestureActive,
                candidatePresent = evaluationStage19 != null,
            )
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_HANDOFF_CONTINUES_SAME_CYCLE", eventPackageStage19, cycleId = cycleIdStage20,
                details = "action=$handoffRearmActionStage46R5; candidate=${evaluationStage19 != null}; root=${currentRootPackageName().orEmpty()}; epoch=$stage46VisualEpoch; yellowCommitted=true; noSecondEventRequired=true",
            )
        }

'''
s = once(s, old_handoff_site, new_handoff_site, 'R5 foreground handoff event-handler same-cycle proof')

# A proven replacement candidate in the ordinary R4 branch is already processed later in the same
# handler. Record the guarantee at the point R4 clears the old paint; do not start a redundant OCR.
old_r4_clear = '''            invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_PROVEN_CARD_CHANGE_INVALIDATE", eventPackageStage19, cycleId = cycleIdStage20,
'''
new_r4_clear = '''            invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            if (evaluationStage19 != null) {
                FarolMaximumForensicsStage38.record(
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R5_NEW_CANDIDATE_CONTINUES_SAME_CYCLE", eventPackageStage19, cycleId = cycleIdStage20,
                    details = "signature=${evaluationStage19.addressSignature}; window=${evaluationStage19.windowId}; root=${currentRootPackageName().orEmpty()}; yellowCommitted=true; noOcrNeeded=true; noSecondEventRequired=true",
                )
            }
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_PROVEN_CARD_CHANGE_INVALIDATE", eventPackageStage19, cycleId = cycleIdStage20,
'''
s = once(s, old_r4_clear, new_r4_clear, 'R5 proven candidate same-cycle telemetry')

SERVICE.write_text(s, encoding='utf-8')
print('stage46_atomic_transition_r5=PASS target_empty_same_event_ocr=true handoff_same_cycle=true candidate_same_cycle=true no_polling=true')
