package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaCaptureOrchestrator0368Test {
    @Test
    fun browserRequestsDeclareReadNavigationAndRemoteWriteBoundaries() {
        assertEquals(BlaBlaBrowserOperation.REMOTE_WRITE, BlaBlaBrowserRequest.SEAT_CHANGE.operation)
        assertEquals(BlaBlaBrowserOperation.REMOTE_WRITE, BlaBlaBrowserRequest.SEAT_SAVE.operation)
        assertEquals(BlaBlaBrowserOperation.CAPTURE, BlaBlaBrowserRequest.SEAT_OPTIONS.operation)
        assertEquals(BlaBlaBrowserOperation.CAPTURE, BlaBlaBrowserRequest.TRIP_DETAIL.operation)
        assertEquals(BlaBlaBrowserOperation.CAPTURE, BlaBlaBrowserRequest.PASSENGER_CONTACT.operation)
        assertEquals(BlaBlaBrowserOperation.NAVIGATION, BlaBlaBrowserRequest.TRIP_OPEN.operation)
        assertEquals(BlaBlaBrowserOperation.NAVIGATION, BlaBlaBrowserRequest.PASSENGER_OPEN.operation)
        assertTrue(BlaBlaBrowserRequest.SEAT_OPTIONS.isCollectionStep)
        assertFalse(BlaBlaBrowserRequest.SEAT_CHANGE.isCollectionStep)
        assertTrue(BlaBlaBrowserRequest.SEAT_CHANGE.mutatesRemoteState)
    }

    @Test
    fun registeredAssetScriptsHaveOneRuntimeOwner() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta/trips")
        val runtimeOwners = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("BlaBlaBrowserScriptRegistry(context)") }
            .map { it.name }
            .toList()
        assertEquals(listOf("BlaBlaBrowserOrchestrator.kt"), runtimeOwners)
    }

    @Test
    fun authoritativeCollectorsCannotExecuteRegisteredScriptsDirectly() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta/trips")
        val dynamic = File(root, "BlaBlaDynamicAccounts.kt").readText()
        val publicSearch = File(root, "BlaBlaPublicSearchActivity.kt").readText()
        val seat = File(root, "BlaBlaSeatBrowserController.kt").readText()
        val legacyHarvest = File(root, "BlaBlaManualSeatAutomation.kt").readText()
        val blockedCancel = File(root, "BlaBlaBlockedPassengerCancellation.kt").readText()

        assertFalse(dynamic.contains("BlaBlaBrowserScriptRegistry"))
        assertFalse(publicSearch.contains("BlaBlaBrowserScriptRegistry"))
        assertFalse(seat.contains("BlaBlaBrowserScriptRegistry"))
        assertFalse(dynamic.contains("webView.evaluateJavascript(script)"))
        assertFalse(publicSearch.contains("evaluateJavascript(script)"))
        assertFalse(seat.contains("evaluateJavascript"))
        assertFalse(legacyHarvest.contains("webView.evaluateJavascript(script)"))
        assertFalse(blockedCancel.contains("Phase.VERIFY -> view.evaluateJavascript"))

        assertTrue(dynamic.contains("executeCollectionStep"))
        assertTrue(publicSearch.contains("executeCollectionStep"))
        assertTrue(seat.contains("executeCollectionStep"))
        assertTrue(legacyHarvest.contains("executeCollectionScript"))
        assertTrue(blockedCancel.contains("blocked_passenger_cancel_verify"))
    }

    @Test
    fun networkFirstEvidenceIsInstalledByTheSameOrchestrator() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta/trips")
        val dynamic = File(root, "BlaBlaDynamicAccounts.kt").readText()
        val orchestrator = File(root, "BlaBlaBrowserOrchestrator.kt").readText()
        assertTrue(dynamic.contains("browserOrchestrator.installNetworkEvidenceCapture"))
        assertFalse(dynamic.contains("networkDiagnosticRecorder = BlaBlaNetworkDiagnosticRecorder("))
        assertTrue(orchestrator.contains("fun installNetworkEvidenceCapture("))
        assertTrue(orchestrator.contains("recorder.install(webView)"))
    }

    @Test
    fun normalTripCollectionHasNoRemoteWritePath() {
        val root = File("src/main/java/br/com/mapeiaia/rotacerta/trips")
        val dynamic = File(root, "BlaBlaDynamicAccounts.kt").readText()
        val publicSearch = File(root, "BlaBlaPublicSearchActivity.kt").readText()
        val orchestrator = File(root, "BlaBlaBrowserOrchestrator.kt").readText()
        assertFalse(dynamic.contains("executeRemoteWrite("))
        assertFalse(publicSearch.contains("executeRemoteWrite("))
        assertTrue(orchestrator.contains("collection_cannot_remote_write"))
        assertTrue(orchestrator.contains("remote_write_requires_explicit_write_request"))
    }

    @Test
    fun futureRequestAssetsRemainUnique() {
        val duplicateAssets = BlaBlaBrowserRequest.values()
            .groupBy { it.assetName }
            .filterValues { it.size > 1 }
        assertTrue(duplicateAssets.isEmpty(), "Duplicate request assets: " + duplicateAssets.keys)
    }
}
