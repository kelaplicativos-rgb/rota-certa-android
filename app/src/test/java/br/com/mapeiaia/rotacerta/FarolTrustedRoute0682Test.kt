package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FarolTrustedRoute0682Test {
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

    @Test
    fun trustedDirectPrimaryBypassesAtlasAndCandidateGeocode() {
        val maps = source("GoogleMapsService.kt")
        val start = maps.indexOf("suspend fun trustedDirectDrivingDistancesFromAddressKm0682(")
        val end = maps.indexOf("private fun requestDrivingDistance(", start)
        assertTrue(start >= 0 && end > start)
        val block = maps.substring(start, end)
        assertTrue(block.contains("addressRouteMatrixBody(originAddress, destinations)"))
        assertTrue(block.contains("requestAddressRouteMatrix(body, apiKey, destinations.size)"))
        assertFalse(block.contains("cachedFarolCoordinate"))
        assertFalse(block.contains("resolveFreePrimaryOrigin0547"))
        assertFalse(block.contains("selectNearestGeocodeCandidate0547"))
        assertFalse(block.contains("OfflineAddressAtlas642"))
    }

    @Test
    fun ambiguousKeylessGeocodeIsRejectedInsteadOfChoosingNearestDriverTarget() {
        val maps = GoogleMapsService()
        val saoPaulo = Coordinate(-23.5505, -46.6333)
        val nearSamePlace = Coordinate(-23.5510, -46.6340)
        val rio = Coordinate(-22.9068, -43.1729)
        assertEquals(saoPaulo, maps.selectUnbiasedGeocodeCandidate0682(listOf(saoPaulo, nearSamePlace)))
        assertEquals(null, maps.selectUnbiasedGeocodeCandidate0682(listOf(saoPaulo, rio)))
    }
    @Test
    fun provisionalLocalResultCannotPublishGreenOrRed() {
        val live = source("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun applyUniversalPreliminaryColorStage637(")
        val end = live.indexOf("private suspend fun applyUniversalTwoAddressResultStage19(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("S682_PROVISIONAL_COLOR_SUPPRESSED"))
        assertTrue(block.contains("showOverlay(RadarColor.Default, distanceKm = null)"))
        assertFalse(block.contains("showOverlay(colorStage637"))
    }

    @Test
    fun finalDistanceOwnsColorDecision() {
        val engine = DecisionEngine()
        val settings = AppSettings(homeRadiusKm = 10.0, alternativeTargetEnabled = false)
        val fields = RideFields(destination = "Rua de teste, 100")
        val outside = engine.decideWorkRegion(
            fields = fields,
            settings = settings,
            fullText = "card",
            homeTargetActive = true,
            homeDistanceKm = 17.5,
            pinRoutes = emptyList(),
        )
        val inside = engine.decideWorkRegion(
            fields = fields,
            settings = settings,
            fullText = "card",
            homeTargetActive = true,
            homeDistanceKm = 4.8,
            pinRoutes = emptyList(),
        )
        assertEquals(Recommendation.OutsideRadius, outside.recommendation)
        assertEquals(Recommendation.GoodRide, inside.recommendation)
    }

    @Test
    fun decisionVisualExpiresAfterFiveSecondsAndIsBoundToCurrentCard() {
        val live = source("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("DECISION_VISUAL_TTL_MILLIS_0682 = 5_000L"))
        assertTrue(live.contains("expectedSignature0682 = universalActiveAddressSignature"))
        assertTrue(live.contains("expectedScreenGeneration0682 = universalScreenGeneration"))
        assertTrue(live.contains("expectedWindowGeneration0682 = universalWindowGeneration"))
        assertTrue(live.contains("FAROL_DECISION_VISUAL_EXPIRED_0682"))
    }

    @Test
    fun directRouteHasSubsecondBudgetBeforeLegacyFallback() {
        val live = source("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("TRUSTED_DIRECT_ROUTE_TIMEOUT_MILLIS_0682 = 950L"))
        assertTrue(live.contains("FAROL_TRUSTED_DIRECT_FALLBACK_0682"))
        assertTrue(live.contains("fallback=legacy_preserved"))
    }
}
