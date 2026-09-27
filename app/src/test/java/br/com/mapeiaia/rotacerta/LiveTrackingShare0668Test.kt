package br.com.mapeiaia.rotacerta

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTrackingShare0668Test {
    @Test
    fun tokenIsStrongUrlSafeAndNotPredictable() {
        val first = secureTrackingToken0668()
        val second = secureTrackingToken0668()
        assertTrue(first.length >= 43)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(first.contains("="))
        assertFalse(first == second)
        assertEquals(32, Base64.getUrlDecoder().decode(first).size)
    }

    @Test
    fun publicLinkKeepsTokenInFragmentNotQueryOrPath() {
        val token = secureTrackingToken0668()
        val url = trackingSharePublicUrl0668("https://rota-certa-7ccc8.web.app/", token)
        assertEquals("https://rota-certa-7ccc8.web.app/tracking.html#$token", url)
        assertFalse(url.substringBefore('#').contains(token))
        assertFalse(url.contains("?token="))
    }

    @Test
    fun passengerExpiryIsBoundedAndAllowsArrivalGrace() {
        val now = 1_800_000_000_000L
        assertEquals(now + 30L * 60L * 1000L, passengerTrackingExpiry0668(now - 5L * 60L * 60L * 1000L, now))
        assertEquals(now + 8L * 60L * 60L * 1000L, passengerTrackingExpiry0668(now + 6L * 60L * 60L * 1000L, now))
        assertEquals(now + 24L * 60L * 60L * 1000L, passengerTrackingExpiry0668(now + 30L * 60L * 60L * 1000L, now))
    }
}