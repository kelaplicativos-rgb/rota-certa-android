package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaTimelineOperationalWindow0581Test {
    private fun trip(
        departureAtMillis: Long,
        arrivalAtMillis: Long?,
        finalDepartureAtMillis: Long? = null,
    ): Trip = Trip(
        title = "Operational parity",
        departureAtMillis = departureAtMillis,
        stops = listOf(
            TripStop(
                order = 0,
                name = "Origem",
                plannedDepartureMillis = departureAtMillis,
            ),
            TripStop(
                order = 1,
                name = "Destino",
                plannedArrivalMillis = arrivalAtMillis,
                plannedDepartureMillis = finalDepartureAtMillis,
            ),
        ),
    )

    private fun assertAgendaTimelineParity(trip: Trip, timelineArrivalAtMillis: Long?, nowMillis: Long) {
        assertEquals(
            operationalTripStillVisible0577(trip, nowMillis),
            isPassengerTimelineCurrentOrUpcoming0548(
                departureAtMillis = trip.departureAtMillis,
                arrivalAtMillis = timelineArrivalAtMillis,
                nowMillis = nowMillis,
            ),
        )
    }

    @Test fun bothSurfacesUseFinalArrivalPlusTwoHours() {
        val departure = 1_000_000L
        val arrival = departure + 4L * 60L * 60L * 1000L
        val card = trip(departure, arrival)
        val grace = OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577

        assertAgendaTimelineParity(card, arrival, departure - 1L)
        assertAgendaTimelineParity(card, arrival, arrival)
        assertAgendaTimelineParity(card, arrival, arrival + grace)
        assertAgendaTimelineParity(card, arrival, arrival + grace + 1L)
    }

    @Test fun bothSurfacesUseSafeRetentionWhenArrivalIsUnknown() {
        val departure = 2_000_000L
        val card = trip(departure, null)
        val retention = OPERATIONAL_TRIP_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577

        assertAgendaTimelineParity(card, null, departure + 1L)
        assertAgendaTimelineParity(card, null, departure + retention)
        assertAgendaTimelineParity(card, null, departure + retention + 1L)
    }

    @Test fun agendaCanUseFinalStopDepartureAsArrivalFallback() {
        val departure = 3_000_000L
        val finalStopTime = departure + 3L * 60L * 60L * 1000L
        val card = trip(departure, null, finalStopTime)

        assertEquals(
            finalStopTime + OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577,
            operationalTripVisibleUntil0577(card),
        )
    }
}
