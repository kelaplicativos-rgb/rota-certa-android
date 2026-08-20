package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerValueFinanceBubbleContractTest {
    @Test
    fun valueAndFinanceAreIndependentSilentModules() {
        val sourceRoot = listOf(File("src/main/java/br/com/mapeiaia/rotacerta"), File("app/src/main/java/br/com/mapeiaia/rotacerta")).first(File::exists)
        val catalog = File(sourceRoot, "BubbleShortcutModule.kt").readText()
        val service = File(sourceRoot, "LiveRideAccessibilityService.kt").readText()
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first(File::exists).readText()
        assertTrue("Bolinha Valor precisa existir", "PassengerValueBubbleShortcutModule" in catalog)
        assertTrue("Bolinha Financeiro precisa existir", "FinanceBubbleShortcutModule" in catalog)
        assertTrue("Ação de copiar valor precisa existir", "CopyPassengerValue" in catalog)
        assertTrue("Ação de abrir financeiro precisa existir", "OpenFinance" in catalog)
        assertTrue("Financeiro precisa estar no Manifest", ".FinancialActivity" in manifest)
        val valueStart = service.indexOf("private fun copyPassengerValue159")
        val valueEnd = service.indexOf("private fun requestPassengerValueOcr159", valueStart)
        assertTrue(valueStart >= 0 && valueEnd > valueStart)
        val valueSection = service.substring(valueStart, valueEnd)
        assertFalse("Valor não pode falar com TTS", "speak(" in valueSection)
        assertFalse("Valor não pode anunciar em voz alta", "announceForAccessibility" in valueSection)
        assertFalse("Valor não pode alterar farol", "showOverlay(" in valueSection || "resetToIdle(" in valueSection)
    }
}
