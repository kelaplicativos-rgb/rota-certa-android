package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Stage47Reliability0251Test {
    @Test
    fun automaticPublishedSeatLookupIsDisabled() {
        assertFalse(BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP)
    }

    @Test
    fun configuredVehicleCapacityIsPhysicalAuthorityForAllTimelineEntries() {
        val external = entry(id = "external", capacity = 0, rosterComplete = true)
        val staleOrExternal = entry(id = "local", capacity = 6, rosterComplete = true)
        val updated = applyConfiguredVehicleCapacity(listOf(external, staleOrExternal), 4)
        assertEquals(4, updated[0].capacity)
        assertEquals(4, updated[1].capacity)
    }

    @Test
    fun invalidConfiguredVehicleCapacityDoesNotInventCapacity() {
        val external = entry(id = "external", capacity = 0, rosterComplete = true)
        assertEquals(0, applyConfiguredVehicleCapacity(listOf(external), 0).single().capacity)
    }

    @Test
    fun configuredCapacityDoesNotPretendZeroReservationsWhenRosterIsIncomplete() {
        val external = entry(id = "external", capacity = 4, rosterComplete = false)
        assertEquals(
            TimelineOccupancyReadState.CAPACITY_CONFIGURED_ROSTER_PENDING,
            timelineOccupancyReadState(external),
        )
    }

    @Test
    fun completeEmptyRosterWithConfiguredCapacityCanShowZeroOccupied() {
        val external = entry(id = "external", capacity = 4, rosterComplete = true)
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
