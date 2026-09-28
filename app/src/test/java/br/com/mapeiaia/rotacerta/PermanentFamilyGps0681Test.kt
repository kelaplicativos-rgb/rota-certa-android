package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentFamilyGps0681Test {
    @Test
    fun permanentFamilyUrlUsesShortGpsSuffix() {
        val url = trackingFamilyPublicUrl0681(
            publicBaseUrl = "https://rota-certa-7ccc8.web.app",
            driverUsername = "Ezequiel",
        )
        assertEquals("https://rota-certa-7ccc8.web.app/ezequiel/gps", url)
    }

    @Test
    fun familyPinAlwaysHasSixDigits() {
        repeat(100) {
            val pin = secureFamilyPin0681()
            assertTrue(pin.matches(Regex("\\d{6}")))
        }
    }
}
