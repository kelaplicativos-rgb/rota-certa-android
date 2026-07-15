package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBubbleHomeContractTest {
    @Test
    fun bubbleCenterIsVisibleWithoutAccessibilityAndWithoutTabs() {
        val sourceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        ).firstOrNull(File::exists) ?: error("MainActivity.kt nao encontrado")

        val source = sourceFile.readText()
        assertTrue("Central precisa existir", "Central de bolinhas" in source)
        assertTrue("Central precisa explicar Acessibilidade OFF", "Acessibilidade OFF: toque em Acesso OFF ou Leitura OFF" in source)
        assertTrue("Acesso precisa mostrar ON/OFF real", "AppControlBubble(\"Acesso\", liveEnabled" in source)
        assertTrue("Leitura precisa considerar permissao Android", "settings.liveReadingEnabled && liveEnabled" in source)
        assertTrue("Barra de abas deve estar eliminada", "bottomBar = {}" in source)
        assertTrue("Icone do app deve abrir na central", "in_app_bubble_home_default_0_1_97" in source)
        assertFalse(
            "Central nao pode ficar limitada apenas a Analise",
            "if (tab == TAB_ANALYSIS) {\n                UnifiedAppControlBubbles(" in source,
        )
    }
}
