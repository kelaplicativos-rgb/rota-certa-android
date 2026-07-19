package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBubbleTapMenuContractTest {
    @Test
    fun compiledSourceOpensHomeDirectlyWithoutFloatingGrid() {
        val sourceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        ).firstOrNull(File::exists) ?: error("LiveRideAccessibilityService.kt nao encontrado")

        val source = sourceFile.readText()
        val overlayStart = source.indexOf("    private fun showOverlay(")
        val overlayEnd = source.indexOf("\n    private fun removeOverlay()", overlayStart)
        assertTrue("showOverlay precisa existir", overlayStart >= 0 && overlayEnd > overlayStart)
        val overlayBlock = source.substring(overlayStart, overlayEnd)

        assertTrue(
            "Toque principal precisa chamar onMainBubbleClick",
            "newView.setOnClickListener { onMainBubbleClick() }" in overlayBlock,
        )
        assertFalse(
            "Listener principal nao pode chamar o alternador do popup",
            "toggleActionMenu()" in overlayBlock || "showActionMenu()" in overlayBlock,
        )

        val tapStart = source.indexOf("    private fun onMainBubbleClick()")
        val tapEnd = source.indexOf("\n    private fun toggleActionMenu()", tapStart)
        assertTrue("Helper do toque precisa existir", tapStart >= 0 && tapEnd > tapStart)
        val tapBlock = source.substring(tapStart, tapEnd)
        assertTrue("Helper precisa abrir a tela principal", "openApp()" in tapBlock)
        assertTrue("Helper precisa registrar o contrato novo", "bubble.tap.home_direct_0_1_114" in tapBlock)
        assertFalse("Helper nao pode alternar o painel", "toggleActionMenu()" in tapBlock)
        assertFalse("Helper nao pode abrir a grade", "showActionMenu()" in tapBlock)

        val menuStart = source.indexOf("    private fun showActionMenu()")
        val menuEnd = source.indexOf("\n    private fun hideActionMenu()", menuStart)
        assertTrue("Stub de compatibilidade precisa existir", menuStart >= 0 && menuEnd > menuStart)
        val menuBlock = source.substring(menuStart, menuEnd)
        assertTrue(
            "Stub precisa estar marcado como popup removido",
            "floating_bubble_popup_removed_0_1_114" in menuBlock,
        )
        assertFalse("Grade flutuante nao pode existir", "GridLayout(this)" in menuBlock)
        assertFalse("Popup nao pode adicionar janela", "manager.addView(menu" in menuBlock)
        assertFalse("Popup nao pode manter botoes rapidos", "quickBubbleButton(" in menuBlock)

        assertFalse("Marcador antigo do toque deve desaparecer", "bubble.tap.menu_contract_0_1_96" in source)
        assertFalse("Marcador antigo da grade deve desaparecer", "bubble.menu.opened grid=true" in source)
    }
}
