package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupLifecycle0190ContractTest {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val directionalEngine = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt").readText()

    @Test
    fun `popup usa uma unica janela de vinte segundos desde a primeira exibicao`() {
        assertTrue(overlay.contains("const val ALERT_TIMEOUT_MILLIS_0647 = 20_000L"))
        assertTrue(overlay.contains("scheduleActiveTimeout0647(visual.targetId)"))
        assertTrue(overlay.contains("targetChanged0647 || activeTimeout0647 == null"))
        assertTrue(overlay.contains("timeout_not_restarted=true"))
        assertFalse(overlay.contains("PASSED_CLOSE_DELAY_MILLIS"))
        assertFalse(overlay.contains("handler.postDelayed(close,"))
    }

    @Test
    fun `fechar manualmente silencia radar ou alerta somente na aproximacao atual`() {
        assertTrue(service.contains("onDismiss = { directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId) }"))
        assertTrue(directionalEngine.contains("fun dismissUntilExit("))
        assertTrue(directionalEngine.contains("mutedUntilExit"))
        assertTrue(directionalEngine.contains("resetAfterExit"))
        assertTrue(directionalEngine.contains("RESET_BUFFER_METERS"))
    }

    @Test
    fun `alerta salvo do fluxo legado tambem respeita fechar ate sair da zona`() {
        assertTrue(service.contains("onDismiss = { proximityAlertEngine.dismissSavedPlaceUntilExit(alert.id) }"))
    }
}
