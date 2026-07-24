package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCacheSafety127Test {
    @Test
    fun failedRouteIsNotStoredForFourteenDays() {
        val settings = AppSettings(
            homeCoordinate = Coordinate(-23.5505, -46.6333),
            homeRadiusKm = 5.0,
        )
        val key = LiveRideRouteCache.keyFor(
            fields = RideFields(destination = "Rua sem rota retornada"),
            settings = settings,
        )
        val cache = LiveRideRouteCache()

        cache.put(
            key,
            LiveRideRouteCache.CachedRoute(
                destinationCoordinate = Coordinate(-23.5600, -46.6400),
                homeCoordinate = settings.homeCoordinate,
                alternativeCoordinate = null,
                homeDistanceKm = null,
                alternativeDistanceKm = null,
            ),
        )

        assertNull("Falha de rede nao pode envenenar o cache", cache.get(key))
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun oneExactConfiguredDistanceIsEnoughToCache() {
        val settings = AppSettings(
            alternativeTargetEnabled = true,
            alternativeCoordinate = Coordinate(-23.5400, -46.6200),
            alternativeRadiusKm = 8.0,
        )
        val key = LiveRideRouteCache.keyFor(
            fields = RideFields(destination = "Destino exato"),
            settings = settings,
        )
        val cache = LiveRideRouteCache()

        cache.put(
            key,
            LiveRideRouteCache.CachedRoute(
                destinationCoordinate = Coordinate(-23.5600, -46.6400),
                homeCoordinate = null,
                alternativeCoordinate = settings.alternativeCoordinate,
                homeDistanceKm = null,
                alternativeDistanceKm = 4.25,
            ),
        )

        assertNotNull(cache.get(key))
        assertEquals(4.25, cache.get(key)?.alternativeDistanceKm ?: 0.0, 0.0001)
    }

    @Test
    fun validCacheIsAppliedBeforeYellowPaint() {
        val service = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        ).firstOrNull(File::exists)?.readText()
            ?: error("LiveRideAccessibilityService.kt nao encontrado")

        val processStart = service.indexOf("private suspend fun processRideText(")
        val processEnd = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", processStart)
        val region = service.substring(processStart, processEnd)
        val cacheCheck = region.indexOf("cachedDrivingDistancesFromAddressKm(")
        val cacheApply = region.indexOf("exact_cache_before_yellow_checklist_13")
        val yellowPaint = region.indexOf("showOverlay(RadarColor.Default, distanceKm = null)")

        assertTrue("Cache exato precisa ser consultado no novo destino", cacheCheck >= 0)
        assertTrue("Aplicacao do cache precisa existir", cacheApply > cacheCheck)
        assertTrue("Cache deve retornar antes da pintura amarela", cacheApply < yellowPaint)
        assertTrue("Aplicacao do cache precisa usar a validacao normal", "applyUniversalTwoAddressResult(" in region)
    }
}
