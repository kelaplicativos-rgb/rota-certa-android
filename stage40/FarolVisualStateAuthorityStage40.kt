package br.com.mapeiaia.rotacerta

/**
 * Stage40 final public FAROL visual authority.
 *
 * Intermediate acquisition states never own the public color. Reading OFF is absolute GRAY.
 * Reading ON without a fresh final route decision is YELLOW. Only a current final route result
 * with a real distance may expose GREEN or RED.
 */
object FarolVisualStateAuthorityStage40 {
    const val CONTRACT_MARKER = "FAROL_FINAL_VISUAL_STATE_AUTHORITY_STAGE40"
    const val OFF_MARKER = "READING_OFF_FORCES_GRAY_STAGE40"
    const val NO_ORANGE_MARKER = "NO_PUBLIC_ORANGE_STAGE40"
    const val FINAL_ONLY_MARKER = "GREEN_RED_REQUIRE_FINAL_ROUTE_DISTANCE_STAGE40"

    enum class PublicState { GRAY, YELLOW, GREEN, RED }

    data class Decision(
        val state: PublicState,
        val distanceKm: Double?,
        val reason: String,
    )

    fun decide(
        readingEnabled: Boolean,
        requestedColorName: String,
        requestedDistanceKm: Double?,
    ): Decision {
        if (!readingEnabled) return Decision(PublicState.GRAY, null, "reading_off_absolute")

        val distance = requestedDistanceKm?.takeIf { it.isFinite() && it >= 0.0 }
        return when (requestedColorName.trim().lowercase()) {
            "green" -> if (distance != null) Decision(PublicState.GREEN, distance, "fresh_final_inside")
                else Decision(PublicState.YELLOW, null, "green_without_final_distance_rejected")
            "red" -> if (distance != null) Decision(PublicState.RED, distance, "fresh_final_outside")
                else Decision(PublicState.YELLOW, null, "red_without_final_distance_rejected")
            else -> Decision(PublicState.YELLOW, null, "reading_on_waiting_final_decision")
        }
    }
}
