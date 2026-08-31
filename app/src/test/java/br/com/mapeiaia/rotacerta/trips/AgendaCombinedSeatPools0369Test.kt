package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaCombinedSeatPools0369Test {
    private fun trip(
        physicalCapacity: Int,
        blablaAvailable: Int? = null,
        rotaCertaAvailable: Int? = null,
        stops: List<TripStop> = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
        ),
    ) = Trip(
        id = "trip",
        title = "A → B",
        departureAtMillis = 4_000_000_000_000L,
        capacity = physicalCapacity,
        rotaCertaSeatAllocation = rotaCertaAvailable,
        status = TripStatus.PUBLISHED,
        stops = stops,
        // Legacy persisted name; semantically this is the current BlaBlaCar free-seat value.
        publishedSeats = blablaAvailable,
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
    fun latestRuleTwoBlaBlaFreePlusFourRotaCertaFreeEqualsSixImmediately() {
        val trip = trip(
            physicalCapacity = 7,
            blablaAvailable = 2,
            rotaCertaAvailable = 4,
        )

        val summary = operationalSeatSummary(trip, emptyList())

        assertTrue(summary.operationalLimitConfigured)
        assertEquals(2, summary.blablaAvailableSeats)
        assertEquals(4, summary.rotaCertaAllocatedSeats)
        assertEquals(4, summary.rotaCertaAvailableSeats)
        assertEquals(6, summary.totalAvailableSeats)
        assertEquals(6, summary.availableSeats)
        assertEquals(0, summary.confirmedPassengerSeats)
        assertEquals(0, summary.blockedSeats)
    }

    @Test
    fun BlaBlaConfirmedPassengersDoNotSubtractFreeSeatsAgain() {
        val trip = trip(7, blablaAvailable = 2, rotaCertaAvailable = 4)
        val bookings = listOf(
            booking(
                "blabla-already-accounted",
                trip,
                3,
                BookingSource.BLABLACAR,
                CapacityClaimType.EXTERNAL_OCCUPANCY,
                group = "bb",
            ),
        )

        val summary = operationalSeatSummary(trip, bookings)

        assertEquals(3, summary.confirmedPassengerSeats)
        assertEquals(2, summary.blablaAvailableSeats)
        assertEquals(4, summary.rotaCertaAvailableSeats)
        assertEquals(6, summary.availableSeats)
    }

    @Test
    fun localRotaCertaPassengerConsumesOnlyRotaCertaPool() {
        val trip = trip(7, blablaAvailable = 2, rotaCertaAvailable = 4)
        val bookings = listOf(
            booking("rota", trip, 1, BookingSource.ROTA_CERTA),
        )

        val summary = operationalSeatSummary(trip, bookings)

        assertEquals(1, summary.confirmedPassengerSeats)
        assertEquals(2, summary.blablaAvailableSeats)
        assertEquals(3, summary.rotaCertaAvailableSeats)
        assertEquals(5, summary.availableSeats)
    }

    @Test
    fun familyMirrorInsideSameStrongGroupIsNotCountedTwice() {
        val trip = trip(7, blablaAvailable = 2, rotaCertaAvailable = 4)
        val bookings = listOf(
            booking(
                "external",
                trip,
                1,
                BookingSource.BLABLACAR,
                CapacityClaimType.EXTERNAL_OCCUPANCY,
                group = "same-reservation",
            ),
            booking(
                "family-mirror",
                trip,
                1,
                BookingSource.ROTA_CERTA,
                CapacityClaimType.PASSENGER,
                group = "same-reservation",
            ),
        )

        val summary = operationalSeatSummary(trip, bookings)

        assertEquals(1, summary.confirmedPassengerSeats)
        assertEquals(4, summary.rotaCertaAvailableSeats)
        assertEquals(6, summary.availableSeats)
    }

    @Test
    fun independentFamilyPassengerConsumesOneRotaCertaSeat() {
        val trip = trip(7, blablaAvailable = 2, rotaCertaAvailable = 4)
        val bookings = listOf(
            booking("external", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("family-independent", trip, 1, BookingSource.PRIVATE),
        )

        val summary = operationalSeatSummary(trip, bookings)

        assertEquals(2, summary.confirmedPassengerSeats)
        assertEquals(3, summary.rotaCertaAvailableSeats)
        assertEquals(5, summary.availableSeats)
    }

    @Test
    fun blockedSeatReducesRotaCertaAvailabilityButIsNotConfirmedPassenger() {
        val trip = trip(7, blablaAvailable = 2, rotaCertaAvailable = 4)
        val blocked = booking(
            "blocked",
            trip,
            1,
            BookingSource.OTHER,
            CapacityClaimType.RESERVED_SEAT,
        )

        val summary = operationalSeatSummary(trip, listOf(blocked))

        assertEquals(0, summary.confirmedPassengerSeats)
        assertEquals(1, summary.blockedSeats)
        assertEquals(3, summary.rotaCertaAvailableSeats)
        assertEquals(5, summary.availableSeats)
    }

    @Test
    fun cancellationReleasesRotaCertaSeatImmediately() {
        val trip = trip(7, blablaAvailable = 2, rotaCertaAvailable = 4)
        val active = booking("rota", trip, 1, BookingSource.ROTA_CERTA)
        assertEquals(5, operationalSeatSummary(trip, listOf(active)).availableSeats)

        val cancelled = active.copy(status = BookingStatus.CANCELLED)
        assertEquals(6, operationalSeatSummary(trip, listOf(cancelled)).availableSeats)
    }

    @Test
    fun physicalSegmentEngineRemainsSeparateFromGlobalChannelAvailability() {
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
    fun architectureUsesSeatEditorAsRemainingSeatEvidenceAndRemovesLegacyPhysicalCapacity() {
        val agenda = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        val domain = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripDomain.kt").readText()
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertTrue(agenda.contains("blablaAvailable"))
        assertTrue(agenda.contains("rotaCertaAvailable"))
        assertTrue(agenda.contains("operationalInventory"))
        assertTrue(agenda.contains("capacitySource=blablacar_remaining_plus_external_peak_plus_rota_certa"))
        assertTrue(domain.contains("operationalInventoryCapacity"))
        assertTrue(domain.contains("bookingOccupancyIdentityKey"))
        assertTrue(domain.contains("EXTERNAL_OCCUPANCY"))
        assertFalse(ui.contains("Capacidade de passageiros"))
        assertFalse(ui.contains("Capacidade do veículo"))
        assertTrue(ui.contains("Vagas disponibilizadas no Rota Certa"))
        assertTrue(ui.contains("Total disponível"))
    }

    @Test
    fun normalSynchronizationReadsSeatOptionsButCannotWriteThem() {
        val policy = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaHarvestPolicy.kt").readText()
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        assertTrue(policy.contains("AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = true"))
        assertTrue(dynamic.contains("BlaBlaBrowserRequest.SEAT_OPTIONS"))
        assertFalse(dynamic.contains("executeRemoteWrite("))
        assertFalse(dynamic.contains("BlaBlaBrowserRequest.SEAT_CHANGE"))
        assertFalse(dynamic.contains("BlaBlaBrowserRequest.SEAT_SAVE"))
    }
}
