package br.com.mapeiaia.rotacerta.trips

/**
 * Display-only cutoff for the operational Timeline.
 * Canonical Agenda data is never deleted or mutated here.
 *
 * 0.1.581: Timeline delegates to the exact same operational visibility policy
 * used by Agenda, so both surfaces expire a card at the same instant.
 */
internal fun isPassengerTimelineCurrentOrUpcoming0548(
    departureAtMillis: Long,
    arrivalAtMillis: Long?,
    nowMillis: Long,
): Boolean = operationalTripStillVisible0577(
    departureAtMillis = departureAtMillis,
    arrivalAtMillis = arrivalAtMillis,
    nowMillis = nowMillis,
)
