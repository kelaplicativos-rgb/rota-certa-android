package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBubbleHomeContractTest {
    @Test
    fun bubbleCenterUsesLogicalGroupsAndImmediateActions() {
        val sourceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        ).firstOrNull(File::exists) ?: error("MainActivity.kt nao encontrado")

        val source = sourceFile.readText()
        assertTrue("Central profissional precisa existir", "professional_bubble_home_0_1_118" in source)
        assertTrue("Estado do grupo precisa existir", "selectedBubbleGroup" in source)

        listOf(
            "Text(\"Operacao\"",
            "Text(\"Sistema\"",
            "Text(\"Registros\"",
            "Text(\"Acoes rapidas\"",
            "Text(\"Suporte\"",
        ).forEach { marker -> assertTrue("Secao ausente: $marker", marker in source) }

        listOf(
            "ProfessionalBubbleItem(\"⚡\", \"Rota\"",
            "ProfessionalBubbleItem(\"🏠\", \"Destino\"",
            "ProfessionalBubbleItem(\"⚠️\", \"Alertas\"",
            "ProfessionalBubbleItem(\"🎨\", \"Aparencia\"",
            "ProfessionalBubbleItem(\"🔐\", \"Permissoes\"",
            "ProfessionalBubbleItem(\"💾\", \"Backup\"",
            "ProfessionalBubbleItem(\"📋\", \"Relatorios\"",
        ).forEach { marker -> assertTrue("Grupo principal ausente: $marker", marker in source) }

        listOf(
            "ProfessionalBubbleItem(\"🟢\", \"WhatsApp\"",
            "ProfessionalBubbleItem(\"🧹\", \"Limpar\"",
            "ProfessionalBubbleItem(\"🛠️\", \"Depurar\"",
            "ProfessionalBubbleItem(\"⏹️\", \"Encerrar\"",
        ).forEach { marker -> assertTrue("Acao imediata ausente: $marker", marker in source) }

        assertFalse("Leitura nao pode ser grupo principal", "\"Leitura\" to BUBBLE_GROUP_READING" in source)
        assertFalse("Ferramentas nao pode ser grupo principal", "\"Ferramentas\" to BUBBLE_GROUP_TOOLS" in source)
        assertFalse("Nao pode existir bolinha Mais duplicada", "AppControlBubble(\"Mais\"" in source)
        assertFalse("Bolinhas nao devem quebrar ON/OFF", "label + \"\\n\" + if (active)" in source)
        assertFalse("Grupo nao pode exigir segundo toque em Abrir", "Text(if (expanded) \"Fechar\" else \"Abrir\")" in source)
    }
}
