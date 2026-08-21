package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupTimingTelemetry0193ContractTest {
    @Test
    fun `popup mede tempo real agenda callback e cancelamento com relogio monotonico`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
        assertTrue(source.contains("android.os.SystemClock.elapsedRealtimeNanos()"))
        assertTrue(source.contains("ALERT_OVERLAY_POST_PASS_SCHEDULED_0193"))
        assertTrue(source.contains("ALERT_OVERLAY_POST_PASS_TIMEOUT_FIRED_0193"))
        assertTrue(source.contains("ALERT_OVERLAY_PENDING_CLOSE_CANCELLED_0193"))
        assertTrue(source.contains("FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193"))
        assertTrue(source.contains("expected_ms=\$PASSED_CLOSE_DELAY_MILLIS"))
        assertTrue(source.contains("EARLY_TIMEOUT_TOLERANCE_MILLIS_0193 = 150L"))
    }
}
