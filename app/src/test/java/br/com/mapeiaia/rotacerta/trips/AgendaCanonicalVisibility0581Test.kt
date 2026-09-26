package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaCanonicalVisibility0581Test {
    private val hour = 60L * 60L * 1000L
    private val now = 2_000_000_000_000L

    private fun trip(
        departure: Long,
        arrival: Long?,
        finalDeparture: Long? = null,
    ): Trip = Trip(
        id = "trip-0581",
        title = "Origem → Destino",
        departureAtMillis = departure,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        publicToken = "public0581",
        canonicalRevision = 7,
        publicationRevision = 9,
        publicTimezoneId0411 = "America/Sao_Paulo",
        stops = listOf(
            TripStop(
                id = "origin",
                order = 0,
                name = "Origem",
                plannedDepartureMillis = departure,
            ),
            TripStop(
                id = "destination",
                order = 1,
                name = "Destino",
                plannedArrivalMillis = arrival,
                plannedDepartureMillis = finalDeparture,
            ),
        ),
    ).withCanonicalAgendaVisibility0581()

    @Test
    fun futureTripTomorrowRemainsVisible() {
        val value = trip(now + 24 * hour, now + 28 * hour)
        val decision = canonicalAgendaLifecycleDecision0581(
            value.departureAtMillis,
            canonicalAgendaArrivalAtMillis0581(value),
            now,
        )
        assertTrue(decision.visible)
        assertEquals("AGENDA_VISIBILITY_FUTURE_TRIP", decision.reasonCode)
    }

    @Test
    fun sameDayTripBeforeDepartureRemainsVisible() {
        val value = trip(now + hour, now + 5 * hour)
        assertTrue(canonicalAgendaTripStillVisible0581(value, now))
    }

    @Test
    fun tripInProgressRemainsVisibleUntilCanonicalEndWindow() {
        val value = trip(now - hour, now + 3 * hour)
        val decision = canonicalAgendaLifecycleDecision0581(
            value.departureAtMillis,
            canonicalAgendaArrivalAtMillis0581(value),
            now,
        )
        assertTrue(decision.visible)
        assertEquals("AGENDA_VISIBILITY_ACTIVE_TRIP", decision.reasonCode)
    }

    @Test
    fun firstSegmentPastDoesNotExpireTripWithFutureFinalSegment() {
        val value = Trip(
            id = "multi-0581",
            title = "A → C",
            departureAtMillis = now - 2 * hour,
            capacity = 4,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop("a", 0, "A", plannedDepartureMillis = now - 2 * hour),
                TripStop("b", 1, "B", plannedArrivalMillis = now - hour, plannedDepartureMillis = now - hour),
                TripStop("c", 2, "C", plannedArrivalMillis = now + 2 * hour),
            ),
        ).withCanonicalAgendaVisibility0581()

        assertEquals(now + 2 * hour, canonicalAgendaArrivalAtMillis0581(value))
        assertTrue(canonicalAgendaTripStillVisible0581(value, now))
    }

    @Test
    fun genuinelyEndedTripExpiresOnlyAfterCanonicalWindow() {
        val arrival = now - OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577 - 1
        val value = trip(arrival - 4 * hour, arrival)
        val decision = canonicalAgendaLifecycleDecision0581(
            value.departureAtMillis,
            canonicalAgendaArrivalAtMillis0581(value),
            now,
        )
        assertFalse(decision.visible)
        assertEquals("AGENDA_VISIBILITY_EXPIRED", decision.reasonCode)
    }

    @Test
    fun utcConversionCannotAdvanceBrazilExpiration() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val localDeparture = LocalDate.of(2030, 9, 20).atTime(LocalTime.of(10, 30)).atZone(saoPaulo)
        val localArrival = LocalDate.of(2030, 9, 20).atTime(LocalTime.of(15, 30)).atZone(saoPaulo)
        val reference = LocalDate.of(2030, 9, 19).atTime(LocalTime.of(23, 59)).atZone(saoPaulo).toInstant().toEpochMilli()
        val value = trip(
            localDeparture.toInstant().toEpochMilli(),
            localArrival.toInstant().toEpochMilli(),
        )
        assertTrue(canonicalAgendaTripStillVisible0581(value, reference))
        assertEquals(
            value.agendaVisibleUntilMillis0581,
            canonicalAgendaVisibleUntilMillis0581(
                localDeparture.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toEpochMilli(),
                localArrival.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            ),
        )
    }

    @Test
    fun midnightBoundaryDoesNotTurnTomorrowIntoPast() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val reference = LocalDate.of(2030, 9, 19).atTime(23, 55).atZone(zone).toInstant().toEpochMilli()
        val departure = LocalDate.of(2030, 9, 20).atTime(0, 15).atZone(zone).toInstant().toEpochMilli()
        val arrival = LocalDate.of(2030, 9, 20).atTime(4, 15).atZone(zone).toInstant().toEpochMilli()
        assertTrue(canonicalAgendaTripStillVisible0581(trip(departure, arrival), reference))
    }

    @Test
    fun partialCollectorSnapshotCannotEraseLifecycle() {
        val complete = trip(now + hour, now + 5 * hour).copy(externalSnapshotComplete = true)
        val partial = complete.copy(
            externalSnapshotComplete = false,
            externalSnapshotFingerprint = "",
        ).withCanonicalAgendaVisibility0581()
        assertEquals(complete.agendaVisibleUntilMillis0581, partial.agendaVisibleUntilMillis0581)
        assertTrue(canonicalAgendaTripStillVisible0581(partial, now))
    }

    @Test
    fun collectorFailureMetadataDoesNotParticipateInLifecycle() {
        val canonical = trip(now + hour, now + 5 * hour)
        val withoutObservation = canonical.copy(
            externalSnapshot = null,
            externalSnapshotComplete = false,
            lastObservedAtMillis = 0L,
        ).withCanonicalAgendaVisibility0581()
        assertEquals(canonical.agendaVisibleUntilMillis0581, withoutObservation.agendaVisibleUntilMillis0581)
    }

    @Test
    fun missingPermalinkDoesNotHideTrip() {
        val value = trip(now + hour, now + 5 * hour).copy(
            blablaManageUrl = null,
            blablaPublicUrl = null,
        ).withCanonicalAgendaVisibility0581()
        assertTrue(canonicalAgendaTripStillVisible0581(value, now))
    }

    @Test
    fun capacityMutationCannotChangeLifecycle() {
        val value = trip(now + hour, now + 5 * hour)
        val capacityChanged = value.copy(
            capacity = 7,
            publishedSeats = 2,
            rotaCertaSeatAllocation = 5,
        ).withCanonicalAgendaVisibility0581()
        assertEquals(value.agendaVisibleUntilMillis0581, capacityChanged.agendaVisibleUntilMillis0581)
    }

    @Test
    fun staleCanonicalRetryCannotBeatNewerRevision() {
        assertEquals(
            CanonicalTripRevisionDecision0395.SKIP_STALE_REVISION,
            canonicalTripRevisionDecision0395(
                currentRevision = 12,
                incomingRevision = 11,
                semanticChanged = true,
            ),
        )
    }

    @Test
    fun internalAndPublicProjectionShareCanonicalCutoff() {
        val value = trip(now + hour, now + 5 * hour)
        val payload = canonicalPublicProjectionPayload0411(
            trip = value,
            bookings = emptyList(),
            publicationRevision = value.publicationRevision,
            nowMillis = now,
        )
        assertTrue(
            isPassengerTimelineCurrentOrUpcoming0548(
                value.departureAtMillis,
                canonicalAgendaArrivalAtMillis0581(value),
                now,
            ),
        )
        assertEquals(value.agendaVisibleUntilMillis0581, payload.agendaVisibleUntilMillis0581)
        assertTrue(now <= payload.agendaVisibleUntilMillis0581)
    }

    @Test
    fun finalStopDepartureCanExtendLifecycleWhenItIsLaterThanArrival() {
        val value = trip(
            departure = now - 2 * hour,
            arrival = now + hour,
            finalDeparture = now + 2 * hour,
        )
        assertEquals(now + 2 * hour, canonicalAgendaArrivalAtMillis0581(value))
        assertEquals(
            now + 2 * hour + OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577,
            value.agendaVisibleUntilMillis0581,
        )
    }
}
