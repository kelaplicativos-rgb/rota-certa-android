package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBubbleTapMenuContractTest {
    @Test
    fun compiledSourceOpensSixIndependentResourceModules() {
        fun sourceFile(name: String): File = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        ).firstOrNull(File::exists) ?: error("$name nao encontrado")

        val service = sourceFile("LiveRideAccessibilityService.kt").readText()
        val controller = sourceFile("BubbleShortcutOverlayController.kt").readText()
        val catalog = sourceFile("BubbleShortcutModule.kt").readText()
        val moduleNames = listOf(
            "AlertBubbleShortcutModule.kt",
            "SavedPlaceBubbleShortcutModule.kt",
            "RideCardBubbleShortcutModule.kt",
            "DestinationBubbleShortcutModule.kt",
            "ReadingBubbleShortcutModule.kt",
            "SettingsBubbleShortcutModule.kt",
        )
        val modules = moduleNames.map(::sourceFile).map(File::readText)

        val overlayStart = service.indexOf("    private fun showOverlay(")
        val overlayEnd = service.indexOf("\n    private fun removeOverlay()", overlayStart)
        assertTrue("showOverlay precisa existir", overlayStart >= 0 && overlayEnd > overlayStart)
        val overlayBlock = service.substring(overlayStart, overlayEnd)

        assertTrue(
            "Toque principal precisa abrir os atalhos",
            "newView.setOnClickListener { toggleResourceShortcuts() }" in overlayBlock,
        )
        assertFalse(
            "Listener principal nao pode abrir a Home diretamente",
            "openApp()" in overlayBlock || "onMainBubbleClick()" in overlayBlock,
        )

        assertTrue("Runtime modular precisa estar aplicado", "bubble_resource_shortcuts_runtime_0_1_117" in service)
        assertTrue("Servico precisa despachar o modulo", "onShortcut = ::executeShortcutModule" in service)
        assertTrue("Alerta precisa possuir acao propria", "BubbleShortcutAction.CreateAlert" in service)
        assertTrue("Local precisa possuir acao propria", "BubbleShortcutAction.CreateSavedPlace" in service)
        assertTrue("Card precisa possuir acao propria", "BubbleShortcutAction.SaveRideCard" in service)
        assertTrue("Arraste precisa fechar a grade", "hideResourceShortcuts()" in service)
        assertFalse("Callbacks fixos nao podem voltar", "BubbleShortcutActions(" in service)

        assertTrue("Catalogo modular ausente", "object BubbleShortcutCatalog" in catalog)
        assertTrue("Controlador precisa percorrer o catalogo", "BubbleShortcutCatalog.modules.forEach" in controller)
        assertTrue("Controlador precisa devolver o modulo", "onShortcut(module.spec)" in controller)
        assertEquals("A grade precisa conter seis classes de modulo", 6, modules.size)
        assertEquals("Cada modulo precisa ser diferente", 6, BubbleShortcutCatalog.modules.map { it::class }.distinct().size)

        listOf(
            "Salvar alerta",
            "Salvar local",
            "Salvar card",
            "Destino",
            "Leitura",
            "Ajustes",
        ).forEachIndexed { index, label ->
            assertTrue("Atalho ausente: $label", "label = \"$label\"" in modules[index])
        }

        assertTrue("Popup de alerta precisa rejeitar Local", "if (alert.type != SavedPlaceType.ProximityAlert)" in controller)
        assertTrue("Popup precisa permitir editar", "popupButton(\"Editar\")" in controller)
        assertTrue("Popup precisa permitir excluir", "popupButton(\"Excluir\")" in controller)
        assertFalse("Marcador antigo do toque deve desaparecer", "bubble.tap.menu_contract_0_1_96" in service)
        assertFalse("Marcador antigo da grade deve desaparecer", "bubble.menu.opened grid=true" in service)
    }
}
