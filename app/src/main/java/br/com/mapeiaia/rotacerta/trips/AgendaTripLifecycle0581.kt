package br.com.mapeiaia.rotacerta.trips

internal const val OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577 = 60L * 60L * 1000L
internal const val OPERATIONAL_TRIP_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577 = 12L * 60L * 60L * 1000L

internal data class AgendaTripLifecycleDecision0581(
    val visible: Boolean,
    val visibleUntilMillis: Long,
    val reasonCode: String,
)

private fun agendaLifecycleSafePlus0581(base: Long, delta: Long): Long =
    if (base > Long.MAX_VALUE - delta) Long.MAX_VALUE else base + delta

internal fun canonicalAgendaArrivalAtMillis0581(
    departureAtMillis: Long,
    plannedArrivalMillis: Long?,
    plannedDepartureMillis: Long? = null,
): Long? = listOfNotNull(plannedArrivalMillis, plannedDepartureMillis)
    .filter { it > 0L && it >= departureAtMillis }
    .maxOrNull()

internal fun canonicalAgendaVisibleUntilMillis0581(
    departureAtMillis: Long,
    arrivalAtMillis: Long?,
): Long {
    if (departureAtMillis <= 0L) return 0L
    val validArrival = arrivalAtMillis?.takeIf { it > 0L && it >= departureAtMillis }
    return if (validArrival != null) {
        agendaLifecycleSafePlus0581(validArrival, OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577)
    } else {
        agendaLifecycleSafePlus0581(departureAtMillis, OPERATIONAL_TRIP_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577)
    }
}

internal fun canonicalAgendaLifecycleDecision0581(
    departureAtMillis: Long,
    arrivalAtMillis: Long?,
    nowMillis: Long,
): AgendaTripLifecycleDecision0581 {
    val visibleUntil = canonicalAgendaVisibleUntilMillis0581(departureAtMillis, arrivalAtMillis)
    if (visibleUntil <= 0L) {
        return AgendaTripLifecycleDecision0581(
            visible = false,
            visibleUntilMillis = 0L,
            reasonCode = "AGENDA_VISIBILITY_INVALID_TIME",
        )
    }
    val visible = nowMillis <= visibleUntil
    return AgendaTripLifecycleDecision0581(
        visible = visible,
        visibleUntilMillis = visibleUntil,
        reasonCode = when {
            !visible -> "AGENDA_VISIBILITY_EXPIRED"
            nowMillis < departureAtMillis -> "AGENDA_VISIBILITY_FUTURE_TRIP"
            else -> "AGENDA_VISIBILITY_ACTIVE_TRIP"
        },
    )
}

internal fun canonicalAgendaArrivalAtMillis0581(trip: Trip): Long? {
    val lastStop = trip.stops.sortedBy(TripStop::order).lastOrNull() ?: return null
    return canonicalAgendaArrivalAtMillis0581(
        departureAtMillis = trip.departureAtMillis,
        plannedArrivalMillis = lastStop.plannedArrivalMillis,
        plannedDepartureMillis = lastStop.plannedDepartureMillis,
    )
}

internal fun Trip.canonicalAgendaVisibleUntilMillis0581(): Long =
    canonicalAgendaVisibleUntilMillis0581(
        departureAtMillis = departureAtMillis,
        arrivalAtMillis = canonicalAgendaArrivalAtMillis0581(this),
    )

internal fun Trip.withCanonicalAgendaVisibility0581(): Trip {
    val calculated = canonicalAgendaVisibleUntilMillis0581()
    return if (agendaVisibleUntilMillis0581 == calculated) this else copy(agendaVisibleUntilMillis0581 = calculated)
}

internal fun canonicalAgendaTripStillVisible0581(
    trip: Trip,
    nowMillis: Long,
): Boolean = canonicalAgendaLifecycleDecision0581(
    departureAtMillis = trip.departureAtMillis,
    arrivalAtMillis = canonicalAgendaArrivalAtMillis0581(trip),
    nowMillis = nowMillis,
).visible
