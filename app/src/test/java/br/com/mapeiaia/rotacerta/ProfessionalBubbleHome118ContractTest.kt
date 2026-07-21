package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalBubbleHome118ContractTest {
    private fun mainSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
    ).firstOrNull(File::exists)?.readText() ?: error("MainActivity.kt nao encontrado")

    @Test
    fun circularControlDashboardIsNoLongerRenderedOnHome() {
        val source = mainSource()

        assertTrue("Base profissional ausente", "professional_bubble_home_0_1_118" in source)
        assertTrue("Contrato popup-only ausente", "popup_only_control_center_0_1_119" in source)
        assertFalse(
            "A Home ainda conecta o estado selecionado ao dashboard circular",
            "selectedGroup = selectedBubbleGroup" in source,
        )
        assertFalse(
            "A Home ainda entrega callback de selecao para a grade circular",
            "onSelectGroup = { group ->" in source,
        )

        val accessStart = source.indexOf("            BUBBLE_GROUP_ACCESS -> {")
        val accessEnd = source.indexOf("            BUBBLE_GROUP_BACKUP ->", accessStart)
        assertTrue("Grupo Permissoes precisa existir", accessStart >= 0 && accessEnd > accessStart)
        val accessBlock = source.substring(accessStart, accessEnd)
        assertTrue("Leitura precisa ficar dentro de Permissoes", "LiveReadingCard(" in accessBlock)
        assertTrue("Localizacao precisa ficar dentro de Permissoes", "AlwaysLocationPermissionCard(" in accessBlock)
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
