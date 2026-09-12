package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingGeocodeResilience0547ContractTest {
    private fun source(relative: String): String = File(System.getProperty("user.dir"), relative).readText()

    @Test
    fun malformed_parenthesis_from_indrive_is_normalized_without_losing_city_or_state() {
        val service = GoogleMapsService()
        val queries = service.geocodeQueries0547(
            "Rua Flores da Primavera, 263 (Conjunto Promorar Rio Claro, São Paulo - SP",
        )

        assertTrue(queries.isNotEmpty())
        assertEquals(
            "Rua Flores da Primavera, 263, Conjunto Promorar Rio Claro, São Paulo - SP",
            queries.first(),
        )
        assertTrue(queries.contains("Rua Flores da Primavera, 263, São Paulo - SP"))
        assertTrue(queries.all { !it.contains('(') && !it.contains(')') })
        assertTrue(queries.all { it.contains("São Paulo - SP") })
    }

    @Test
    fun candidate_disambiguation_prefers_coordinate_nearest_configured_work_target() {
        val service = GoogleMapsService()
        val target = Coordinate(latitude = -23.5954123, longitude = -46.4797131)
        val far = Coordinate(latitude = -22.90, longitude = -43.20)
        val near = Coordinate(latitude = -23.6000, longitude = -46.4800)

        assertEquals(
            near,
            service.selectNearestGeocodeCandidate0547(listOf(far, near), listOf(target)),
        )
    }

    @Test
    fun resolver_chain_keeps_osm_primary_and_android_geocoder_keyless_fallback() {
        val text = source("src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt")
        assertTrue(text.contains("resolveFreePrimaryOrigin0547(originAddress, destinations)"))
        assertTrue(text.contains("requestNominatimGeocodeCandidates0547"))
        assertTrue(text.contains("platformGeocodingService0547"))
        assertTrue(text.contains("GEOCODE_RESOLUTION_START_0547"))
        assertTrue(text.contains("GEOCODE_ANDROID_FALLBACK_0547"))
        assertTrue(text.contains("GEOCODE_RESOLUTION_FAILED_0547"))
        assertTrue(text.contains("limit=5&countrycodes=br"))
    }

    @Test
    fun no_protected_reading_decision_or_proximity_runtime_is_rewritten_by_0547() {
        val liveRide = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        assertTrue(liveRide.contains("decideFastWorkRegionChecklist13"))
        assertTrue(liveRide.contains("MANUAL_READING_RUNTIME_STAGE43"))
        assertFalse(liveRide.contains("GEOCODE_RESOLUTION_START_0547"))
    }

    @Test
    fun version_is_0547() {
        val build = source("build.gradle.kts")
        assertTrue(build.contains("versionCode = 5839"))
        assertTrue(build.contains("versionName = \"0.1.547\""))
    }
}
