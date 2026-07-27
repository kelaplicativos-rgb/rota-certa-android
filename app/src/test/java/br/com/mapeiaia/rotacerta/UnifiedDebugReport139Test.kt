package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class UnifiedDebugReport139Test {
    @Test
    fun interface_has_one_report_generator_and_debug_toggle() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        assertContains(source, "Log de depuração")
        assertContains(source, "Gerar relatório para depuração")
        assertContains(source, "Eventos unificados")
        assertFalse(source.contains("Gerar e baixar relatorio"))
    }

    @Test
    fun service_records_events_before_filters() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertContains(source, "ACCESSIBILITY_EVENT")
        assertContains(source, "DiagnosticRuntimeGate.setEnabled(DebugLogPreferenceStore.isEnabled")
    }
}
