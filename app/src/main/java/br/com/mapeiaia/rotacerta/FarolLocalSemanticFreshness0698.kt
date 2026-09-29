package br.com.mapeiaia.rotacerta

/**
 * 0.1.698: local coordinate/Haversine results are owned by the semantic card lease.
 *
 * Visual/window epochs remain acquisition authority for OCR and remote refinement, but ordinary
 * surface churn cannot revoke a local result while the Stage36 runtime token and destination stay
 * current. A real destination transition or explicit visual-lease clear still invalidates the token.
 */
object FarolLocalSemanticFreshness0698 {
    const val CONTRACT_MARKER = "FAROL_LOCAL_SEMANTIC_FRESHNESS_0698"
    const val ACCEPTED_MARKER = "FAROL_LOCAL_RESULT_ACCEPTED_SAME_DESTINATION_0698"
    const val RUNTIME_STALE_MARKER = "FAROL_LOCAL_RESULT_DROPPED_RUNTIME_TOKEN_0698"
    const val DESTINATION_CHANGED_MARKER = "FAROL_LOCAL_RESULT_DROPPED_DESTINATION_CHANGED_0698"
    const val RAW_PREBIND_REMOVED_MARKER = "FAROL_RAW_PRE_SANITIZATION_AUTHORITY_REMOVED_0698"

    enum class PaintAuthority {
        VISUAL_SURFACE,
        LOCAL_SEMANTIC,
    }

    enum class Verdict {
        ACCEPTED_SAME_DESTINATION,
        DROPPED_RUNTIME_TOKEN,
        DROPPED_DESTINATION_CHANGED,
    }

    fun evaluate(
        runtimeTokenFresh: Boolean,
        bindingAddressSignature: String?,
        activeAddressSignature: String?,
    ): Verdict {
        if (!runtimeTokenFresh) return Verdict.DROPPED_RUNTIME_TOKEN
        return if (
            DestinationAddressIdentityPolicy.sameDestinationSignatures(
                bindingAddressSignature,
                activeAddressSignature,
            )
        ) {
            Verdict.ACCEPTED_SAME_DESTINATION
        } else {
            Verdict.DROPPED_DESTINATION_CHANGED
        }
    }

    fun marker(verdict: Verdict): String = when (verdict) {
        Verdict.ACCEPTED_SAME_DESTINATION -> ACCEPTED_MARKER
        Verdict.DROPPED_RUNTIME_TOKEN -> RUNTIME_STALE_MARKER
        Verdict.DROPPED_DESTINATION_CHANGED -> DESTINATION_CHANGED_MARKER
    }
}
