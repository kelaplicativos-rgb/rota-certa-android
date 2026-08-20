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
    fun `popup permanece tres segundos depois que o ponto foi ultrapassado`() {
        assertTrue(overlay.contains("if (visual.shouldClose)"))
        assertTrue(overlay.contains("handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)"))
        assertTrue(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
        assertFalse(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 750L"))
    }

    @Test
    fun `fechar manualmente silencia radar ou alerta somente na aproximacao atual`() {
        assertTrue(
            service.contains(
                "onDismiss = { directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId) }",
            ),
        )
        assertTrue(directionalEngine.contains("fun dismissUntilExit("))
        assertTrue(directionalEngine.contains("mutedUntilExit"))
        assertTrue(directionalEngine.contains("resetAfterExit"))
        assertTrue(directionalEngine.contains("RESET_BUFFER_METERS"))
    }

    @Test
    fun `alerta salvo do fluxo legado tambem respeita fechar ate sair da zona`() {
        assertTrue(
            service.contains(
                "onDismiss = { proximityAlertEngine.dismissSavedPlaceUntilExit(alert.id) }",
            ),
        )
    }
}
