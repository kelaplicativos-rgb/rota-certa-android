package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRideRouteCachePersistenceTest {
    @Test
    fun exactRouteSurvivesCacheRecreation() {
        var now = 10_000L
        val settings = AppSettings(
            homeAddress = "Rua Lateral, 14",
            homeRadiusKm = 10.0,
            homeCoordinate = Coordinate(-23.59543, -46.47970),
        )
        val fields = RideFields(destination = "Rua Acacio Antunes")
        val key = LiveRideRouteCache.keyFor(fields, settings)
        val original = LiveRideRouteCache(nowMillis = { now })
        original.put(
            key,
            LiveRideRouteCache.CachedRoute(
                destinationCoordinate = Coordinate(-23.60000, -46.49000),
                homeCoordinate = settings.homeCoordinate,
                alternativeCoordinate = null,
                homeDistanceKm = 3.922,
                alternativeDistanceKm = null,
            ),
        )

        val payload = original.exportSnapshot()
        assertTrue(payload.isNotBlank())

        now += 2_000L
        val restored = LiveRideRouteCache(nowMillis = { now })
        assertEquals(1, restored.importSnapshot(payload))
        val cached = restored.get(key)

        assertNotNull(cached)
        assertEquals(3.922, cached?.homeDistanceKm ?: 0.0, 0.0001)
        assertEquals(2_000L, cached?.ageMillis)
    }

    @Test
    fun expiredRouteIsNotRestored() {
        var now = 1_000L
        val settings = AppSettings(homeAddress = "Casa", homeCoordinate = Coordinate(-23.5, -46.4))
        val key = LiveRideRouteCache.keyFor(RideFields(destination = "Destino"), settings)
        val original = LiveRideRouteCache(nowMillis = { now })
        original.put(
            key,
            LiveRideRouteCache.CachedRoute(
                destinationCoordinate = Coordinate(-23.6, -46.5),
                homeCoordinate = settings.homeCoordinate,
                alternativeCoordinate = null,
                homeDistanceKm = 8.0,
                alternativeDistanceKm = null,
            ),
        )
        val payload = original.exportSnapshot()

        now += LiveRideRouteCache.ROUTE_CACHE_TTL_MILLIS + 1L
        val restored = LiveRideRouteCache(nowMillis = { now })
        assertEquals(0, restored.importSnapshot(payload))
        assertNull(restored.get(key))
    }

    @Test
    fun changingRadiusCreatesDifferentOperationalCacheKey() {
        val fields = RideFields(destination = "Rua Acacio Antunes")
        val radiusFive = AppSettings(homeAddress = "Casa", homeRadiusKm = 5.0)
        val radiusTen = radiusFive.copy(homeRadiusKm = 10.0)

        val keyFive = LiveRideRouteCache.keyFor(fields, radiusFive)
        val keyTen = LiveRideRouteCache.keyFor(fields, radiusTen)

        assertTrue(keyFive != keyTen)
    }
}
