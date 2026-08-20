package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupPostPassHold0192ContractTest {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun `visual nulo depois da passagem nao cancela fechamento de tres segundos`() {
        val idleHide = overlay
            .substringAfter("fun hideFromEngineIdle()")
            .substringBefore("fun hide()")
        assertTrue(idleHide.contains("if (pendingClose != null) {"))
        assertTrue(idleHide.contains("ALERT_OVERLAY_ENGINE_IDLE_PRESERVED_0193"))
        assertTrue(idleHide.contains("return"))
        assertTrue(idleHide.contains("hide()"))

        val visualCallback = service
            .substringAfter("onVisual = { visual ->")
            .substringBefore("} else {")
        assertTrue(visualCallback.contains("directionalAlertOverlayChecklist5.hideFromEngineIdle()"))
        assertFalse(visualCallback.contains("directionalAlertOverlayChecklist5.hide()"))
    }

    @Test
    fun `temporizador pos passagem continua em tres segundos`() {
        assertTrue(overlay.contains("handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)"))
        assertTrue(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
    }

    @Test
    fun `fechamentos explicitos continuam imediatos`() {
        assertTrue(overlay.contains("fun hide()"))
        assertTrue(overlay.contains("cancelPendingClose()"))
        assertTrue(service.contains("if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled)"))
        assertTrue(service.contains("directionalAlertOverlayChecklist5.hide()"))
    }

    @Test
    fun `novo visual ainda substitui o fechamento pendente anterior`() {
        val show = overlay
            .substringAfter("fun showOrUpdate(")
            .substringBefore("fun hideFromEngineIdle()")
        assertTrue(show.contains("cancelPendingClose()"))
        assertTrue(show.contains("activeTargetId = visual.targetId"))
    }
}
