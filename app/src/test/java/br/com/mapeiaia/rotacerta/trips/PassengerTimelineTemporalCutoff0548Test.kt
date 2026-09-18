package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerTimelineTemporalCutoff0548Test {
    private val now = 100_000_000L

    @Test fun tripThatJustArrivedRemainsVisibleForOperationalGrace() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now - 20_000L, now - 1L, now))
    }

    @Test fun tripDisappearsOnlyAfterArrivalPlusOneHour() {
        val arrival = now - OPERATIONAL_TRIP_ARRIVAL_GRACE_MILLIS_0577 - 1L
        val departure = arrival - 2L * 60L * 60L * 1000L
        assertFalse(isPassengerTimelineCurrentOrUpcoming0548(departure, arrival, now))
    }

    @Test fun ongoingTripRemainsVisibleUntilAndAfterArrivalBoundary() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now - 20_000L, now + 20_000L, now))
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now - 20_000L, now, now))
    }

    @Test fun futureTripRemainsVisible() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now + 20_000L, now + 40_000L, now))
    }

    @Test fun missingArrivalNeverHidesAtDeparture() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now - 1L, null, now))
        assertFalse(
            isPassengerTimelineCurrentOrUpcoming0548(
                now - OPERATIONAL_TRIP_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577 - 1L,
                null,
                now,
            ),
        )
    }

    @Test fun invalidArrivalUsesSafeOperationalRetention() {
        assertTrue(isPassengerTimelineCurrentOrUpcoming0548(now - 1L, 1L, now))
        assertFalse(
            isPassengerTimelineCurrentOrUpcoming0548(
                now - OPERATIONAL_TRIP_UNKNOWN_ARRIVAL_RETENTION_MILLIS_0577 - 1L,
                1L,
                now,
            ),
        )
    }
}
