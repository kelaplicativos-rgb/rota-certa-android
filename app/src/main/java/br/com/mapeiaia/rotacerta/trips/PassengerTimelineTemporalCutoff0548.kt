package br.com.mapeiaia.rotacerta.trips

/**
 * Display-only adapter for the operational Timeline.
 * The lifecycle definition is canonical and shared with Agenda publication via
 * [canonicalAgendaLifecycleDecision0581].
 */
internal fun isPassengerTimelineCurrentOrUpcoming0548(
    departureAtMillis: Long,
    arrivalAtMillis: Long?,
    nowMillis: Long,
): Boolean = canonicalAgendaLifecycleDecision0581(
    departureAtMillis = departureAtMillis,
    arrivalAtMillis = arrivalAtMillis,
    nowMillis = nowMillis,
).visible
