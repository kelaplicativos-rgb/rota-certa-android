package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripUnifiedInventory0372Test {
    private val abStops = listOf(
        TripStop(id = "a", order = 0, name = "A"),
        TripStop(id = "b", order = 1, name = "B"),
    )

    private fun trip(
        blablaRemaining: Int?,
        rotaCerta: Int,
        stops: List<TripStop> = abStops,
    ) = Trip(
        id = "trip",
        title = "A → B",
        departureAtMillis = 4_000_000_000_000L,
        capacity = 0,
        status = TripStatus.PUBLISHED,
        stops = stops,
        publishedSeats = blablaRemaining,
        rotaCertaSeatAllocation = rotaCerta,
    )

    private fun booking(
        id: String,
        seats: Int = 1,
        source: BookingSource = BookingSource.ROTA_CERTA,
        claimType: CapacityClaimType = CapacityClaimType.PASSENGER,
        status: BookingStatus = BookingStatus.CONFIRMED,
        from: String = "a",
        to: String = "b",
        group: String? = null,
        sourceReference: String = "",
        passengerId: String = "",
    ) = Booking(
        id = id,
        tripId = "trip",
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = status,
        source = source,
        capacityClaimType = claimType,
        occupancyGroupId = group,
        sourceReference = sourceReference,
        passengerId = passengerId,
    )

    private fun normalized(trip: Trip, bookings: List<Booking>): Trip =
        trip.copy(capacity = operationalInventoryCapacity(trip, bookings))

    @Test
    fun test01RotaCertaExplicitZeroKeepsBlaBlaRemainingAvailable() {
        val summary = operationalSeatSummary(trip(blablaRemaining = 3, rotaCerta = 0), emptyList())
        assertEquals(3, summary.availableSeats)
        assertEquals(3, summary.blablaAvailableSeats)
        assertEquals(0, summary.rotaCertaAllocatedSeats)
    }

    @Test
    fun test02BasicCombinedInventoryIsFive() {
        val summary = operationalSeatSummary(trip(blablaRemaining = 3, rotaCerta = 2), emptyList())
        assertEquals(5, summary.totalAvailableSeats)
    }

    @Test
    fun test03BlaBlaConfirmedPassengerIsNotSubtractedFromRemainingSeatsAgain() {
        val external = booking(
            id = "bb-1",
            source = BookingSource.BLABLACAR,
            claimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
        )
        val summary = operationalSeatSummary(trip(blablaRemaining = 3, rotaCerta = 2), listOf(external))
        assertEquals(1, summary.confirmedPassengerSeats)
        assertEquals(5, summary.availableSeats)
    }

    @Test
    fun test04ReferenceScenarioThreeExternalPlusTwoLocalLeavesTwo() {
        val bookings = listOf(
            booking("bb-1", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("bb-2", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("bb-3", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("agenda-1", source = BookingSource.ROTA_CERTA),
            booking("manual-1", source = BookingSource.PRIVATE),
        )
        // BlaBlaCar has zero remaining after its three confirmed occupants.
        // Reconstructed BlaBla share = 0 remaining + 3 occupied; plus 4 Rota Certa = 7.
        val summary = operationalSeatSummary(trip(blablaRemaining = 0, rotaCerta = 4), bookings)
        assertEquals(5, summary.confirmedPassengerSeats)
        assertEquals(2, summary.availableSeats)
    }

    @Test
    fun test05StrongIdentityDeduplicatesExternalAndLocalMirror() {
        val bookings = listOf(
            booking(
                "external",
                source = BookingSource.BLABLACAR,
                claimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
                sourceReference = "reservation-strong-1",
            ),
            booking(
                "local-mirror",
                source = BookingSource.ROTA_CERTA,
                sourceReference = "reservation-strong-1",
            ),
        )
        val summary = operationalSeatSummary(trip(blablaRemaining = 2, rotaCerta = 2), bookings)
        assertEquals(1, summary.confirmedPassengerSeats)
        assertEquals(4, summary.availableSeats)
    }

    @Test
    fun test06ManualConfirmedPassengerConsumesExactlyOneSeat() {
        val base = trip(blablaRemaining = 2, rotaCerta = 2)
        val normalized = normalized(base, emptyList())
        val before = SeatAvailabilityEngine.remainingSeatsForWholeTrip(normalized, emptyList())
        val after = SeatAvailabilityEngine.remainingSeatsForWholeTrip(normalized, listOf(booking("manual", source = BookingSource.PRIVATE)))
        assertEquals(4, before)
        assertEquals(3, after)
    }

    @Test
    fun test07AgendaConfirmedPassengerConsumesExactlyOneSeat() {
        val base = trip(blablaRemaining = 2, rotaCerta = 2)
        val normalized = normalized(base, emptyList())
        val after = SeatAvailabilityEngine.remainingSeatsForWholeTrip(normalized, listOf(booking("agenda")))
        assertEquals(3, after)
    }

    @Test
    fun test08CancellationReleasesExactlyOneSeat() {
        val base = trip(blablaRemaining = 2, rotaCerta = 2)
        val normalized = normalized(base, emptyList())
        val active = booking("agenda")
        val cancelled = active.copy(status = BookingStatus.CANCELLED)
        assertEquals(3, SeatAvailabilityEngine.remainingSeatsForWholeTrip(normalized, listOf(active)))
        assertEquals(4, SeatAvailabilityEngine.remainingSeatsForWholeTrip(normalized, listOf(cancelled)))
    }

    @Test
    fun test09ZeroAvailabilityIsStableAndNeverNegative() {
        val base = normalized(trip(blablaRemaining = 0, rotaCerta = 0), emptyList())
        val summary = operationalSeatSummary(base, emptyList())
        assertEquals(0, summary.availableSeats)
        assertEquals(0, summary.overbookingSeats)
        assertEquals(0, SeatAvailabilityEngine.remainingSeatsForWholeTrip(base, emptyList()))
    }

    @Test
    fun test10TransactionalGuardRejectsRequestLargerThanRemainingRange() {
        val base = normalized(trip(blablaRemaining = 1, rotaCerta = 0), emptyList())
        val availability = SeatAvailabilityEngine.availability(
            trip = base,
            bookings = emptyList(),
            boardingStopId = "a",
            dropoffStopId = "b",
            requestedSeats = 2,
        )
        assertEquals(1, availability.availableSeats)
        assertFalse(availability.canBook)
    }

    @Test
    fun test11BlaBlaRemainingSeatsNeverDoubleSubtractExternalRoster() {
        val external = listOf(
            booking("bb-1", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("bb-2", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY),
            booking("bb-3", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY),
        )
        val summary = operationalSeatSummary(trip(blablaRemaining = 2, rotaCerta = 4), external)
        assertEquals(3, summary.confirmedPassengerSeats)
        assertEquals(6, summary.availableSeats)
    }

    @Test
    fun test12ExplicitZeroPersistenceDoesNotFallBackToLegacyVehicleCapacity() {
        val repository = File("src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt").readText()
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(repository.contains("prefs[rotaCertaSeatAllocation] ?: 0"))
        assertFalse(repository.contains("prefs[rotaCertaSeatAllocation] ?: prefs[vehicleCapacity]"))
        assertFalse(ui.contains("Capacidade de passageiros"))
        assertFalse(ui.contains("Capacidade do veículo"))
        assertTrue(ui.contains("Vagas disponibilizadas no Rota Certa"))
    }

    @Test
    fun test13LegacyVehicleCapacityIsReadCompatibleButIgnoredOperationally() {
        val models = File("src/main/java/br/com/mapeiaia/rotacerta/Models.kt").readText()
        val repository = File("src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt").readText()
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(models.contains("val vehicleCapacity: Int = 0"))
        assertTrue(repository.contains("Read-only legacy compatibility"))
        assertTrue(timeline.contains("@Suppress(\"UNUSED_PARAMETER\") vehicleCapacity"))
        assertFalse(timeline.contains("capacity = vehicleCapacity"))
    }

    @Test
    fun test14SeatIsReusableAfterIntermediateDropoff() {
        val stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        )
        val base = trip(blablaRemaining = 0, rotaCerta = 2, stops = stops)
        val normalized = normalized(base, emptyList())
        val bookings = listOf(
            booking("a-b", seats = 2, source = BookingSource.PRIVATE, from = "a", to = "b"),
            booking("b-c", seats = 1, source = BookingSource.ROTA_CERTA, from = "b", to = "c"),
        )
        val loads = SeatAvailabilityEngine.segmentLoads(normalized, bookings)
        assertEquals(listOf(0, 1), loads.map(SegmentLoad::availableSeats))

        val bToC = SeatAvailabilityEngine.availability(
            trip = normalized,
            bookings = bookings,
            boardingStopId = "b",
            dropoffStopId = "c",
            requestedSeats = 1,
        )
        assertEquals(1, bToC.availableSeats)
        assertTrue(bToC.canBook)
    }
}
