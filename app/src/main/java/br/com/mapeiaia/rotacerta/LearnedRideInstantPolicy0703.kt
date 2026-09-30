package br.com.mapeiaia.rotacerta

import java.util.Locale

object LearnedRideInstantPolicy0703 {
    const val CONTRACT_MARKER = "LEARNED_RIDE_INSTANT_FAROL_0703"
    const val DESTINATION_AUTH_MARKER = "LEARNED_DESTINATION_ONLY_AUTH_0703"
    const val LAYOUT_BYPASS_MARKER = "LEARNED_LAYOUT_GATE_BYPASS_0703"
    const val PARTIAL_PRESERVE_MARKER = "LEARNED_SAME_CARD_FINAL_PRESERVE_0703"

    fun canAuthorizeDestination(result: LearnedRideReader0700.Result?): Boolean =
        result?.applied == true && !result.destination.isNullOrBlank()

    fun shouldPreserveFinalOnPartialRead(
        selectedPackage: String?,
        activePackage: String?,
        hasActiveAddress: Boolean,
        finalPaintVisible: Boolean,
        sessionCurrent: Boolean,
        learnedProfileUsable: Boolean,
    ): Boolean {
        val selected = normalize(selectedPackage) ?: return false
        val active = normalize(activePackage) ?: return false
        return learnedProfileUsable &&
            sessionCurrent &&
            hasActiveAddress &&
            finalPaintVisible &&
            selected == active
    }

    private fun normalize(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
}
