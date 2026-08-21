package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicIncidentMonitor0193Test {
    @Test
    fun `monitor nao armazena texto bruto nem cria loop artificial`() {
        val source = java.io.File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertTrue(source.contains("EVENT_STORM_THRESHOLD = 8"))
        assertTrue(!source.contains("Timer("))
        assertTrue(!source.contains("delay("))
        assertTrue(!source.contains("takeScreenshot"))
        assertTrue(!source.contains("writeText("))
    }

    @Test
    fun `resultado antigo e tempestade de eventos possuem detectores dedicados`() {
        val source = java.io.File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertTrue(source.contains("FORENSIC_EVENT_STORM_0193"))
        assertTrue(source.contains("FORENSIC_OCR_STORM_0193"))
        assertTrue(source.contains("FORENSIC_STALE_GENERATION_RESULT_0193"))
        assertTrue(source.contains("FORENSIC_STALE_WINDOW_RESULT_0193"))
        assertTrue(source.contains("FORENSIC_FINAL_COLOR_WITHOUT_DISTANCE_0193"))
    }
}
