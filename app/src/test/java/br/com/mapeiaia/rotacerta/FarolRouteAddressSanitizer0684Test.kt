package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FarolRouteAddressSanitizer0684Test {
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
    fun cutsAcceptedRequestUiAfterRealStreet() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Rua Chuvas de Verão, 200 ( 1D Solicitação já aceita Paulo F -SP)",
        )
        assertTrue(result.accepted)
        assertEquals("Rua Chuvas de Verão, 200", result.sanitized)
        assertTrue(result.cuts.any { it.startsWith("operational_boundary:") })
        assertTrue(result.cuts.contains("dangling_parenthetical"))
    }

    @Test
    fun collapsesEquivalentStreetRepeatedByOcr() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Rua Eucalípto, 325 (Parque Sao Rafael) Rua Eucalípto, 325 (Parque Sao Rafael)",
        )
        assertTrue(result.accepted)
        assertEquals("Rua Eucalípto, 325 (Parque Sao Rafael)", result.sanitized)
        assertTrue(result.cuts.contains("equivalent_repeated_street_collapsed"))
    }

    @Test
    fun cutsPedidosDeViagemSuffix() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Rua Golfo da California, 57 - Pedidos de viagem",
        )
        assertTrue(result.accepted)
        assertEquals("Rua Golfo da California, 57", result.sanitized)
    }

    @Test
    fun cutsBrokenLocalityWhenUiStartsInsideOpenParenthesis() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Rua Juá Mirim, 402 (Chacara Pedidos de viagem Demanda Desempenho",
        )
        assertTrue(result.accepted)
        assertEquals("Rua Juá Mirim, 402", result.sanitized)
    }

    @Test
    fun rejectsTwoDifferentStreetAddressesInsideOneRouteDestination() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Rua Afonso Pena, 10 Rua Vergueiro, 20",
        )
        assertFalse(result.accepted)
        assertNull(result.sanitized)
        assertEquals("multiple_distinct_street_addresses", result.reason)
    }

    @Test
    fun cleanAddressPassesUnchanged() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Rua Vergueiro, 2000, São Paulo - SP",
        )
        assertTrue(result.accepted)
        assertEquals("Rua Vergueiro, 2000, São Paulo - SP", result.sanitized)
        assertFalse(result.changed)
    }

    @Test
    fun strongNamedPlaceWithLocalityRemainsRouteable() {
        val result = FarolRouteAddressSanitizer0684.sanitize(
            "Shopping ABC, Santo André - SP",
        )
        assertTrue(result.accepted)
        assertEquals("Shopping ABC, Santo André - SP", result.sanitized)
    }

    @Test
    fun equivalentAccessibilityAndOcrAddressesAreCompatibleForPriority() {
        assertTrue(
            FarolRouteAddressSanitizer0684.compatibleRouteAddress(
                "Avenida Paulista, 1000, São Paulo - SP",
                "Av. Paulista, 1000, São Paulo - SP",
            ),
        )
    }

    @Test
    fun serviceBlocksDirtyAddressBeforeCacheAndRouteAndKeepsAccessibilityPriority() {
        val live = source("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("S684_ROUTE_ADDRESS_REJECTED"))
        assertTrue(live.contains("S684_ROUTE_ADDRESS_APPROVED"))
        assertTrue(live.contains("destination = routeDestination0684"))
        assertTrue(live.contains("stage684AccessibilityRouteAddress"))
        assertTrue(live.contains("accessibilityPriorityApplied0684"))
        assertTrue(live.contains("FarolRouteAddressSanitizer0684.sanitize(originAddress)"))
        val fields = live.indexOf("destination = routeDestination0684")
        val cache = live.indexOf("googleMapsService.cachedOfflineFirstDrivingDistancesFromAddressKm0749(", fields)
        val trusted = live.indexOf("offlineFirstDrivingDistancesFromAddressKm0749(", fields)
        assertTrue(fields >= 0 && cache > fields && trusted > fields)
    }
}
