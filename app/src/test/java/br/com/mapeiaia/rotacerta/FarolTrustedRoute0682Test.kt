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
    fun localResultKeepsHaversineKmPrivateBeforeRoadRefinement() {
        val live = source("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private suspend fun applyUniversalPreliminaryColorStage637(")
        val end = live.indexOf("private suspend fun applyUniversalTwoAddressResultStage19(", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("FarolRoadKmFinality0713.LOCAL_KM_SUPPRESSED_MARKER"))
        assertTrue(block.contains("DistanceAuthority.LOCAL_HAVERSINE"))
        assertTrue(block.contains("applyUniversalTwoAddressResultStage19("))
        assertFalse(block.contains("FarolLocalDecisionAuthority0696.LOCAL_COMMIT_MARKER"))
    }

    @Test
    fun localDistanceOwnsColorDecision() {
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
    fun trustedRouteRemainsButVisualTtlWasRemovedBy0683CardAuthority() {
        val live = source("LiveRideAccessibilityService.kt")
        assertFalse(live.contains("DECISION_VISUAL_TTL_MILLIS_0682"))
        assertFalse(live.contains("scheduleDecisionBubbleExpiry0682"))
        assertFalse(live.contains("FAROL_DECISION_VISUAL_EXPIRED_0682"))
        assertTrue(live.contains("TRUSTED_DIRECT_ROUTE_TIMEOUT_MILLIS_0682 = 950L"))
    }

    @Test
    fun roadRefinementHasSubsecondBudgetOnlyAfterLocalAuthority() {
        val live = source("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("TRUSTED_DIRECT_ROUTE_TIMEOUT_MILLIS_0682 = 950L"))
        val analyzeStart = live.indexOf("private suspend fun analyzeUniversalTwoAddressStage19(")
        val analyzeEnd = live.indexOf("private fun attachExactRoadDistanceStage637(", analyzeStart)
        assertTrue(analyzeStart >= 0 && analyzeEnd > analyzeStart)
        val block = live.substring(analyzeStart, analyzeEnd)
        val localIndex = block.indexOf("localDistancesFromAddressKm(")
        val localCommitIndex = block.indexOf("applyUniversalPreliminaryColorStage637(")
        val remoteIndex = block.indexOf("offlineFirstDrivingDistancesFromAddressKm0749(")
        assertTrue(localIndex >= 0)
        assertTrue(localCommitIndex > localIndex)
        assertTrue(remoteIndex > localCommitIndex)
        assertTrue(block.contains("FarolLocalDecisionAuthority0696.REMOTE_STARTED_MARKER"))
        val offlineRouter = source("OrganicMapsOfflineRoadRouter0749.kt")
        assertTrue(offlineRouter.contains("TOTAL_OFFLINE_BUDGET_MS = 350L"))
        assertTrue(offlineRouter.contains("FAROL_GOOGLE_ROAD_FALLBACK_0749"))
    }
}
