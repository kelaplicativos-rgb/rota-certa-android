package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalBubbleHome118ContractTest {
    @Test
    fun homeSeparatesGroupsAndImmediateActionsProfessionally() {
        val source = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        ).firstOrNull(File::exists)?.readText() ?: error("MainActivity.kt nao encontrado")

        assertTrue("Painel profissional ausente", "professional_bubble_home_0_1_118" in source)
        listOf(
            "Text(\"Operacao\"",
            "Text(\"Sistema\"",
            "Text(\"Registros\"",
            "Text(\"Acoes rapidas\"",
            "Text(\"Suporte\"",
        ).forEach { marker -> assertTrue("Secao ausente: $marker", marker in source) }

        listOf(
            "ProfessionalBubbleItem(\"🟢\", \"WhatsApp\"",
            "ProfessionalBubbleItem(\"🚗\", \"Coletor\"",
            "ProfessionalBubbleItem(\"🧹\", \"Limpar\"",
            "ProfessionalBubbleItem(\"🛠️\", \"Depurar\"",
            "ProfessionalBubbleItem(\"⏹️\", \"Encerrar\"",
        ).forEach { marker -> assertTrue("Acao em bolinha ausente: $marker", marker in source) }

        assertFalse("Ferramentas nao pode continuar como grupo principal", "\"Ferramentas\" to BUBBLE_GROUP_TOOLS" in source)
        assertFalse("Leitura nao pode continuar como grupo principal", "\"Leitura\" to BUBBLE_GROUP_READING" in source)

        val accessStart = source.indexOf("            BUBBLE_GROUP_ACCESS -> {")
        val accessEnd = source.indexOf("            BUBBLE_GROUP_BACKUP ->", accessStart)
        assertTrue("Grupo Permissoes precisa existir", accessStart >= 0 && accessEnd > accessStart)
        val accessBlock = source.substring(accessStart, accessEnd)
        assertTrue("Leitura precisa ficar dentro de Permissoes", "LiveReadingCard(" in accessBlock)
        assertTrue("Localizacao precisa ficar dentro de Permissoes", "AlwaysLocationPermissionCard(" in accessBlock)
        assertTrue("Titulo profissional de Permissoes ausente", "Permissoes, leitura e GPS" in source)
    }

    @Test
    fun debugReportExportsFullInMemoryTimeline() {
        val source = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        ).firstOrNull(File::exists)?.readText() ?: error("MainActivity.kt nao encontrado")

        assertTrue("Exportacao precisa usar todos os eventos retidos", "DiagnosticLogStore.dump()" in source)
        assertTrue("Titulo da linha do tempo ausente", "LINHA DO TEMPO COMPLETA DA EXECUCAO" in source)
        assertTrue("Inicio da sessao precisa ser registrado", "app.session.started" in source)
        assertTrue("Inicio da exportacao precisa ser registrado", "report.export.started" in source)
        assertTrue("Fim da exportacao precisa ser registrado", "report.export.completed" in source)
    }
}
