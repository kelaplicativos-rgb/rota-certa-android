package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerTimelineTemporalCutoff0548Test {
    private val now = 1_000_000L

    @Test fun endedTripIsHiddenFromOperationalTimeline() {
        assertFalse(isPassengerTimelineCurrentOrUpcoming0548(now - 20_000L, now - 1L, now))
    }

    @Test fun ongoingTripRemainsVisibleUntilArrival() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now - 20_000L, now + 20_000L, now))
    }

    @Test fun futureTripRemainsVisible() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now + 20_000L, now + 40_000L, now))
    }

    @Test fun exactArrivalBoundaryIsStillVisible() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now - 20_000L, now, now))
    }

    @Test fun missingArrivalFallsBackToDeparture() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now + 1L, null, now))
        assertFalse(isPassengerTimelineCurrentOrUpcoming0548(now - 1L, null, now))
    }

    @Test fun invalidArrivalFallsBackToDepartureWithoutInventingDuration() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now + 1L, 1L, now))
        assertFalse(isPassengerTimelineCurrentOrUpcoming0548(now - 1L, 1L, now))
    }
}
