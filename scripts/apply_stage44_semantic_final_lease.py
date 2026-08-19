#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
HELPER = PKG / 'FarolSemanticFinalLeaseStage44.kt'


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


HELPER.write_text(r'''package br.com.mapeiaia.rotacerta

/** Stage44: a raw/structural event is not proof that the currently painted final card changed. */
object FarolSemanticFinalLeaseStage44 {
    const val CONTRACT_MARKER = "FAROL_SEMANTIC_FINAL_LEASE_STAGE44"
    const val RAW_EVENT_MARKER = "RAW_STRUCTURAL_EVENT_CANNOT_REVOKE_FINAL_STAGE44"
    const val RAW_DUPLICATE_MARKER = "UNCHANGED_SNAPSHOT_PRESERVES_FINAL_STAGE44"
    const val SAME_SIGNATURE_MARKER = "SAME_ADDRESS_SIGNATURE_PRESERVES_FINAL_STAGE44"
    const val PROVEN_CHANGE_MARKER = "YELLOW_ONLY_AFTER_PROVEN_CARD_CHANGE_STAGE44"
    const val NO_POLLING_MARKER = "NO_POLLING_NO_CONTINUOUS_OCR_STAGE44"

    data class Lease(
        val activeFinal: Boolean,
        val color: String,
        val distanceKm: Double?,
        val addressSignature: String?,
    )

    fun capture(color: String, distanceKm: Double?, addressSignature: String?): Lease {
        val normalizedColor = color.trim().lowercase()
        val normalizedSignature = addressSignature?.trim()?.takeIf { it.isNotEmpty() }
        val finalColor = normalizedColor == "green" || normalizedColor == "red"
        return Lease(
            activeFinal = finalColor && distanceKm != null && normalizedSignature != null,
            color = color,
            distanceKm = distanceKm,
            addressSignature = normalizedSignature,
        )
    }

    fun preservesSameSemanticCard(lease: Lease, candidateAddressSignature: String?): Boolean {
        if (!lease.activeFinal) return false
        val current = lease.addressSignature?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val candidate = candidateAddressSignature?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return current == candidate
    }
}
''', encoding='utf-8')

service = SERVICE.read_text(encoding='utf-8')

old = '''        FarolCausalLatencyStage28.Metrics.increment("visualIdentityChanged")
        FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsStarted")

        // Mandatory Stage26 order: previous generation/result is invalidated BEFORE heavy traversal.
        invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)

        val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(
'''
new = '''        FarolCausalLatencyStage28.Metrics.increment("visualIdentityChanged")
        FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsStarted")

        // Stage44: pre-collect activity is evidence to inspect, never authority to revoke a valid final.
        // Keep the currently painted Green/Red leased until the collected frame proves a real change.
        val finalLeaseStage44 = FarolSemanticFinalLeaseStage44.capture(
            currentRadarColor.name,
            currentDistanceKm,
            universalActiveAddressSignature,
        )
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_FINAL_LEASE_HELD_PRECOLLECT", eventPackageStage19,
            details = "activeFinal=${finalLeaseStage44.activeFinal}; color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; admissionReason=${admissionStage26.reason}; admissionGeneration=${admissionStage26.visualGeneration}",
        )

        val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(
'''
service = once(service, old, new, 'defer destructive invalidation until proof')

old = '''        if (!visualDecisionStage23.process) {
            // Raw Accessibility identity is unchanged. A proven semantic mutation may still be an image-only card (99/Uber).
            FarolVisualIdentityStage23.Metrics.increment("unchangedVisualSkipped")
'''
new = '''        if (!visualDecisionStage23.process) {
            // Stage44: exact raw duplicate proves that the structural event did not change this visual frame.
            // Never turn a valid Green/Red into Yellow before this branch.
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_RAW_DUPLICATE_FINAL_PRESERVED", eventPackageStage19, cycleId = cycleIdStage20,
                details = "activeFinal=${finalLeaseStage44.activeFinal}; color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${finalLeaseStage44.addressSignature.orEmpty()}; snapshotHash=${collectionStage26.snapshot.hash}; semanticMutation=${semanticDecisionStage32.mutation}",
            )
            FarolVisualIdentityStage23.Metrics.increment("unchangedVisualSkipped")
'''
service = once(service, old, new, 'raw duplicate preserves final')

old = '''        if (!isReadingActivationGenerationFreshStage26(activationStage26.generation)) {
            FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
            return true
        }
        if (evaluationStage19 != null) {
'''
new = '''        if (!isReadingActivationGenerationFreshStage26(activationStage26.generation)) {
            FarolReadingActivationStage26.Metrics.increment("workCancelledOnReadingOff")
            return true
        }

        if (evaluationStage19 != null &&
            FarolSemanticFinalLeaseStage44.preservesSameSemanticCard(finalLeaseStage44, evaluationStage19.addressSignature)
        ) {
            // Raw text/layout may change (price, timer, animation) while pickup/destination still identify the same card.
            // Preserve the already-final Google decision and absorb the new raw snapshot as processed.
            stage19VisualVerificationPending = false
            stage19OcrSerial += 1L
            FarolReadingActivationStage26.Metrics.increment("ocrCancelled")
            stage23OcrGate.cancelBecauseAccessibilityWon(visualDecisionStage23.generation, collectionStage26.snapshot.hash)
            stage21OcrGate.cancelBecauseAccessibilityWon()
            stage19OcrRerunRequested = false
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_SEMANTIC_SAME_CARD_FINAL_PRESERVED", eventPackageStage19, cycleId = cycleIdStage20,
                details = "color=${finalLeaseStage44.color}; distance=${finalLeaseStage44.distanceKm ?: -1.0}; signature=${evaluationStage19.addressSignature}; snapshotHash=${collectionStage26.snapshot.hash}; admissionGeneration=${admissionStage26.visualGeneration}",
            )
            return true
        }

        // Only now is a different/ambiguous visual state proven. Revoke the old final and enter Yellow
        // before processing the new candidate or demanding OCR. This prevents duplicate events from
        // producing Red->Yellow flicker while retaining fail-closed behavior for genuine card changes.
        invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S44_PROVEN_CARD_CHANGE_INVALIDATE", eventPackageStage19, cycleId = cycleIdStage20,
            details = "candidate=${evaluationStage19 != null}; oldFinal=${finalLeaseStage44.activeFinal}; oldSignature=${finalLeaseStage44.addressSignature.orEmpty()}; newSignature=${evaluationStage19?.addressSignature.orEmpty()}; snapshotHash=${collectionStage26.snapshot.hash}; admissionGeneration=${admissionStage26.visualGeneration}",
        )

        if (evaluationStage19 != null) {
'''
service = once(service, old, new, 'semantic lease decision before invalidation')

old = '''    private fun invalidateOldVisualBeforeCollectStage26(newGenerationStage26: Long, eventStartedNsStage26: Long) {
'''
new = '''    // Stage44: historical name retained for patch compatibility; callers now invoke it only AFTER
    // collection/evaluation proves a different or ambiguous card. It is no longer a pre-collect action.
    private fun invalidateOldVisualBeforeCollectStage26(newGenerationStage26: Long, eventStartedNsStage26: Long) {
'''
service = once(service, old, new, 'document post-proof invalidation')

# Do not couple the functional patch to inherited comments or user-facing diagnostic wording.
# Those strings have changed across Stage32..43 and are not part of the Stage44 causal contract.

SERVICE.write_text(service, encoding='utf-8')
print('stage44_semantic_final_lease=PASS functional_anchors_only=true')
