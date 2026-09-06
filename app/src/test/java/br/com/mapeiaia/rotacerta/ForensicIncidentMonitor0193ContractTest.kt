package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicIncidentMonitor0193ContractTest {
    private val recorder = File("src/main/java/br/com/mapeiaia/rotacerta/FarolFlightRecorder0163.kt").readText()
    private val report = File("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()

    @Test
    fun `gravador existente alimenta monitor sem segundo logger`() {
        assertTrue(recorder.contains("ForensicIncidentMonitor0193.observe(stage, packageName, details)"))
        assertFalse(recorder.contains("ForensicIncidentMonitor0193.write"))
    }

    @Test
    fun `relatorio manual marca momento exato da reclamacao`() {
        assertEquals(1, Regex("FarolFlightRecorder0163\\.exportReport").findAll(report).count())
        assertEquals(1, Regex("ForensicIncidentMonitor0193\\.markManualReport\\(\\)").findAll(report).count())
        assertTrue(report.indexOf("ForensicIncidentMonitor0193.markManualReport()") < report.indexOf("FarolFlightRecorder0163.exportReport"))
    }

    @Test
    fun `popup registra agendamento e preservacao do fechamento de tres segundos`() {
        assertTrue(overlay.contains("ALERT_OVERLAY_POST_PASS_SCHEDULED_0193"))
        assertTrue(overlay.contains("ALERT_OVERLAY_ENGINE_IDLE_PRESERVED_0193"))
        assertTrue(overlay.contains("PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
    }
}
