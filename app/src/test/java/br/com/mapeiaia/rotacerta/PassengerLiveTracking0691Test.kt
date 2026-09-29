package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerLiveTracking0691Test {
    @Test
    fun passenger_link_is_live_only_forwardable_and_arrival_bounded() {
        assertEquals("PASSENGER_LIVE_ONLY_ARRIVAL_0691", PassengerLiveTracking0691.MARKER)
        assertEquals("LIVE_ONLY", PassengerLiveTracking0691.PUBLIC_MODE)
        assertEquals(3, PassengerLiveTracking0691.ARRIVAL_REQUIRED_FIXES)
        assertEquals(140, PassengerLiveTracking0691.ARRIVAL_RADIUS_METERS)
        assertTrue(PassengerLiveTracking0691.FAMILY_FORWARDING_ALLOWED)
        assertFalse(PassengerLiveTracking0691.PUBLIC_TRACE_ENABLED)
    }
}
