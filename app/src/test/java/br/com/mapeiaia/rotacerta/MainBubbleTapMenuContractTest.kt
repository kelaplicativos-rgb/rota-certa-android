package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBubbleTapMenuContractTest {
    @Test
    fun compiledSourceOpensSixLightweightResourceShortcuts() {
        val serviceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        ).firstOrNull(File::exists) ?: error("LiveRideAccessibilityService.kt nao encontrado")
        val controllerFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt"),
        ).firstOrNull(File::exists) ?: error("BubbleShortcutOverlayController.kt nao encontrado")

        val service = serviceFile.readText()
        val controller = controllerFile.readText()
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
        assertTrue(
            "Atalho Alerta precisa criar ProximityAlert",
            "onSaveAlert = { saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert) }" in service,
        )
        assertTrue(
            "Atalho Local precisa criar somente Place",
            "onSaveLocal = { saveCurrentPlaceFromBubble(SavedPlaceType.Place) }" in service,
        )
        assertTrue("Arraste precisa fechar a grade", "shortcutOverlayController.hideShortcuts()" in service)

        assertEquals(
            "A grade precisa conter exatamente seis atalhos",
            6,
            Regex("addView\\(shortcutBubble\\(").findAll(controller).count(),
        )
        listOf(
            "Salvar alerta",
            "Salvar local",
            "Salvar card",
            "Abrir destino",
            "Abrir leitura",
            "Abrir ajustes",
        ).forEach { label ->
            assertTrue("Atalho ausente: $label", "\"$label\"" in controller)
        }

        assertTrue(
            "Popup de alerta precisa rejeitar Local",
            "if (alert.type != SavedPlaceType.ProximityAlert)" in controller,
        )
        assertTrue("Popup precisa permitir editar", "popupButton(\"Editar\")" in controller)
        assertTrue("Popup precisa permitir excluir", "popupButton(\"Excluir\")" in controller)

        assertFalse("Marcador antigo do toque deve desaparecer", "bubble.tap.menu_contract_0_1_96" in service)
        assertFalse("Marcador antigo da grade deve desaparecer", "bubble.menu.opened grid=true" in service)
    }
}
