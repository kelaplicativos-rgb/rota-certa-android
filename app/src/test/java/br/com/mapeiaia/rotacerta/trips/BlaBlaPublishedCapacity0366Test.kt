package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaPublishedCapacity0366Test {
    private val profileA = "7371f028-9c55-4903-8444-308015823efd"
    private val profileB = "175a7068-50d8-40c3-a27a-214b9c6e0461"

    @Test
    fun derivedInventoryRemainsSeparateFromBlaBlaRemainingMetadata() {
        val resolved = timelinePublicCapacityResolution(entry(profileA, "trip-a", published = 3, blablaOccupied = 0))
        assertEquals(4, resolved.physicalVehicleCapacity)
        assertEquals(3, resolved.remotePublishedCapacity)
        assertEquals(4, resolved.effectiveCapacity)
        assertEquals(4, resolved.availableSeats)
        assertEquals("trip_operational_inventory", resolved.capacitySource)
    }

    @Test
    fun operationalAvailabilityUsesDerivedInventoryAndRealOccupancy() {
        assertEquals(1, timelinePublicCapacityResolution(entry(profileA, "trip-a", 3, 3)).availableSeats)
        assertEquals(2, timelinePublicCapacityResolution(entry(profileA, "trip-a", 3, 2)).availableSeats)
        assertEquals(4, timelinePublicCapacityResolution(entry(profileA, "trip-a", 3, 0)).effectiveCapacity)
    }

    @Test
    fun changingRemainingSeatsNeedsInventoryReconciliationBeforeChangingCapacity() {
        val before = entry(profileA, "trip-a", 4, 2)
        val after = before.copy(blablaPublishedSeats = 3)
        assertEquals(4, timelinePublicCapacityResolution(before).effectiveCapacity)
        assertEquals(4, timelinePublicCapacityResolution(after).effectiveCapacity)
        assertEquals(2, timelinePublicCapacityResolution(before).availableSeats)
        assertEquals(2, timelinePublicCapacityResolution(after).availableSeats)
    }

    @Test
    fun lastConfirmedPublishedMetadataSurvivesPartialReload() {
        val previous = collectorTrip(profileA, "trip-a", 3, 3, true)
        val partial = collectorTrip(profileA, "trip-a", null, 2, false)
        val merged = BlaBlaCollectorPassengerModule.mergeMonotonic(previous, partial)
        assertEquals(3, merged.published_seats)
        assertEquals(3, merged.booked_seats)
    }

    @Test
    fun channelAllocationThreePlusFourBuildsTotalSeven() {
        val breakdown = tripChannelAllocationBreakdown(
            physicalPassengerCapacity = 7,
            blablaPublishedSeats = 3,
            rotaCertaSeatAllocation = 4,
        )
        assertEquals(7, breakdown.physicalPassengerCapacity)
        assertEquals(3, breakdown.blablaPublishedAllocation)
        assertEquals(4, breakdown.rotaCertaAllocation)
        assertEquals(7, breakdown.totalConsidered)
    }

    @Test
    fun blablaFreeSeatsAreNotSubtractedByAlreadyConfirmedBlaBlaPassengers() {
        val trip = trip(capacity = 7, publishedSeats = 2, rotaCertaSeatAllocation = 4)
        val claims = listOf(
            booking("bb", trip, 3, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY, "bb"),
        )
        val summary = operationalSeatSummary(trip, claims)
        assertEquals(2, summary.blablaAvailableSeats)
        assertEquals(4, summary.rotaCertaAvailableSeats)
        assertEquals(6, summary.totalAvailableSeats)
        assertEquals(3, summary.confirmedPassengerSeats)
        assertEquals(6, summary.availableSeats)
        assertEquals(0, summary.overbookingSeats)
    }

    @Test
    fun sameOccupancyGroupIsNotCountedTwice() {
        val trip = trip(capacity = 7, publishedSeats = 3, rotaCertaSeatAllocation = 4)
        val claims = listOf(
            booking("external", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.EXTERNAL_OCCUPANCY, "same-reservation"),
            booking("local-mirror", trip, 1, BookingSource.ROTA_CERTA, CapacityClaimType.PASSENGER, "same-reservation"),
        )
        val summary = operationalSeatSummary(trip, claims)
        assertEquals(1, summary.confirmedPassengerSeats)
        assertEquals(4, summary.rotaCertaAvailableSeats)
        assertEquals(7, summary.availableSeats)
    }

    @Test
    fun remainingSeatCountDoesNotClampSegmentReuse() {
        val e = entry(profileA, "trip-a", published = 2, blablaOccupied = 0)
        val stops = trip(capacity = 4).stops
        val physicalLoads = listOf(
            SegmentLoad(stops[0], stops[1], occupiedSeats = 0, availableSeats = 4),
            SegmentLoad(stops[1], stops[2], occupiedSeats = 1, availableSeats = 3),
        )
        val publicLoads = timelinePublicSegmentLoads(e, physicalLoads)
        assertEquals(listOf(4, 3), publicLoads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun explicitZeroInventoryIsAValidZeroState() {
        val unresolved = entry(profileA, "trip-a", published = 3, blablaOccupied = 0).copy(capacity = 0)
        val resolved = timelinePublicCapacityResolution(unresolved)
        assertEquals(0, resolved.effectiveCapacity)
        assertEquals(0, resolved.availableSeats)
        assertEquals("trip_operational_inventory", resolved.capacitySource)
    }

    @Test
    fun accountsKeepPublishedMetadataIndependent() {
        val a = timelinePublicCapacityResolution(entry(profileA, "same-trip", 3, 1))
        val b = timelinePublicCapacityResolution(entry(profileB, "same-trip", 4, 1))
        assertEquals(3, a.remotePublishedCapacity)
        assertEquals(4, b.remotePublishedCapacity)
        assertEquals(4, a.effectiveCapacity)
        assertEquals(4, b.effectiveCapacity)
    }

    @Test
    fun publicAgendaSourceDerivesInventoryFromBlaBlaRemainingAndRotaCerta() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        assertFalse(source.contains("combinedAgendaAvailableSeats"))
        assertFalse(source.contains("rotaCertaSeatPool"))
        assertTrue(source.contains("derivedInventory"))
        assertTrue(source.contains("blablaAvailable"))
        assertTrue(source.contains("rotaCertaAvailable"))
        assertTrue(source.contains("totalAvailable"))
        assertTrue(source.contains("capacitySource=blablacar_remaining_plus_external_peak_plus_rota_certa"))
    }

    private fun entry(
        profile: String,
        tripId: String,
        published: Int?,
        blablaOccupied: Int,
        rotaOccupied: Int = 0,
    ): TripTimelineEntry {
        val sources = buildMap {
            if (blablaOccupied > 0) put(BookingSource.BLABLACAR, blablaOccupied)
            if (rotaOccupied > 0) put(BookingSource.ROTA_CERTA, rotaOccupied)
        }
        return TripTimelineEntry(
            tripId = "timeline:$profile:$tripId",
            profileId = profile,
            profileLabel = "Perfil",
            departureAtMillis = 1_800_000_000_000L,
            arrivalAtMillis = null,
            origin = "A",
            destination = "C",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            rotaCertaSeatAllocation = 4,
            minimumOccupiedSeats = blablaOccupied + rotaOccupied,
            maximumOccupiedSeats = blablaOccupied + rotaOccupied,
            sourcePassengerSeats = sources,
            blablaTripId = tripId,
            blablaProfileUuid = profile,
            blablaPublishedSeats = published,
            blablaPassengerRosterComplete = true,
        )
    }

    private fun collectorTrip(
        profile: String,
        tripId: String,
        published: Int?,
        occupied: Int,
        complete: Boolean,
    ) = BlaBlaCollectorTrip(
        profile_uuid = profile,
        date = "2026-09-04",
        departure_time = "10:30",
        actual_departure = "A",
        actual_arrival = "C",
        trip_id = tripId,
        trip_href = "https://www.blablacar.com.br/rides/offer/$tripId",
        passengers = (1..occupied).map { BlaBlaCollectorPassenger(name = "P$it") },
        booked_seats = occupied,
        published_seats = published,
        passenger_roster_complete = complete,
    )

    private fun trip(
        capacity: Int = 4,
        publishedSeats: Int? = null,
        rotaCertaSeatAllocation: Int? = null,
    ) = Trip(
        id = "trip",
        title = "A → C",
        departureAtMillis = 1_800_000_000_000L,
        capacity = capacity,
        rotaCertaSeatAllocation = rotaCertaSeatAllocation,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
        publishedSeats = publishedSeats,
    )

    private fun booking(
        id: String,
        trip: Trip,
        seats: Int,
        source: BookingSource,
        claimType: CapacityClaimType,
        group: String? = null,
    ) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = id,
        boardingStopId = "a",
        dropoffStopId = "c",
        seats = seats,
        status = BookingStatus.CONFIRMED,
        source = source,
        capacityClaimType = claimType,
        occupancyGroupId = group,
    )
}
