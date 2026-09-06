package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDebugReport139Test {
    @Test
    fun interface_has_one_report_generator_and_debug_toggle() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        assertTrue(source.contains("Log de depuração"))
        assertTrue(source.contains("Gerar relatório para depuração"))
        assertTrue(source.contains("EVENTOS UNIFICADOS"))
        assertFalse(source.contains("Gerar e baixar relatorio"))
    }

    @Test
    fun service_records_events_before_filters() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertTrue(source.contains("ACCESSIBILITY_EVENT"))
        assertTrue(source.contains("DiagnosticRuntimeGate.setEnabled(DebugLogPreferenceStore.isEnabled"))
    }
}
