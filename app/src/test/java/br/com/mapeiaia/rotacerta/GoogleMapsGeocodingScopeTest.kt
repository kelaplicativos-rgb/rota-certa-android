package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapsGeocodingScopeTest {
    private val service = GoogleMapsService()

    @Test
    fun addressWithoutCityDoesNotInventSaoPaulo() {
        val queries = service.geocodeQueries(
            query = "Rua das Flores, 100",
            region = DeviceRegion(country = "Brasil"),
        )

        assertEquals(listOf("Rua das Flores, 100, Brasil", "Rua das Flores, 100"), queries)
        assertFalse(queries.any { it.contains("São Paulo", ignoreCase = true) })
    }

    @Test
    fun detectedCityIsUsedBeforeNationalFallback() {
        val queries = service.geocodeQueries(
            query = "Rua das Flores, 100",
            region = DeviceRegion(city = "Foz do Iguaçu - PR", country = "Brasil"),
        )

        assertEquals("Rua das Flores, 100, Foz do Iguaçu - PR, Brasil", queries.first())
        assertTrue("Rua das Flores, 100, Brasil" in queries)
    }

    @Test
    fun explicitStateOrCepIsNotDuplicatedWithRegionCity() {
        val withState = service.geocodeQueries(
            query = "Rua das Flores, 100, Curitiba - PR",
            region = DeviceRegion(city = "São Paulo - SP", country = "Brasil"),
        )
        val withCep = service.geocodeQueries(
            query = "Rua das Flores, 100, 80000-000",
            region = DeviceRegion(city = "São Paulo - SP", country = "Brasil"),
        )

        assertFalse(withState.first().contains("São Paulo", ignoreCase = true))
        assertFalse(withCep.first().contains("São Paulo", ignoreCase = true))
    }

    @Test
    fun appSettingsStartWithManualAppsAndOptionalCards() {
        val settings = AppSettings()

        assertTrue(settings.restrictToSelectedRideApps)
        assertFalse(settings.requireRegisteredRideCard)
        assertFalse(settings.monitor99)
        assertFalse(settings.monitorUber)
        assertFalse(settings.monitorInDrive)
        assertTrue(settings.extraMonitoredPackages.isBlank())
    }
}
