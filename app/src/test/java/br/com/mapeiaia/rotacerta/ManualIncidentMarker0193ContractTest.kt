package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualIncidentMarker0193ContractTest {
    @Test
    fun `toque para gerar relatorio marca incidente antes do seletor de arquivo`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        assertEquals(2, Regex("FORENSIC_USER_INCIDENT_MARK_0193").findAll(source).count())
        assertEquals(2, Regex("source=manual_report_tap").findAll(source).count())
        val firstMarker = source.indexOf("FORENSIC_USER_INCIDENT_MARK_0193")
        val firstLaunch = source.indexOf("supportReportFileCreator.launch", firstMarker)
        assertTrue(firstMarker >= 0)
        assertTrue(firstLaunch > firstMarker)
    }
}
