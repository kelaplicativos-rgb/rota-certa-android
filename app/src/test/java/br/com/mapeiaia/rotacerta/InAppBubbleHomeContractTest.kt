package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBubbleHomeContractTest {
    @Test
    fun bubbleCenterUsesNineGroupsWithoutDuplicatedSettingsCards() {
        val sourceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        ).firstOrNull(File::exists) ?: error("MainActivity.kt nao encontrado")

        val source = sourceFile.readText()
        assertTrue("Central precisa existir", "Central de bolinhas" in source)
        assertTrue("Navegacao agrupada precisa existir", "grouped_bubble_navigation_0_1_115" in source)
        assertTrue("Estado do grupo precisa existir", "selectedBubbleGroup" in source)
        assertTrue("Grupo Rota precisa existir", "\"Rota\" to BUBBLE_GROUP_GENERAL" in source)
        assertTrue("Grupo Leitura precisa existir", "\"Leitura\" to BUBBLE_GROUP_READING" in source)
        assertTrue("Grupo Destino precisa existir", "\"Destino\" to BUBBLE_GROUP_DESTINATION" in source)
        assertTrue("Grupo Alertas precisa existir", "\"Alertas\" to BUBBLE_GROUP_ALERTS" in source)
        assertTrue("Grupo Aparencia precisa existir", "\"Aparencia\" to BUBBLE_GROUP_APPEARANCE" in source)
        assertTrue("Grupo Permissoes precisa existir", "\"Permissoes\" to BUBBLE_GROUP_ACCESS" in source)
        assertTrue("Grupo Relatorios precisa existir", "\"Relatorios\" to BUBBLE_GROUP_REPORTS" in source)
        assertTrue("Grupo Backup precisa existir", "\"Backup\" to BUBBLE_GROUP_BACKUP" in source)
        assertTrue("Grupo Ferramentas precisa existir", "\"Ferramentas\" to BUBBLE_GROUP_TOOLS" in source)
        assertTrue("Cartoes do grupo devem abrir direto", "grouped_card_always_open_0_1_115" in source)
        assertTrue("Barra de abas deve estar eliminada", "bottomBar = {}" in source)
        assertFalse("Nao pode existir bolinha Mais duplicada", "AppControlBubble(\"Mais\"" in source)
        assertFalse("Bolinhas nao devem quebrar ON/OFF em varias linhas", "label + \"\\n\" + if (active)" in source)
        assertFalse("Grupo nao pode exigir segundo toque em Abrir", "Text(if (expanded) \"Fechar\" else \"Abrir\")" in source)
    }
}
