package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage47Reliability0251Test {
    @Test
    fun automaticPublishedSeatLookupIsEnabledForAgendaAvailability() {
        assertTrue(BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP)
    }

    @Test
    fun legacyVehicleCapacityIsIgnoredAndChannelInventoryDrivesTimelineEntries() {
        val external = entry(id = "external", capacity = 99, rosterComplete = true).copy(blablaPublishedSeats = 3)
        val second = entry(id = "local", capacity = 77, rosterComplete = true).copy(blablaPublishedSeats = 2)
        val updated = applyConfiguredVehicleCapacity(
            entries = listOf(external, second),
            vehicleCapacity = 999,
            rotaCertaSeatAllocation = 2,
        )
        assertEquals(5, updated[0].capacity)
        assertEquals(4, updated[1].capacity)
    }

    @Test
    fun legacyVehicleCapacityCannotInventInventoryWithoutChannelEvidence() {
        val external = entry(id = "external", capacity = 8, rosterComplete = true)
        assertEquals(
            0,
            applyConfiguredVehicleCapacity(
                entries = listOf(external),
                vehicleCapacity = 999,
                rotaCertaSeatAllocation = 0,
            ).single().capacity,
        )
    }

    @Test
    fun knownInventoryDoesNotPretendZeroReservationsWhenRosterIsIncomplete() {
        val external = entry(id = "external", capacity = 3, rosterComplete = false).copy(
            blablaPublishedSeats = 3,
            rotaCertaSeatAllocation = 0,
        )
        assertEquals(
            TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING,
            timelineOccupancyReadState(external),
        )
    }

    @Test
    fun completeEmptyRosterWithKnownInventoryCanShowZeroOccupied() {
        val external = entry(id = "external", capacity = 3, rosterComplete = true).copy(
            blablaPublishedSeats = 3,
            rotaCertaSeatAllocation = 0,
        )
        assertEquals(TimelineOccupancyReadState.CAPACITY_CONFIGURED, timelineOccupancyReadState(external))
    }

    private fun entry(
        id: String,
        capacity: Int,
        rosterComplete: Boolean,
    ): TripTimelineEntry = TripTimelineEntry(
        tripId = id,
        profileId = "profile",
        profileLabel = "Driver",
        departureAtMillis = 1_000L,
        arrivalAtMillis = 2_000L,
        origin = "Origin",
        destination = "Destination",
        status = TripStatus.PUBLISHED,
        capacity = capacity,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        sourcePassengerSeats = emptyMap(),
        blablaTripId = "external-$id",
        blablaPassengerRosterComplete = rosterComplete,
    )
}
