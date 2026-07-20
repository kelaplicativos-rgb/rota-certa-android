package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBubbleImmediateStateContractTest {
    @Test
    fun professionalGroupSelectionChangesComposeStateImmediately() {
        val sourceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        ).firstOrNull(File::exists) ?: error("MainActivity.kt nao encontrado")

        val source = sourceFile.readText()
        assertTrue("Estado de grupo precisa existir", "grouped_bubble_state_0_1_115" in source)
        assertTrue(
            "Toque precisa atualizar o grupo selecionado imediatamente",
            "selectedBubbleGroup = group" in source,
        )
        assertTrue(
            "Destino precisa abrir o grupo de endereco e raio",
            "BUBBLE_GROUP_DESTINATION -> TAB_ANALYSIS" in source,
        )
        assertTrue(
            "Relatorios precisam abrir o historico agrupado",
            "BUBBLE_GROUP_REPORTS -> TAB_HISTORY" in source,
        )
        assertTrue(
            "Demais grupos precisam abrir configuracao filtrada",
            "else -> TAB_CONFIG" in source,
        )
        assertFalse(
            "Ferramentas nao pode possuir navegacao propria",
            "BUBBLE_GROUP_TOOLS -> TAB_TOOLS" in source,
        )
        assertTrue(
            "WhatsApp precisa executar imediatamente",
            "onOpenWhatsApp" in source && "home.action whatsapp" in source,
        )
        assertTrue(
            "Encerrar precisa executar imediatamente",
            "onStopApplication" in source && "home.action stop_application" in source,
        )
        assertFalse(
            "Central nao deve alternar configuracao pelo estado antigo",
            "QuickBubbleToggleReducer.toggle(bubbleControlSettings, toggle)" in source,
        )
        assertFalse(
            "Estado antigo da grade ON/OFF nao deve permanecer ativo",
            "settings = bubbleControlSettings," in source,
        )
    }
}
