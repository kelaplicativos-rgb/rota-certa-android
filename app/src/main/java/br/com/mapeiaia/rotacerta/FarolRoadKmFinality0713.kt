package br.com.mapeiaia.rotacerta

/**
 * 0.1.713 — public FAROL kilometres are road-route only.
 *
 * Haversine remains useful for the local radius recommendation, but it is private evidence.
 * The user-visible bubble may expose kilometres only after a road route is confirmed for the
 * same semantic address. A confirmed road result is monotonic for that binding.
 */
object FarolRoadKmFinality0713 {
    const val CONTRACT_MARKER = "FAROL_ROAD_KM_FINALITY_0713"
    const val LOCAL_KM_SUPPRESSED_MARKER = "FAROL_LOCAL_KM_NEVER_PUBLIC_0713"
    const val ROAD_KM_PUBLISHED_MARKER = "FAROL_ROAD_KM_CONFIRMED_PUBLIC_0713"
    const val INVALID_ROAD_KM_REJECTED_MARKER = "FAROL_INVALID_ROAD_KM_REJECTED_0713"
    const val SAME_BINDING_MONOTONIC_MARKER = "FAROL_ROAD_RESULT_MONOTONIC_SAME_BINDING_0713"
    const val TIMER_ONLY_REVOKE_BLOCKED_MARKER = "FAROL_TIMER_ONLY_REVOKE_BLOCKED_0713"

    enum class DistanceAuthority { LOCAL_HAVERSINE, ROAD_CONFIRMED }

    fun publicDistanceKm(authority: DistanceAuthority, distanceKm: Double?): Double? =
        distanceKm?.takeIf { authority == DistanceAuthority.ROAD_CONFIRMED }
            ?.takeIf { isPublishableRoadDistance(it) }

    fun isPublishableRoadDistance(distanceKm: Double?): Boolean =
        distanceKm != null && distanceKm.isFinite() && distanceKm >= 0.0

    fun shouldPreserveConfirmedRoad(
        confirmedBinding: String?,
        candidateBinding: String?,
        confirmedDistanceKm: Double?,
        currentDistanceKm: Double?,
    ): Boolean {
        if (confirmedBinding.isNullOrBlank() || candidateBinding.isNullOrBlank()) return false
        if (confirmedBinding != candidateBinding) return false
        if (!isPublishableRoadDistance(confirmedDistanceKm)) return false
        if (!isPublishableRoadDistance(currentDistanceKm)) return false
        return kotlin.math.abs(confirmedDistanceKm!! - currentDistanceKm!!) < 0.000_001
    }
}
