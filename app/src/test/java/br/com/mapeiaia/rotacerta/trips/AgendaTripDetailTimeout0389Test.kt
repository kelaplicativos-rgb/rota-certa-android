package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaTripDetailTimeout0389Test {
    @Test
    fun onlyTripDetailGetsThePhysicalStallWatchdog() {
        assertEquals(
            BLABLA_TRIP_DETAIL_CAPTURE_TIMEOUT_MS_0389,
            blaBlaDynamicCollectionTimeoutMs0389(BlaBlaBrowserRequest.TRIP_DETAIL),
        )
        assertEquals(10_000L, BLABLA_TRIP_DETAIL_CAPTURE_TIMEOUT_MS_0389)
        BlaBlaBrowserRequest.values()
            .filterNot { it == BlaBlaBrowserRequest.TRIP_DETAIL }
            .forEach { request ->
                assertEquals(0L, blaBlaDynamicCollectionTimeoutMs0389(request), request.name)
            }
    }

    @Test
    fun dynamicCollectorActuallyPassesTheWatchdogToTheExistingOrchestrator() {
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val evaluateStart = dynamic.indexOf("private inline fun <reified T> evaluateRequest(")
        val completeStart = dynamic.indexOf("private fun completeSync(", evaluateStart)
        assertTrue(evaluateStart >= 0)
        assertTrue(completeStart > evaluateStart)
        val evaluateBlock = dynamic.substring(evaluateStart, completeStart)
        assertTrue(evaluateBlock.contains("executeCollectionStep("))
        assertTrue(evaluateBlock.contains("timeoutMs = blaBlaDynamicCollectionTimeoutMs0389(request)"))
    }

    @Test
    fun orchestratorTimeoutRemainsFailClosedAndLateCallbackCannotWin() {
        val orchestrator = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaBrowserOrchestrator.kt").readText()
        assertTrue(orchestrator.contains("if (timeoutMs > 0L)"))
        assertTrue(orchestrator.contains("if (completed || !isCurrent(token, currentContext())) return@postDelayed"))
        assertTrue(orchestrator.contains("BROWSER_REQUEST_TIMEOUT"))
        assertTrue(orchestrator.contains("finish(token)"))
        assertTrue(orchestrator.contains("callback(null)"))
        assertTrue(orchestrator.contains("if (completed) return@evaluateJavascript"))
    }

    @Test
    fun nullTripDetailReleasesInFlightAndQuarantinesOnlyThatCard() {
        val dynamic = File("src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt").readText()
        val captureStart = dynamic.indexOf("private fun captureTripDetail(")
        val saveStart = dynamic.indexOf("private fun savePendingTrip(", captureStart).let { if (it > captureStart) it else dynamic.indexOf("private fun ", captureStart + 30) }
        assertTrue(captureStart >= 0)
        val captureBlock = dynamic.substring(captureStart, if (saveStart > captureStart) saveStart else dynamic.length)
        assertTrue(captureBlock.contains("detailCaptureInFlight = false"))
        assertTrue(captureBlock.contains("if (result == null)"))
        assertTrue(captureBlock.contains("reason=detail_dom_unreadable"))
        assertTrue(captureBlock.contains("advanceCandidate(expectedSync, expectedCandidate)"))

        val quarantineStart = dynamic.indexOf("private fun blockCurrentCard(")
        val quarantineEnd = dynamic.indexOf("private fun quarantineReasonLabel", quarantineStart)
        val quarantineBlock = dynamic.substring(quarantineStart, quarantineEnd)
        assertTrue(quarantineBlock.contains("CARD_TRAVERSAL_QUARANTINED"))
        assertTrue(quarantineBlock.contains("enterBrowserPhase(Phase.RIDES, BlaBlaBrowserRequest.RIDE_LIST"))
        assertTrue(quarantineBlock.contains("loadTrackedUrl(RIDES_URL)"))
        assertFalse(quarantineBlock.contains("finish()"))
    }
}
