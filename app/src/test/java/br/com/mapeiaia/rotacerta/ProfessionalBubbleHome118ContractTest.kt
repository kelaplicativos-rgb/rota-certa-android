package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalBubbleHome118ContractTest {
    private fun mainSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
    ).firstOrNull(File::exists)?.readText() ?: error("MainActivity.kt nao encontrado")

    @Test
    fun operationalContentRemainsAvailableAfterPopupTransfer() {
        val source = mainSource()

        assertTrue("Grupo Permissoes precisa continuar disponivel", "BUBBLE_GROUP_ACCESS -> {" in source)
        assertTrue("Leitura precisa continuar disponivel", "LiveReadingCard(" in source)
        assertTrue("Localizacao precisa continuar disponivel", "AlwaysLocationPermissionCard(" in source)
        assertTrue("Backup precisa continuar disponivel", "BUBBLE_GROUP_BACKUP ->" in source)
        assertTrue("Relatorios precisam continuar disponiveis", "BUBBLE_GROUP_REPORTS ->" in source)
    }

    @Test
    fun diagnosticCanBeRequestedDirectlyFromPopup() {
        val source = mainSource()

        assertTrue("Exportacao precisa usar todos os eventos retidos", "DiagnosticLogStore.dump()" in source)
        assertTrue("Titulo da linha do tempo ausente", "LINHA DO TEMPO COMPLETA DA EXECUCAO" in source)
        assertTrue("Inicio da sessao precisa ser registrado", "app.session.started" in source)
        assertTrue("Inicio da exportacao precisa ser registrado", "report.export.started" in source)
        assertTrue("Fim da exportacao precisa ser registrado", "report.export.completed" in source)
        assertTrue("Pedido vindo do popup precisa ser reconhecido", "auto_export_report_0_1_119" in source)
        assertTrue("Arquivo padrao do relatorio ausente", "rota-certa-relatorio-completo.txt" in source)
    }
}
