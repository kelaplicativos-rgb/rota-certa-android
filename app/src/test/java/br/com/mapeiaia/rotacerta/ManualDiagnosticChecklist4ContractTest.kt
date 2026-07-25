package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualDiagnosticChecklist4ContractTest {
    @Test
    fun `finalizador remove instrumentacao continua e a interface final baixa o relatorio`() {
        val historical = projectFile("diagnostics-manual-only-checklist-4.gradle.kts").readText()
        val main = projectFile("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()

        assertTrue(historical.contains("DiagnosticLogStore.record"))
        assertTrue(historical.contains("LiveFailureTraceStore.recordRead"))
        assertTrue(historical.contains("repository.saveDiagnostic"))
        assertTrue(historical.contains("diagnostics_off_checklist_4"))
        assertTrue(main.contains("Gerar e baixar relatorio"))
        assertTrue(main.contains("saveToDownloads(context, report)"))
        assertFalse(main.contains("Gerar e compartilhar relatorio"))
        assertFalse(main.contains("createAndShare(context, report)"))
    }

    @Test
    fun `relatorio e salvo em txt dentro de Downloads somente sob demanda`() {
        val builder = projectFile("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").readText()
        val exporter = projectFile("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportExporter.kt").readText()
        val gate = projectFile("src/main/java/br/com/mapeiaia/rotacerta/DiagnosticRuntimeGate.kt").readText()

        assertTrue(
            builder.contains("MANUAL_TECHNICAL_REPORT") ||
                builder.contains("RELATORIO TECNICO MANUAL"),
        )
        assertTrue(exporter.contains("rota-certa-relatorio-"))
        assertTrue(exporter.contains("fun saveToDownloads"))
        assertTrue(exporter.contains("MediaStore.Downloads.EXTERNAL_CONTENT_URI"))
        assertTrue(exporter.contains("Environment.DIRECTORY_DOWNLOADS"))
        assertFalse(exporter.contains("Intent.ACTION_SEND"))
        assertFalse(exporter.contains("createChooser"))
        assertTrue(gate.contains("ignora pedidos de"))
        assertFalse(gate.contains("private var enabled: Boolean = true"))
    }

    private fun projectFile(relativePath: String): File {
        val appDir = File(System.getProperty("user.dir"))
        val direct = File(appDir, relativePath)
        if (direct.exists()) return direct
        return File(appDir, "app/$relativePath")
    }
}
