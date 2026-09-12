package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent regression contract for the sealed FAROL 0.1.547 baseline.
 *
 * Historical note: 0.1.547 legitimately uses a Haversine-style straight-line
 * calculation only to rank ambiguous geocoding candidates. That calculation
 * must never become the operational trip distance used by the green/red
 * recommendation. Operational distance remains road distance returned by OSRM.
 */
class FarolSealed0547RegressionContractTest {
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

    private fun block(source: String, startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("missing start marker: $startMarker", start >= 0)
        assertTrue("missing end marker: $endMarker", end > start)
        return source.substring(start, end)
    }

    @Test fun operationalCardDistanceRemainsOsrmRoadDistance() {
        val s = source("GoogleMapsService.kt")
        val freeRoute = block(
            s,
            "    private suspend fun requestOpenStreetMapAddressRoutes(",
            "    private suspend fun resolveFreePrimaryOrigin0547(",
        )
        assertTrue(freeRoute.contains("requestOsrmDrivingDistance(origin, destination)"))
        assertTrue(freeRoute.contains("OSM_PRIMARY_ROUTE_RESULT_0546"))
        assertFalse(freeRoute.contains("straightLineKm0547"))
        assertFalse(freeRoute.contains("haversine"))

        val osrm = block(
            s,
            "    private fun requestOsrmDrivingDistance(",
            "    private fun requestAddressRouteFallback(",
        )
        assertTrue(osrm.contains("/route/v1/driving/"))
        assertTrue(osrm.contains("root[\"routes\"]"))
        assertTrue(osrm.contains("get(\"distance\")"))
        assertTrue(osrm.contains("distanceMeters / 1000.0"))
        assertFalse(osrm.contains("straightLineKm0547"))
        assertFalse(osrm.contains("haversine"))
    }

    @Test fun straightLineCalculationIsConfinedToGeocodeCandidateRanking() {
        val s = source("GoogleMapsService.kt")
        val selector = block(
            s,
            "    internal fun selectNearestGeocodeCandidate0547(",
            "    private fun requestNominatimGeocode(",
        )
        assertTrue(selector.contains("straightLineKm0547(candidate, destination)"))
        assertTrue(selector.contains("val haversine ="))

        val addressDecisionPath = block(
            s,
            "    suspend fun drivingDistancesFromAddressKm(",
            "    fun cachedDrivingDistancesFromAddressKm(",
        )
        assertFalse(addressDecisionPath.contains("straightLineKm0547"))
        assertFalse(addressDecisionPath.contains("haversine"))
    }

    @Test fun freePrimaryAndGoogleContingencyOrderCannotFlip() {
        val s = source("GoogleMapsService.kt")
        val path = block(
            s,
            "    suspend fun drivingDistancesFromAddressKm(",
            "    fun cachedDrivingDistancesFromAddressKm(",
        )
        val osm = path.indexOf("requestOpenStreetMapAddressRoutes(")
        val google = path.indexOf("requestAddressRouteMatrix(")
        assertTrue(osm >= 0)
        assertTrue(google > osm)
        assertTrue(path.contains("unresolvedIndexes.isNotEmpty() && apiKey.isNotBlank()"))
        assertTrue(path.contains("ROUTE_PROVIDER_SELECTION_0546"))
    }

    @Test fun googleKeyCannotBecomeHardGateForReadingAgain() {
        val live = source("LiveRideAccessibilityService.kt")
        assertFalse(live.contains("if (apiKeyStage19.isBlank()) return"))
        assertFalse(live.contains("google_maps_api_required"))
        assertTrue(live.contains("googleMapsService.drivingDistancesFromAddressKm("))
    }
}
