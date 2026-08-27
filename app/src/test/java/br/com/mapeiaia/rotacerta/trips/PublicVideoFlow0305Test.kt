package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicVideoFlow0305Test {
    @Test
    fun onlineSettingsCarryOnlyConfiguredPublicDriverProfile() {
        val settings = TripOnlineSettings(
            apiBaseUrl = "https://example.test",
            publicBaseUrl = "https://example.test",
            driverToken = "secret",
            publicCalendarToken = "abcdefghijklmnop",
            driverDisplayName = "Motorista",
            driverUsername = "motorista",
            driverWhatsapp = "(11) 99999-9999",
            driverPhotoUrl = "https://example.test/driver.jpg",
            driverPublicAbout = "Viagens com conforto.",
            driverPublicRating = "4,9",
            driverPublicReviewCount = 123,
            driverPublicBadge = "Motorista verificado",
            vehicleMakeModel = "Veículo de teste",
            vehicleColor = "Prata",
            vehicleAmenities = "Ar-condicionado; USB",
            driverPreferences = "Não fumar",
            paymentInstructions = "Pix ou dinheiro no carro",
        )

        assertTrue(settings.configured)
        assertEquals("(11) 99999-9999", settings.driverWhatsapp)
        assertEquals("https://example.test/driver.jpg", settings.driverPhotoUrl)
        assertEquals("4,9", settings.driverPublicRating)
        assertEquals(123, settings.driverPublicReviewCount)
        assertEquals("Pix ou dinheiro no carro", settings.paymentInstructions)
    }
}
