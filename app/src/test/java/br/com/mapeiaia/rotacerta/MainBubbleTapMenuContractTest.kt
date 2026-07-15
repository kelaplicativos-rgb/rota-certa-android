package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBubbleTapMenuContractTest {
    @Test
    fun compiledSourceWiresMainBubbleTapToGridInsteadOfOpeningApp() {
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
            "Toque principal nao pode abrir a Activity diretamente",
            "setOnClickListener { openApp" in overlayBlock,
        )

        val tapStart = source.indexOf("    private fun onMainBubbleClick()")
        val tapEnd = source.indexOf("\n    private fun toggleActionMenu()", tapStart)
        assertTrue("Helper do toque precisa existir", tapStart >= 0 && tapEnd > tapStart)
        val tapBlock = source.substring(tapStart, tapEnd)
        assertTrue("Helper precisa alternar o painel", "toggleActionMenu()" in tapBlock)
        assertFalse("Helper nao pode abrir a tela principal", "openApp(" in tapBlock)

        val menuStart = source.indexOf("    private fun showActionMenu()")
        val menuEnd = source.indexOf("\n    private fun hideActionMenu()", menuStart)
        assertTrue("Painel precisa existir", menuStart >= 0 && menuEnd > menuStart)
        val menuBlock = source.substring(menuStart, menuEnd)
        assertTrue("Painel precisa ser grade", "GridLayout(this)" in menuBlock)
        assertTrue("Grade precisa conter Rota", "quickToggleBubbleButton(\"Rota\"" in menuBlock)
        assertTrue("Grade precisa conter Leitura", "quickToggleBubbleButton(\"Leitura\"" in menuBlock)
        assertTrue("Grade precisa conter WhatsApp", "quickActionBubbleButton(\"WA\"" in menuBlock)
    }
}
