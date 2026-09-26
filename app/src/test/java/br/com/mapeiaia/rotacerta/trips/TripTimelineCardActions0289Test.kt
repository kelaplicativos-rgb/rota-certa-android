package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineCardActions0289Test {
    @Test
    fun directionLabelsKeepGpsOriginContract() {
        assertEquals("↑ IDA", timelineDirectionDisplayLabel(TimelineDirectionState.OUTBOUND))
        assertEquals("↓ VOLTA", timelineDirectionDisplayLabel(TimelineDirectionState.INBOUND))
    }

    @Test
    fun externalCardWithoutPassengersStillHasDirectActionEvidence() {
        val entry = TripTimelineEntry(
            tripId = "timeline-trip",
            profileId = "7371f028-9c55-4903-8444-308015823efd",
            profileLabel = "Perfil",
            departureAtMillis = 1_800_000_000_000L,
            arrivalAtMillis = null,
            origin = "Santo André",
            destination = "Três Corações",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            blablaTripId = "trip-123",
            blablaTripHref = "https://www.blablacar.com.br/rides/offer/trip-123",
            blablaProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            blablaPassengers = emptyList(),
        )

        assertTrue(entry.blablaPassengers.isEmpty())
        assertTrue(hasExternalTripActionEvidence(entry))
        assertNotNull(externalTripTarget(entry.blablaProfileUuid, entry.blablaTripHref))
        assertNotNull(BlaBlaReliableSeatSyncBridge.targetForTimeline(entry))
    }
}
