package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentLocationCore0680Test {
    @Test
    fun familyShareRemainsActiveUntilExplicitRevocation() {
        val now = 2_000_000L
        val family = TrackingShareLocal0668(
            token = "family-token",
            scope = TrackingShareScope0668.FAMILY,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_500L,
            active = true,
        )
        assertTrue(PersistentTrackingPolicy0680.isShareActive(family, now))
        assertFalse(PersistentTrackingPolicy0680.isShareActive(family.copy(active = false), now))
        assertTrue(PersistentTrackingPolicy0680.familyExpiryMillis() > 4_102_444_800_000L)
    }

    @Test
    fun passengerShareRemainsTimeBounded() {
        val now = 2_000_000L
        val passenger = TrackingShareLocal0668(
            token = "passenger-token",
            scope = TrackingShareScope0668.PASSENGER,
            createdAtMillis = 1_000L,
            expiresAtMillis = now - 1L,
            active = true,
        )
        assertFalse(PersistentTrackingPolicy0680.isShareActive(passenger, now))
        assertTrue(PersistentTrackingPolicy0680.isShareActive(passenger.copy(expiresAtMillis = now + 1L), now))
    }
}
