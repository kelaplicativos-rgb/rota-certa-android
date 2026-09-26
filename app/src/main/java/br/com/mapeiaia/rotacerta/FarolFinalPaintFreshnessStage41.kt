package br.com.mapeiaia.rotacerta

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
