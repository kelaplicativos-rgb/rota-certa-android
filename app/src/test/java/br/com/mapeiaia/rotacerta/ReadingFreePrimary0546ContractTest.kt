package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFreePrimary0546ContractTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test fun addressRouteUsesFreeRoadProviderBeforeGoogleFallback() {
        val s = source("GoogleMapsService.kt")
        val start = s.indexOf("    suspend fun drivingDistancesFromAddressKm(")
        val end = s.indexOf("    fun cachedDrivingDistancesFromAddressKm(", start)
        assertTrue(start >= 0 && end > start)
        val block = s.substring(start, end)
        val osm = block.indexOf("requestOpenStreetMapAddressRoutes(")
        val google = block.indexOf("requestAddressRouteMatrix(")
        assertTrue(osm >= 0)
        assertTrue(google > osm)
        assertTrue(block.contains("ROUTE_PROVIDER_SELECTION_0546"))
        assertFalse(block.contains("destinations.isEmpty() || apiKey.isBlank()"))
    }

    @Test fun freeProviderProducesRoadDistanceNotHaversineApproximation() {
        val s = source("GoogleMapsService.kt")
        assertTrue(s.contains("https://nominatim.openstreetmap.org/search"))
        assertTrue(s.contains("https://router.project-osrm.org"))
        assertTrue(s.contains("/route/v1/driving/"))
        assertTrue(s.contains("OSM_PRIMARY_ROUTE_RESULT_0546"))
        assertFalse(s.contains("haversine"))
    }

    @Test fun readingNoLongerRequiresGoogleKeyToStartDistanceResolution() {
        val s = source("LiveRideAccessibilityService.kt")
        assertFalse(s.contains("if (apiKeyStage19.isBlank()) return"))
        assertFalse(s.contains("google_maps_api_required"))
        assertTrue(s.contains("googleMapsService.drivingDistancesFromAddressKm("))
    }
}
