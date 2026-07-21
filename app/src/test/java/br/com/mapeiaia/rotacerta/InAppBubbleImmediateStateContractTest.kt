package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBubbleImmediateStateContractTest {
    @Test
    fun popupNavigationUsesIndependentModulesAndStartsInPermissions() {
        fun sourceFile(name: String): File = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        ).firstOrNull(File::exists) ?: error("$name nao encontrado")

        val main = sourceFile("MainActivity.kt").readText()
        val service = sourceFile("LiveRideAccessibilityService.kt").readText()
        val catalog = sourceFile("BubbleShortcutModule.kt").readText()

        assertTrue("Contrato 0.1.120 precisa existir", "popup_navigation_main_0_1_120" in main)
        assertTrue("Aplicativo precisa iniciar em Permissoes", "startup_permissions_0_1_120" in main)
        assertTrue("Permissoes precisam ser o grupo inicial", "mutableStateOf(BUBBLE_GROUP_ACCESS)" in main)
        assertTrue("A aba inicial precisa ser Config", "mutableStateOf(TAB_CONFIG)" in main)

        assertTrue("Rota precisa abrir Controle geral", "targetGroup = \"general\"" in catalog && "targetTab = \"config\"" in catalog)
        assertTrue("Destino precisa continuar independente", "targetGroup = \"destination\"" in sourceFile("DestinationBubbleShortcutModule.kt").readText())
        assertTrue("Alertas precisam ter grupo proprio", "targetGroup = \"alerts\"" in catalog)
        assertTrue("Locais precisam ter grupo proprio", "targetGroup = \"saved_places\"" in catalog)
        assertTrue("Radares precisam ter grupo proprio", "targetGroup = \"radars\"" in catalog)
        assertTrue("Cards precisam ter grupo proprio", "targetGroup = \"cards\"" in catalog)
        assertTrue("Servico precisa navegar pelos grupos", "openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))" in service)

        assertTrue("Locais e alertas precisam usar tela filtrada", "separate_saved_place_modules_0_1_120" in main)
        assertTrue("Cards precisam abrir CardModelsCard", "BUBBLE_GROUP_CARDS -> CardModelsCard(" in main)
        assertFalse("Relatorios nao podem permanecer no popup", "ReportsBubbleShortcutModule," in catalog.substringAfter("val modules:"))
        assertFalse("Alerta rapido duplicado nao pode permanecer", "AlertBubbleShortcutModule," in catalog.substringAfter("val modules:"))
        assertFalse("Estado antigo ON/OFF nao deve controlar a Home", "settings = bubbleControlSettings," in main)
    }
}
