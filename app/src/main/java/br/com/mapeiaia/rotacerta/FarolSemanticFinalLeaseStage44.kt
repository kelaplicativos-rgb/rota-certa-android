package br.com.mapeiaia.rotacerta

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
