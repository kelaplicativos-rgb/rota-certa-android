package br.com.mapeiaia.rotacerta

/**
 * 0.1.749 — a transient single pickup observation cannot overwrite a freshly proven A/B card.
 *
 * This directly protects cards where OCR/Accessibility first proves A + B and a later partial
 * Accessibility event exposes only A. B remains authoritative during a short coalescing lease.
 */
object FarolPairDestinationAuthority0749 {
    const val CONTRACT_MARKER = "FAROL_PAIR_DESTINATION_AUTHORITY_0749"
    const val PARTIAL_PICKUP_BLOCKED_MARKER = "PARTIAL_A_CANNOT_REPLACE_B_0749"
    const val PAIR_LEASE_MILLIS = 2_500L

    data class Decision(
        val suppressCandidate: Boolean,
        val reason: String,
    )

    fun evaluate(
        previousPair: FarolUniversalVisualPipelineStage19.Evaluation?,
        candidate: FarolUniversalVisualPipelineStage19.Evaluation,
        samePackage: Boolean,
        previousPairAtElapsedMillis: Long,
        nowElapsedMillis: Long,
    ): Decision {
        if (previousPair == null || previousPair.addresses.size < 2) {
            return Decision(false, "no_previous_pair")
        }
        if (candidate.addresses.size != 1) return Decision(false, "candidate_not_single")
        if (!samePackage) return Decision(false, "package_changed")
        if (previousPair.windowId != candidate.windowId) return Decision(false, "window_changed")

        val age = nowElapsedMillis - previousPairAtElapsedMillis
        if (age !in 0L..PAIR_LEASE_MILLIS) return Decision(false, "pair_lease_expired")

        val pickupSignature = DestinationAddressIdentityPolicy.signature("visual", previousPair.pickup)
        val destinationSignature = DestinationAddressIdentityPolicy.signature("visual", previousPair.destination)
        val candidateSignature = DestinationAddressIdentityPolicy.signature("visual", candidate.destination)
        if (pickupSignature.isBlank() || destinationSignature.isBlank() || candidateSignature.isBlank()) {
            return Decision(false, "blank_signature")
        }

        val candidateIsPreviousPickup = DestinationAddressIdentityPolicy.sameDestinationSignatures(
            pickupSignature,
            candidateSignature,
        )
        val candidateIsPreviousDestination = DestinationAddressIdentityPolicy.sameDestinationSignatures(
            destinationSignature,
            candidateSignature,
        )
        return if (candidateIsPreviousPickup && !candidateIsPreviousDestination) {
            Decision(true, "fresh_pair_destination_b_blocks_partial_pickup_a")
        } else {
            Decision(false, if (candidateIsPreviousDestination) "same_destination_b" else "different_destination")
        }
    }
}
