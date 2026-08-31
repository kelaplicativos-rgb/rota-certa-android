package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicAgendaIncrementalAvailability0375Test {
    private val stops = listOf(
        TripStop(id = "a", order = 0, name = "A"),
        TripStop(id = "b", order = 1, name = "B"),
        TripStop(id = "c", order = 2, name = "C"),
        TripStop(id = "d", order = 3, name = "D"),
    )

    private fun trip(blabla: Int, rota: Int) = Trip(
        id = "trip-0375",
        title = "A → D",
        departureAtMillis = 4_000_000_000_000L,
        capacity = blabla + rota,
        status = TripStatus.PUBLISHED,
        stops = stops,
        publishedSeats = blabla,
        rotaCertaSeatAllocation = rota,
    )

    private fun booking(
        id: String,
        from: String = "a",
        to: String = "d",
        seats: Int = 1,
        status: BookingStatus = BookingStatus.CONFIRMED,
    ) = Booking(
        id = id,
        tripId = "trip-0375",
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = status,
        source = BookingSource.BLABLACAR,
        capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
        sourceReference = "BLABLACAR_SYNC:$id",
        occupancyGroupId = "group:$id",
    )

    @Test
    fun caseAThreePlusZeroMinusThreeIsLotadoAndBookingBlocked() {
        val claims = listOf(booking("p1"), booking("p2"), booking("p3"))
        val target = trip(3, 0)
        val summary = operationalSeatSummary(target, claims)
        val availability = SeatAvailabilityEngine.availability(target, claims, "a", "d", 1)
        assertEquals(3, summary.operationalInventorySeats)
        assertEquals(3, summary.confirmedPassengerSeats)
        assertEquals(0, summary.availableSeats)
        assertEquals(TripStatus.FULL, SeatAvailabilityEngine.suggestedStatus(target, claims))
        assertFalse(availability.canBook)
    }

    @Test
    fun caseBOneRotaCertaSeatProducesExactlyOneAvailableSeat() {
        val claims = listOf(booking("p1"), booking("p2"), booking("p3"))
        val summary = operationalSeatSummary(trip(3, 1), claims)
        assertEquals(4, summary.operationalInventorySeats)
        assertEquals(1, summary.availableSeats)
    }

    @Test
    fun caseCCancellationImmediatelyReleasesExactlyOneSeat() {
        val target = trip(3, 0)
        val claims = listOf(booking("p1"), booking("p2"), booking("p3", status = BookingStatus.CANCELLED))
        assertEquals(1, operationalSeatSummary(target, claims).availableSeats)
        assertTrue(SeatAvailabilityEngine.availability(target, claims, "a", "d", 1).canBook)
    }

    @Test
    fun caseDNewConfirmationConsumesTheLastSeatAgain() {
        val target = trip(3, 0)
        val reopened = listOf(booking("p1"), booking("p2"))
        assertEquals(1, operationalSeatSummary(target, reopened).availableSeats)
        val closed = reopened + booking("p3")
        assertEquals(0, operationalSeatSummary(target, closed).availableSeats)
        assertFalse(SeatAvailabilityEngine.availability(target, closed, "a", "d", 1).canBook)
    }

    @Test
    fun caseEIncompleteRosterIsNotAValidReliableSnapshot() {
        val source = collectorTrip(publishedSeats = 3, rosterComplete = false)
        val projected = PublicAgendaAutoSync0300.toPublicTrip(
            source = source,
            capacity = 3,
            rotaCertaSeatAllocation = 0,
            nowMillis = 0L,
            zoneId = ZoneId.of("America/Sao_Paulo"),
        )
        assertNotNull(projected)
        assertFalse(projected.sourceComplete)
        assertFalse(projected.trip.capacityReliable)
    }

    @Test
    fun caseFNewValidBlaBlaQuotaChangesOnlySemanticSnapshotRevision() {
        val before = collectorTrip(publishedSeats = 3, rosterComplete = true)
        val after = before.copy(published_seats = 4)
        val beforeRevision = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(before, 0)
        val afterRevision = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(after, 0)
        assertNotEquals(beforeRevision, afterRevision)
        assertEquals(beforeRevision, PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(before.copy(profile_name = "Renamed"), 0))
    }

    @Test
    fun caseGSegmentOccupancyUsesEveryTraversedSegmentAndReusesSeatsAfterDropoff() {
        val target = trip(2, 0)
        val claims = listOf(
            booking("early", from = "a", to = "b"),
            booking("late", from = "c", to = "d"),
        )
        val loads = SeatAvailabilityEngine.segmentLoads(target, claims)
        assertEquals(listOf(1, 0, 1), loads.map(SegmentLoad::passengerSeats))
        assertEquals(1, SeatAvailabilityEngine.availability(target, claims, "a", "d", 1).availableSeats)
        assertEquals(2, SeatAvailabilityEngine.availability(target, claims, "b", "c", 2).availableSeats)
        assertTrue(SeatAvailabilityEngine.availability(target, claims, "b", "c", 2).canBook)
    }

    @Test
    fun antiLoopAndIncrementalContractsAreWiredWithoutFullSyncOnCriticalPath() {
        val publisher = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        assertTrue(publisher.contains("PUBLIC_CAPACITY_INCREMENTAL_NO_OP"))
        assertTrue(publisher.contains("fullSyncRequested=false"))
        assertTrue(publisher.contains("syncExternalTripIncremental"))
        assertTrue(publisher.contains("syncLocalTripIncremental"))
        assertTrue(remote.contains("/capacity-snapshot"))
        assertTrue(timeline.contains("PublicAgendaAutoSync0300.syncExternalTripIncremental"))
        assertTrue(timeline.contains("incrementalPublishMutex.withLock"))
    }

    private fun collectorTrip(publishedSeats: Int?, rosterComplete: Boolean) = BlaBlaCollectorTrip(
        profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
        profile_name = "Driver name is not identity",
        date = "2030-09-04",
        departure_time = "10:30",
        actual_departure = "São Paulo",
        actual_arrival = "São Tomé das Letras",
        trip_id = "01a0359e-de23-78ab-ab26-6cc973c5c3d1",
        trip_href = "https://www.blablacar.com.br/rides/offer/01a0359e-de23-78ab-ab26-6cc973c5c3d1",
        published_seats = publishedSeats,
        booked_seats = 3,
        passenger_roster_complete = rosterComplete,
        passengers = listOf(
            BlaBlaCollectorPassenger(name = "P1", seats = 1, booking_href = "https://www.blablacar.com.br/passenger/p1"),
            BlaBlaCollectorPassenger(name = "P2", seats = 1, booking_href = "https://www.blablacar.com.br/passenger/p2"),
            BlaBlaCollectorPassenger(name = "P3", seats = 1, booking_href = "https://www.blablacar.com.br/passenger/p3"),
        ),
    )
}
