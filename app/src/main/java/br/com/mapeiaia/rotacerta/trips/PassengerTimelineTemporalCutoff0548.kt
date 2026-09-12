package br.com.mapeiaia.rotacerta.trips

/**
 * Display-only cutoff for the operational Timeline.
 * Canonical Agenda data is never deleted or mutated here.
 */
internal fun isPassengerTimelineCurrentOrUpcoming0548(
    departureAtMillis: Long,
    arrivalAtMillis: Long?,
    nowMillis: Long,
): Boolean {
    val validArrivalAtMillis = arrivalAtMillis?.takeIf { arrival ->
        arrival > 0L && arrival >= departureAtMillis
    }
    return validArrivalAtMillis?.let { it >= nowMillis }
        ?: (departureAtMillis >= nowMillis)
}
