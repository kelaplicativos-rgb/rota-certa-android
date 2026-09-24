package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupPostPassHold0192ContractTest {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun `visual nulo depois da passagem preserva popup ate timeout ou botao`() {
        val idleHide = overlay.substringAfter("fun hideFromEngineIdle()").substringBefore("fun hide()")
        assertTrue(idleHide.contains("if (container != null)"))
        assertTrue(idleHide.contains("ALERT_OVERLAY_ENGINE_IDLE_PINNED_0647"))
        assertTrue(idleHide.contains("return"))
        assertFalse(idleHide.contains("hide()"))

        val visualCallback = service.substringAfter("onVisual = { visual ->").substringBefore("} else {")
        assertTrue(visualCallback.contains("directionalAlertOverlayChecklist5.hideFromEngineIdle()"))
        assertFalse(visualCallback.contains("directionalAlertOverlayChecklist5.hide()"))
    }

    @Test
    fun `ultrapassar ponto nao cria temporizador curto nem reinicia vinte segundos`() {
        assertTrue(overlay.contains("ALERT_OVERLAY_PASSED_PINNED_0647"))
        assertTrue(overlay.contains("auto_close_on_pass=false"))
        assertTrue(overlay.contains("const val ALERT_TIMEOUT_MILLIS_0647 = 20_000L"))
        assertFalse(overlay.contains("PASSED_CLOSE_DELAY_MILLIS"))
    }

    @Test
    fun `fechamentos explicitos externos continuam imediatos`() {
        assertTrue(overlay.contains("fun hide()"))
        assertTrue(overlay.contains("cancelActiveTimeout0647(reason = \"EXPLICIT_HIDE\")"))
        assertTrue(service.contains("if (!AlertRuntimePolicy0644.isEnabled(currentSettings))"))
        assertTrue(service.contains("directionalAlertOverlayChecklist5.hide()"))
    }

    @Test
    fun `novo target substitui antigo com novo prazo mas update gps nao reinicia`() {
        val show = overlay.substringAfter("fun showOrUpdate(").substringBefore("fun hideFromEngineIdle()")
        assertTrue(show.contains("val targetChanged0647 = activeTargetId != visual.targetId"))
        assertTrue(show.contains("cancelActiveTimeout0647(reason = \"TARGET_REPLACED\")"))
        assertTrue(show.contains("if (targetChanged0647 || activeTimeout0647 == null)"))
    }
}
