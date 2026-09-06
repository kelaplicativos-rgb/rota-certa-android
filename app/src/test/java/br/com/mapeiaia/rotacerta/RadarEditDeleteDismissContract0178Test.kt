package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarEditDeleteDismissContract0178Test {
    private val engine = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val repository = File("src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt").readText()
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val radarImport = File("src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt").readText()

    @Test
    fun closeMutesOnlyCurrentApproachAndExitReleasesIt() {
        assertTrue(engine.contains("fun dismissUntilExit(targetId: String)"))
        assertTrue(engine.contains("dismissGate0178.isDismissed(key)"))
        assertTrue(engine.contains("dismissGate0178.clearAfterExit(key)"))
        assertTrue(service.contains("onDismiss = { directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId) }"))
    }

    @Test
    fun radarPopupOffersEditDeleteAndClose() {
        assertTrue(overlay.contains("onEditRadar"))
        assertTrue(overlay.contains("onDeleteRadar"))
        assertTrue(service.contains("openImportedRadarEditor0178"))
        assertTrue(service.contains("repository.removeImportedRadar"))
    }

    @Test
    fun repositoryAndHomeSupportOneRadarWithoutRenderingTheWholeDatabase() {
        assertTrue(repository.contains("suspend fun updateImportedRadar"))
        assertTrue(repository.contains("suspend fun removeImportedRadar"))
        assertTrue(main.contains("highlightedImportedRadarId0178"))
        assertTrue(main.contains("EXTRA_IMPORTED_RADAR_ID_0178"))
        assertTrue(radarImport.contains("val highlightedRadar = radars.firstOrNull"))
    }
}
