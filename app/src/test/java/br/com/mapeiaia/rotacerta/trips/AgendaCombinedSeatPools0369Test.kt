package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaCombinedSeatPools0369Test {
    private fun trip(
        physicalCapacity: Int,
        blablaAllocation: Int? = null,
        rotaCertaAllocation: Int? = null,
        stops: List<TripStop> = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
        ),
    ) = Trip(
        id = "trip",
        title = "A → B",
        departureAtMillis = 4_000_000_000_000L,
        capacity = physicalCapacity,
        rotaCertaSeatAllocation = rotaCertaAllocation,
        status = TripStatus.PUBLISHED,
        stops = stops,
        publishedSeats = blablaAllocation,
    )

    private fun booking(
        id: String,
        trip: Trip,
        seats: Int,
        source: BookingSource,
        claimType: CapacityClaimType = CapacityClaimType.PASSENGER,
        status: BookingStatus = BookingStatus.CONFIRMED,
        from: String = "a",
        to: String = "b",
        group: String? = null,
    ) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = status,
        source = source,
        capacityClaimType = claimType,
        occupancyGroupId = group,
    )

    @Test
    fun finalRuleThreePlusFourMinusFiveEqualsTwoWithoutChangingPhysicalCapacity() {
        val trip = trip(
            physicalCapacity = 7,
            blablaAllocation = 3,
            rotaCertaAllocation = 4,
        )
        val bookings = listOf(
            booking("blabla", trip, 3, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY, group = "bb"),
            booking("rota", trip, 2, BookingSource.ROTA_CERTA, group = "family-linked"),
            // The family representation is already inside the Rota Certa booking.
            booking("family-mirror", trip, 1, BookingSource.PRIVATE, CapacityClaimType.RESERVED_SEAT, group = "family-linked"),
        )

        val summary = operationalSeatSummary(trip, bookings)
        assertTrue(summary.operationalLimitConfigured)
        assertEquals(3, summary.blablaPublishedSeats)
        assertEquals(4, summary.rotaCertaAllocatedSeats)
        assertEquals(7, summary.totalConsideredSeats)
        assertEquals(5, summary.confirmedPassengerSeats)
        assertEquals(0, summary.blockedSeats)
        assertEquals(2, summary.availableSeats)
        assertEquals(0, summary.overbookingSeats)

        val availability = SeatAvailabilityEngine.availability(trip, bookings, "a", "b", requestedSeats = 2)
        assertEquals(2, availability.availableSeats)
        assertTrue(availability.canBook)
        assertFalse(SeatAvailabilityEngine.availability(trip, bookings, "a", "b", requestedSeats = 3).canBook)
    }

    @Test
    fun independentFamilyPassengerConsumesOneMoreSeat() {
        val trip = trip(7, blablaAllocation = 3, rotaCertaAllocation = 4)
        val bookings = listOf(
            booking("blabla", trip, 3, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("rota", trip, 2, BookingSource.ROTA_CERTA),
            booking("family-independent", trip, 1, BookingSource.PRIVATE),
        )
        val summary = operationalSeatSummary(trip, bookings)
        assertEquals(6, summary.confirmedPassengerSeats)
        assertEquals(1, summary.availableSeats)
    }

    @Test
    fun blockedSeatReducesAvailabilityButIsNotAConfirmedPassenger() {
        val trip = trip(7, blablaAllocation = 3, rotaCertaAllocation = 4)
        val bookings = listOf(
            booking("blabla", trip, 3, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("rota", trip, 2, BookingSource.ROTA_CERTA),
            booking("blocked", trip, 1, BookingSource.OTHER, CapacityClaimType.RESERVED_SEAT),
        )
        val summary = operationalSeatSummary(trip, bookings)
        assertEquals(5, summary.confirmedPassengerSeats)
        assertEquals(1, summary.blockedSeats)
        assertEquals(1, summary.availableSeats)
    }

    @Test
    fun cancellationReleasesExactlyTheCancelledSeats() {
        val trip = trip(7, blablaAllocation = 3, rotaCertaAllocation = 4)
        val active = listOf(
            booking("blabla", trip, 3, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("rota", trip, 2, BookingSource.ROTA_CERTA),
        )
        assertEquals(2, operationalSeatSummary(trip, active).availableSeats)

        val cancelled = active.map { booking ->
            if (booking.id == "rota") booking.copy(status = BookingStatus.CANCELLED) else booking
        }
        assertEquals(3, operationalSeatSummary(trip, cancelled).confirmedPassengerSeats)
        assertEquals(4, operationalSeatSummary(trip, cancelled).availableSeats)
    }

    @Test
    fun segmentReuseStillUsesPhysicalCapacityPerSegment() {
        val trip = trip(
            physicalCapacity = 4,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
                TripStop(id = "c", order = 2, name = "C"),
            ),
        )
        val bookings = listOf(
            booking("a-b", trip, 3, BookingSource.PRIVATE, from = "a", to = "b"),
            booking("b-c", trip, 1, BookingSource.PRIVATE, from = "b", to = "c"),
        )
        val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
        assertEquals(listOf(3, 1), loads.map(SegmentLoad::passengerSeats))
        assertEquals(listOf(1, 3), loads.map(SegmentLoad::availableSeats))
        assertEquals(1, SeatAvailabilityEngine.availability(trip, bookings, "a", "b").availableSeats)
        assertEquals(3, SeatAvailabilityEngine.availability(trip, bookings, "b", "c").availableSeats)
    }

    @Test
    fun sameStrongOccupancyGroupIsCountedOnceAcrossRepresentations() {
        val trip = trip(7, blablaAllocation = 3, rotaCertaAllocation = 4)
        val bookings = listOf(
            booking("external", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY, group = "reservation-strong-id"),
            booking("local-mirror", trip, 1, BookingSource.ROTA_CERTA, CapacityClaimType.PASSENGER, group = "reservation-strong-id"),
        )
        val summary = operationalSeatSummary(trip, bookings)
        assertEquals(1, summary.confirmedPassengerSeats)
        assertEquals(6, summary.availableSeats)
        assertEquals(1, SeatAvailabilityEngine.segmentLoads(trip, bookings).single().passengerSeats)
    }

    @Test
    fun pendingRequestBlocksAvailabilityButDoesNotBecomeConfirmedPassenger() {
        val trip = trip(7, blablaAllocation = 3, rotaCertaAllocation = 4)
        val pending = booking(
            "pending",
            trip,
            2,
            BookingSource.ROTA_CERTA,
            status = BookingStatus.REQUESTED,
        )
        val summary = operationalSeatSummary(trip, listOf(pending))
        assertEquals(0, summary.confirmedPassengerSeats)
        assertEquals(2, summary.blockedSeats)
        assertEquals(5, summary.availableSeats)
    }

    @Test
    fun physicalOverbookingIsVisibleAndNeverCreatesArtificialCapacity() {
        val trip = trip(4)
        val booking = booking("too-many", trip, 5, BookingSource.PRIVATE)
        val load = SeatAvailabilityEngine.segmentLoads(trip, listOf(booking)).single()
        assertEquals(5, load.passengerSeats)
        assertEquals(0, load.availableSeats)
        assertEquals(1, load.overbookingSeats)
        assertEquals(4, trip.capacity)
    }

    @Test
    fun architectureNoLongerAddsChannelNumbersIntoPhysicalCapacity() {
        val agenda = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        val domain = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripDomain.kt").readText()
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertTrue(agenda.contains("capacity = physicalCapacity"))
        assertTrue(agenda.contains("rotaCertaSeatAllocation"))
        assertTrue(agenda.contains("totalConsidered"))
        assertFalse(agenda.contains("combinedAgendaAvailableSeats"))
        assertFalse(agenda.contains("blablaAvailableSeats"))
        assertFalse(agenda.contains("rotaCertaSeatPool"))
        assertTrue(domain.contains("operationalSeatSummary"))
        assertTrue(domain.contains("EXTERNAL_OCCUPANCY"))
        assertTrue(ui.contains("Capacidade de passageiros"))
        assertTrue(ui.contains("Passageiros confirmados"))
        assertTrue(ui.contains("Vagas disponíveis"))
    }

    @Test
    fun normalSynchronizationReadsPublishedSeatsButCannotWriteThem() {
        val policy = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaHarvestPolicy.kt").readText()
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(policy.contains("AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = true"))
        assertTrue(dynamic.contains("BlaBlaBrowserRequest.SEAT_OPTIONS"))
        assertFalse(dynamic.contains("executeRemoteWrite("))
        assertFalse(dynamic.contains("BlaBlaBrowserRequest.SEAT_CHANGE"))
        assertFalse(dynamic.contains("BlaBlaBrowserRequest.SEAT_SAVE"))
    }
}
