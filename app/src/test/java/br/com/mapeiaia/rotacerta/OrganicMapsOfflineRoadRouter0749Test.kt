package br.com.mapeiaia.rotacerta

import app.organicmaps.sdk.util.Distance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganicMapsOfflineRoadRouter0749Test {
    @Test fun convertsOrganicMapsDistanceUnitsToKm() {
        assertEquals(
            2.5,
            OrganicMapsOfflineRoadRouter0749.distanceToKm(Distance(2500.0, "2500", 0.toByte()))!!,
            0.000001,
        )
        assertEquals(
            2.5,
            OrganicMapsOfflineRoadRouter0749.distanceToKm(Distance(2.5, "2.5", 1.toByte()))!!,
            0.000001,
        )
        assertEquals(
            1.609344,
            OrganicMapsOfflineRoadRouter0749.distanceToKm(Distance(1.0, "1", 3.toByte()))!!,
            0.000001,
        )
    }

    @Test fun routeDistanceMustRemainPlausibleAgainstStraightLine() {
        assertTrue(OrganicMapsOfflineRoadRouter0749.plausibleAgainstStraightLine(2.9, 5.5))
        assertFalse(OrganicMapsOfflineRoadRouter0749.plausibleAgainstStraightLine(10.0, 1.0))
        assertFalse(OrganicMapsOfflineRoadRouter0749.plausibleAgainstStraightLine(2.0, 100.0))
    }

    @Test fun routeCacheKeyIsDirectionalAndStable() {
        val a = Coordinate(-23.5505, -46.6333)
        val b = Coordinate(-23.6000, -46.5500)
        assertEquals(
            OrganicMapsOfflineRoadRouter0749.cacheKey(a, b),
            OrganicMapsOfflineRoadRouter0749.cacheKey(a, b),
        )
        assertNotEquals(
            OrganicMapsOfflineRoadRouter0749.cacheKey(a, b),
            OrganicMapsOfflineRoadRouter0749.cacheKey(b, a),
        )
    }
}
