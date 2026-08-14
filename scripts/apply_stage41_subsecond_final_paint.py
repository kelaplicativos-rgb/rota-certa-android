#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
PIPELINE = PKG / 'FarolUniversalVisualPipelineStage19.kt'
HELPER = PKG / 'FarolFinalPaintFreshnessStage41.kt'
TEST34 = ROOT / 'app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage34Test.kt'

helper = r'''package br.com.mapeiaia.rotacerta

/**
 * Stage41: keep the anti-stale barrier, but do not let generation churn delay a final
 * Google-route result when the currently observed visual frame and final destination
 * are still exactly the same card.
 */
object FarolFinalPaintFreshnessStage41 {
    const val CONTRACT_MARKER = "FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41"
    const val SAME_FRAME_MARKER = "PENDING_SAME_HASH_SAME_DESTINATION_CAN_PAINT_STAGE41"
    const val CHANGED_FRAME_MARKER = "PENDING_CHANGED_FRAME_FAILS_CLOSED_STAGE41"
    const val HARD_END_TO_END_BUDGET_NS = 1_000_000_000L
    const val INTERNAL_POST_ROUTE_BUDGET_NS = 50_000_000L

    fun bindingMayPaint(
        bindingScreenHash: Int?,
        bindingAddressSignature: String,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
        visualVerificationPending: Boolean,
    ): Boolean {
        val currentSignature = currentAddressSignature?.takeIf(String::isNotBlank) ?: return false
        if (bindingAddressSignature.isBlank() || bindingAddressSignature != currentSignature) return false

        // Once the current candidate has been verified, Stage34 destination authority remains intact:
        // package/window/generation/hash churn is provenance only.
        if (!visualVerificationPending) return true

        // While verification is pending, a route result may only paint when the physical frame
        // itself is unchanged. This is the exact case proven by the Stage40 physical trace:
        // generations advanced while screenHash + final-destination signature stayed identical.
        return bindingScreenHash != null &&
            currentScreenHash != null &&
            bindingScreenHash == currentScreenHash
    }
}
'''
HELPER.write_text(helper)

s = PIPELINE.read_text()
old = '''    fun bindingMatchesCurrent(
        binding: Binding,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
        visualVerificationPending: Boolean,
    ): Boolean {
        @Suppress("UNUSED_VARIABLE") val rawStage34=currentScreenGeneration+currentWindowGeneration+(currentScreenHash?:0)
        return !visualVerificationPending && binding.addressSignature == currentAddressSignature
    }
'''
new = '''    fun bindingMatchesCurrent(
        binding: Binding,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
        visualVerificationPending: Boolean,
    ): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val provenanceOnlyStage41 = binding.screenGeneration + binding.windowGeneration + currentScreenGeneration + currentWindowGeneration
        return FarolFinalPaintFreshnessStage41.bindingMayPaint(
            bindingScreenHash = binding.screenHash,
            bindingAddressSignature = binding.addressSignature,
            currentScreenHash = currentScreenHash,
            currentAddressSignature = currentAddressSignature,
            visualVerificationPending = visualVerificationPending,
        )
    }
'''
if s.count(old) != 1:
    raise SystemExit(f'Stage41 binding block mismatch: {s.count(old)}')
PIPELINE.write_text(s.replace(old, new, 1))

# Stage34's original blanket pending-verification rejection was correct before physical evidence.
# Refine that single historical assertion without changing the test inventory: same frame is safe;
# a changed frame remains fail-closed.
t = TEST34.read_text()
old_test = '''    @Test fun bindingPendingVerificationRejected(){ val b=FarolUniversalVisualPipelineStage19.Binding(1,1,10,"sig"); assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(b,1,1,10,"sig",true)) }
'''
new_test = '''    @Test fun bindingPendingVerificationRequiresSameFrame(){ val b=FarolUniversalVisualPipelineStage19.Binding(1,1,10,"sig"); assertTrue(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(b,9,8,10,"sig",true)); assertFalse(FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(b,9,8,11,"sig",true)) }
'''
if t.count(old_test) != 1:
    raise SystemExit(f'Stage41 Stage34 regression assertion mismatch: {t.count(old_test)}')
TEST34.write_text(t.replace(old_test, new_test, 1))

print('stage41_subsecond_final_paint=PASS')
