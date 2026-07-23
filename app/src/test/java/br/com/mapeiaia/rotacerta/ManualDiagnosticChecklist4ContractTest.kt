package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualDiagnosticChecklist4ContractTest {
    @Test
    fun `finalizador remove instrumentacao continua do servico materializado`() {
        val source = projectFile("diagnostics-manual-only-checklist-4.gradle.kts").readText()

        assertTrue(source.contains("DiagnosticLogStore.record"))
        assertTrue(source.contains("LiveFailureTraceStore.recordRead"))
        assertTrue(source.contains("repository.saveDiagnostic"))
        assertTrue(source.contains("diagnostics_off_checklist_4"))
        assertTrue(source.contains("Gerar e compartilhar relatorio"))
    }

    @Test
    fun `relatorio e criado em txt somente sob demanda`() {
        val builder = projectFile("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").readText()
        val exporter = projectFile("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportExporter.kt").readText()
        val gate = projectFile("src/main/java/br/com/mapeiaia/rotacerta/DiagnosticRuntimeGate.kt").readText()

        assertTrue(builder.contains("Logs continuos: DESATIVADOS"))
        assertTrue(exporter.contains("rota-certa-relatorio-"))
        assertTrue(exporter.contains("Intent.ACTION_SEND"))
        assertTrue(gate.contains("ignora pedidos de"))
        assertFalse(gate.contains("private var enabled: Boolean = true"))
    }

    private fun projectFile(relativePath: String): File {
        val appDir = File(System.getProperty("user.dir"))
        return File(appDir, relativePath).takeIf { it.exists() }
            ?: File(appDir, "app/$relativePath")
    }
}
