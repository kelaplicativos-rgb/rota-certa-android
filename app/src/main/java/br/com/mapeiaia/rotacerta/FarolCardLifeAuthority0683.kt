package br.com.mapeiaia.rotacerta

/**
 * 0.1.683 — visual card lifetime authority.
 *
 * A final FAROL result belongs to the semantic ride card, never to a timer.
 * The identity intentionally uses the visible location sequence only; volatile fare, ETA,
 * driver-to-pickup distance and map animation cannot make the same card blink.
 */
object FarolCardLifeAuthority0683 {
    const val CONTRACT_MARKER = "FAROL_CARD_LIFE_AUTHORITY_0683"
    const val NO_VISUAL_TTL_MARKER = "NO_TIMER_CARD_OWNS_BUBBLE_0683"
    const val PROVEN_REPLACEMENT_MARKER = "PROVEN_CARD_REPLACEMENT_CLEARS_BEFORE_NEW_ROUTE_0683"
    private const val SEPARATOR = " -> "

    fun identity(evaluation: FarolUniversalVisualPipelineStage19.Evaluation): String {
        val addresses = evaluation.addresses.ifEmpty {
            listOfNotNull(evaluation.pickup.takeIf(String::isNotBlank), evaluation.destination.takeIf(String::isNotBlank))
        }
        val canonical = addresses.mapNotNull { address ->
            DestinationAddressIdentityPolicy.identity(address).canonical.takeIf(String::isNotBlank)
        }
        if (canonical.isEmpty()) return ""
        return canonical.joinToString(SEPARATOR)
    }

    fun sameCard(currentIdentity: String?, candidateIdentity: String?): Boolean {
        val current = currentIdentity?.trim()?.takeIf(String::isNotEmpty) ?: return false
        val candidate = candidateIdentity?.trim()?.takeIf(String::isNotEmpty) ?: return false
        if (current == candidate) return true
        val currentLocations = current.split(SEPARATOR).map(String::trim).filter(String::isNotBlank)
        val candidateLocations = candidate.split(SEPARATOR).map(String::trim).filter(String::isNotBlank)
        if (currentLocations.size != candidateLocations.size || currentLocations.isEmpty()) return false
        return currentLocations.indices.all { index ->
            DestinationAddressIdentityPolicy.areCompatible(
                DestinationAddressIdentityPolicy.identity(currentLocations[index]),
                DestinationAddressIdentityPolicy.identity(candidateLocations[index]),
            )
        }
    }

    fun provesReplacement(
        activeFinal: Boolean,
        currentIdentity: String?,
        candidateIdentity: String?,
    ): Boolean = activeFinal &&
        !currentIdentity.isNullOrBlank() &&
        !candidateIdentity.isNullOrBlank() &&
        !sameCard(currentIdentity, candidateIdentity)
}
