package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalTripCardRefresh0663Test {
    @Test
    fun everyViagensCardReceivesTheSameTargetedRefreshAction() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

        assertTrue(source.contains("refreshRunning0663: Boolean"))
        assertTrue(source.contains("onRefreshCard0663: () -> Unit"))
        assertEquals(
            2,
            Regex("""onRefreshCard0663 = \{ refreshRow0663\(row\) \}""").findAll(source).count(),
            "active and archived cards must both wire the same targeted refresh callback",
        )
        assertTrue(source.contains("text = if (refreshRunning0663) \"…\" else \"↻\""))
        assertTrue(source.contains("enabled = !refreshRunning0663"))
    }

    @Test
    fun blablacarCardUsesDirectTripOnlyHtmlAndNeverGlobalTraversal() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

        assertTrue(source.contains("CentralDayCommandBridge0552.refreshTripDirect0662("))
        assertTrue(source.contains("TRIPS_CARD_HTML_DIRECT_0663"))
        assertTrue(source.contains("scope=TRIP_ONLY singleCard=true fullTraversal=false"))
        assertTrue(source.contains("authority=HTML_DIRECT_0607"))
        assertFalse(source.contains("CentralDayCommandBridge0552.refreshAll("))
        assertFalse(source.contains("enqueueTripCollectorRefresh0517("))
    }

    @Test
    fun nativeRotaCertaCardStaysLocalAndExternalCardSelectsHtml() {
        assertEquals(
            OperationalTripCardRefreshMode0663.ROTA_CERTA_LOCAL,
            operationalTripCardRefreshMode0663(
                nativeRotaCerta = true,
                canonicalTripPresent = true,
            ),
        )
        assertEquals(
            OperationalTripCardRefreshMode0663.BLABLACAR_DIRECT_HTML,
            operationalTripCardRefreshMode0663(
                nativeRotaCerta = false,
                canonicalTripPresent = true,
            ),
        )
        assertEquals(
            OperationalTripCardRefreshMode0663.UNAVAILABLE,
            operationalTripCardRefreshMode0663(
                nativeRotaCerta = false,
                canonicalTripPresent = false,
            ),
        )
    }

    @Test
    fun refreshIsTrackedPerCanonicalTripRatherThanGlobally() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt").readText()

        assertTrue(source.contains("mutableStateMapOf<String, Boolean>()"))
        assertTrue(source.contains("refreshingTripIds0663[canonicalTripId0663] = true"))
        assertTrue(source.contains("refreshingTripIds0663.remove(canonicalTripId0663)"))
        assertTrue(source.contains("context.findComponentActivity0663()?.lifecycleScope"))
    }
}
