package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupTimingTelemetry0193ContractTest {
    @Test
    fun `popup mede timeout de vinte segundos botoes e toque nao acionavel com relogio monotonico`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
        assertTrue(source.contains("android.os.SystemClock.elapsedRealtimeNanos()"))
        assertTrue(source.contains("ALERT_OVERLAY_TIMEOUT_STARTED_0647"))
        assertTrue(source.contains("ALERT_OVERLAY_TIMEOUT_DISMISSED_0647"))
        assertTrue(source.contains("ALERT_OVERLAY_TIMEOUT_CANCELLED_0647"))
        assertTrue(source.contains("ALERT_OVERLAY_BUTTON_" + "$" + "{action}_0647"))
        assertTrue(source.contains("ALERT_OVERLAY_NON_ACTION_TOUCH_IGNORED_0647"))
        assertTrue(source.contains("FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193"))
        assertTrue(source.contains("expected_ms=" + "$" + "ALERT_TIMEOUT_MILLIS_0647"))
        assertTrue(source.contains("EARLY_TIMEOUT_TOLERANCE_MILLIS_0647 = 150L"))
    }
}
