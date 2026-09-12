package br.com.mapeiaia.rotacerta.trips

import kotlin.math.abs

internal const val PASSENGER_FARE_ACTION_LABEL = "💰"

internal enum class PassengerTimelineRoutePosition {
    PAST_REQUIRES_REVIEW,
    CURRENT_OR_UPCOMING,
    UNKNOWN,
}

internal fun passengerTimelineOperationalOrder(
    rows: List<EnhancedPassengerCardRow>,
    progress: TripRouteProgress?,
): List<EnhancedPassengerCardRow> = rows.sortedWith(
    compareBy<EnhancedPassengerCardRow> { passengerTimelineRoutePosition(it, progress).priority }
        .thenBy { passengerTimelineDistanceFromProgress(it, progress) }
        .thenBy { passengerTimelineReservationPriority(it.bookingStatus) }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
)

internal fun passengerTimelineNextActionIndex(
    rows: List<EnhancedPassengerCardRow>,
    progress: TripRouteProgress?,
): Int? {
    if (rows.isEmpty()) return null
    val review = rows.indexOfFirst {
        passengerTimelineRoutePosition(it, progress) == PassengerTimelineRoutePosition.PAST_REQUIRES_REVIEW
    }
    if (review >= 0) return review
    val upcoming = rows.indexOfFirst {
        passengerTimelineRoutePosition(it, progress) == PassengerTimelineRoutePosition.CURRENT_OR_UPCOMING
    }
    return upcoming.takeIf { it >= 0 }
}

internal fun passengerTimelineActionLabel(
    row: EnhancedPassengerCardRow,
    index: Int,
    nextActionIndex: Int?,
    progress: TripRouteProgress?,
): String? = when {
    passengerTimelineRoutePosition(row, progress) == PassengerTimelineRoutePosition.PAST_REQUIRES_REVIEW ->
        "⚠ EMBARQUE A CONFERIR"
    nextActionIndex == index -> "📍 PRÓXIMA AÇÃO"
    else -> null
}

private val PassengerTimelineRoutePosition.priority: Int
    get() = when (this) {
        PassengerTimelineRoutePosition.PAST_REQUIRES_REVIEW -> 0
        PassengerTimelineRoutePosition.CURRENT_OR_UPCOMING -> 1
        PassengerTimelineRoutePosition.UNKNOWN -> 2
    }

private fun passengerTimelineRoutePosition(
    row: EnhancedPassengerCardRow,
    progress: TripRouteProgress?,
): PassengerTimelineRoutePosition {
    val stop = row.boardingStopIndex ?: return PassengerTimelineRoutePosition.UNKNOWN
    val current = progress?.stopIndexProgress ?: return PassengerTimelineRoutePosition.CURRENT_OR_UPCOMING
    return if (stop.toDouble() < current - 0.35) {
        PassengerTimelineRoutePosition.PAST_REQUIRES_REVIEW
    } else {
        PassengerTimelineRoutePosition.CURRENT_OR_UPCOMING
    }
}

private fun passengerTimelineDistanceFromProgress(
    row: EnhancedPassengerCardRow,
    progress: TripRouteProgress?,
): Double {
    val stop = row.boardingStopIndex?.toDouble() ?: return Double.POSITIVE_INFINITY
    val current = progress?.stopIndexProgress ?: return stop
    return abs(stop - current)
}

private fun passengerTimelineReservationPriority(status: BookingStatus?): Int = when (status) {
    BookingStatus.HELD -> 0
    BookingStatus.CONFIRMED -> 1
    BookingStatus.REQUESTED -> 2
    null -> 3
    BookingStatus.CANCELLED, BookingStatus.EXPIRED -> 4
}
