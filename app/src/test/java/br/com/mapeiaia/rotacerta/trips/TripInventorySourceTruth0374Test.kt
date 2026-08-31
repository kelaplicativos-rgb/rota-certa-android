package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripInventorySourceTruth0374Test {
    private val stops = listOf(
        TripStop(id = "a", order = 0, name = "A"),
        TripStop(id = "b", order = 1, name = "B"),
        TripStop(id = "c", order = 2, name = "C"),
        TripStop(id = "d", order = 3, name = "D"),
    )

    private fun trip(blablaQuota: Int, rotaCertaQuota: Int, tripStops: List<TripStop> = stops) = Trip(
        id = "trip",
        title = "A → D",
        departureAtMillis = 4_000_000_000_000L,
        capacity = 0,
        status = TripStatus.PUBLISHED,
        stops = tripStops,
        publishedSeats = blablaQuota,
        rotaCertaSeatAllocation = rotaCertaQuota,
    )

    private fun booking(
        id: String,
        from: String = "a",
        to: String = "d",
        seats: Int = 1,
        source: BookingSource = BookingSource.ROTA_CERTA,
        claimType: CapacityClaimType = CapacityClaimType.PASSENGER,
        status: BookingStatus = BookingStatus.CONFIRMED,
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

    private fun normalized(base: Trip, bookings: List<Booking>) =
        base.copy(capacity = operationalInventoryCapacity(base, bookings))

    @Test
    fun case1ThreePlusZeroMinusThreeIsZeroAndSoldOut() {
        val confirmed = (1..3).map {
            booking("bb-$it", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY)
        }
        val base = trip(3, 0)
        val normalized = normalized(base, confirmed)
        val summary = operationalSeatSummary(base, confirmed)
        val availability = SeatAvailabilityEngine.availability(normalized, confirmed, "a", "d", 1)

        assertEquals(3, summary.blablaQuotaSeats)
        assertEquals(0, summary.rotaCertaQuotaSeats)
        assertEquals(3, summary.operationalInventorySeats)
        assertEquals(3, summary.confirmedPassengerSeats)
        assertEquals(0, summary.availableSeats)
        assertEquals(TripStatus.FULL, SeatAvailabilityEngine.suggestedStatus(normalized, confirmed))
        assertEquals(0, availability.availableSeats)
        assertFalse(availability.canBook)
    }

    @Test
    fun case2ThreePlusTwoMinusThreeIsTwo() {
        val confirmed = (1..3).map { booking("p-$it") }
        val base = trip(3, 2)
        val summary = operationalSeatSummary(base, confirmed)
        assertEquals(5, summary.operationalInventorySeats)
        assertEquals(2, summary.availableSeats)
    }

    @Test
    fun case3ThreePlusZeroMinusZeroIsThree() {
        val summary = operationalSeatSummary(trip(3, 0), emptyList())
        assertEquals(3, summary.operationalInventorySeats)
        assertEquals(3, summary.availableSeats)
    }

    @Test
    fun case4ZeroPlusTwoMinusTwoIsSoldOut() {
        val confirmed = listOf(booking("p", seats = 2))
        val base = trip(0, 2)
        val normalized = normalized(base, confirmed)
        assertEquals(0, operationalSeatSummary(base, confirmed).availableSeats)
        assertEquals(TripStatus.FULL, SeatAvailabilityEngine.suggestedStatus(normalized, confirmed))
    }

    @Test
    fun case5CancellationReleasesExactlyTheCancelledSeats() {
        val base = trip(3, 0)
        val all = listOf(booking("p1"), booking("p2"), booking("p3"))
        assertEquals(0, operationalSeatSummary(base, all).availableSeats)
        val after = all.map { if (it.id == "p3") it.copy(status = BookingStatus.CANCELLED) else it }
        assertEquals(1, operationalSeatSummary(base, after).availableSeats)
    }

    @Test
    fun case6NewConfirmationConsumesExactlyTheConfirmedSeats() {
        val base = trip(3, 2)
        val before = listOf(booking("p1"), booking("p2"))
        val after = before + booking("new", seats = 2)
        assertEquals(3, operationalSeatSummary(base, before).availableSeats)
        assertEquals(1, operationalSeatSummary(base, after).availableSeats)
    }

    @Test
    fun case7StrongIdentityMirrorConsumesOneSeatOnly() {
        val base = trip(3, 0)
        val claims = listOf(
            booking("external", source = BookingSource.BLABLACAR, claimType = CapacityClaimType.EXTERNAL_OCCUPANCY, sourceReference = "strong-1"),
            booking("mirror", source = BookingSource.ROTA_CERTA, sourceReference = "strong-1"),
        )
        val summary = operationalSeatSummary(base, claims)
        assertEquals(1, summary.confirmedPassengerSeats)
        assertEquals(2, summary.availableSeats)
    }

    @Test
    fun segmentOccupancyReleasesSeatAfterDropoffAndUsesRequestedMinimum() {
        val base = trip(3, 0)
        val normalized = normalized(base, emptyList())
        val claims = listOf(
            booking("p1", from = "a", to = "b"),
            booking("p2", from = "b", to = "d"),
            booking("p3", from = "a", to = "c"),
        )
        val loads = SeatAvailabilityEngine.segmentLoads(normalized, claims)
        assertEquals(listOf(2, 2, 1), loads.map(SegmentLoad::passengerSeats))
        assertEquals(listOf(1, 1, 2), loads.map(SegmentLoad::availableSeats))
        assertEquals(listOf(0, 0, 0), loads.map(SegmentLoad::overbookingSeats))
        assertEquals(1, SeatAvailabilityEngine.availability(normalized, claims, "b", "d", 1).availableSeats)
        assertEquals(2, SeatAvailabilityEngine.availability(normalized, claims, "c", "d", 2).availableSeats)
        assertTrue(SeatAvailabilityEngine.availability(normalized, claims, "c", "d", 2).canBook)
    }

    @Test
    fun explicitZeroIsKnownNotUnknownInTimelineCapacity() {
        val resolution = resolveTimelinePublicCapacity(
            operationalInventory = 0,
            blablaQuota = 0,
            passengerSeats = 0,
        )
        assertEquals(0, resolution.operationalInventory)
        assertEquals(0, resolution.availableSeats)
        assertEquals("trip_operational_inventory", resolution.capacitySource)
    }

    @Test
    fun externalMissingBlaBlaQuotaRemainsPendingWhileExplicitZeroIsKnown() {
        fun entry(published: Int?) = TripTimelineEntry(
            tripId = "timeline-ext-zero",
            profileId = "profile",
            profileLabel = "Profile",
            departureAtMillis = 4_000_000_000_000L,
            arrivalAtMillis = null,
            origin = "A",
            destination = "D",
            status = TripStatus.PUBLISHED,
            capacity = 0,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            blablaTripId = "trip-zero",
            blablaProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            blablaPublishedSeats = published,
            blablaPassengerRosterComplete = true,
            rotaCertaSeatAllocation = 0,
        )
        assertEquals(TimelineOccupancyReadState.PENDING, timelineOccupancyReadState(entry(null)))
        assertEquals(TimelineOccupancyReadState.CAPACITY_CONFIGURED, timelineOccupancyReadState(entry(0)))
        assertEquals(0, timelinePublicCapacityResolution(entry(0)).availableSeats)
    }

    @Test
    fun timelineNormalizationNeverAddsConfirmedPassengersToQuota() {
        val entry = TripTimelineEntry(
            tripId = "timeline-ext-test",
            profileId = "profile",
            profileLabel = "Profile",
            departureAtMillis = 4_000_000_000_000L,
            arrivalAtMillis = null,
            origin = "A",
            destination = "D",
            status = TripStatus.PUBLISHED,
            capacity = 99,
            minimumOccupiedSeats = 3,
            maximumOccupiedSeats = 3,
            sourcePassengerSeats = mapOf(BookingSource.BLABLACAR to 3),
            blablaTripId = "trip-1",
            blablaProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            blablaPublishedSeats = 3,
            blablaPassengerRosterComplete = true,
        )
        val normalized = applyConfiguredVehicleCapacity(listOf(entry), vehicleCapacity = 99, rotaCertaSeatAllocation = 0).single()
        val publicCapacity = timelinePublicCapacityResolution(normalized)
        assertEquals(3, normalized.capacity)
        assertEquals(0, publicCapacity.availableSeats)
    }

    @Test
    fun sourceAndUiContractsDoNotContainGhostCapacityFormula() {
        val domain = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripDomain.kt").readText()
        val publisher = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        val ui = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertFalse(domain.contains("externalConfirmedPeakSeats"))
        assertFalse(publisher.contains("blablacar_remaining_plus_external_peak_plus_rota_certa"))
        assertTrue(publisher.contains("capacitySource=blablacar_quota_plus_rota_certa_quota"))
        assertTrue(ui.contains("Vagas disponíveis: ${'$'}{free ?: 0} ${'$'}availabilityLabel"))
        assertTrue(ui.contains("LOTADO"))
    }
}
