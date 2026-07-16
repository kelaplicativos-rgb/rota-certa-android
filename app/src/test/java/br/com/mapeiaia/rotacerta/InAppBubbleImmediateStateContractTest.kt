package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBubbleImmediateStateContractTest {
    @Test
    fun inAppToggleChangesLocalComposeStateBeforePersisting() {
        val sourceFile = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"),
        ).firstOrNull(File::exists) ?: error("MainActivity.kt nao encontrado")

        val source = sourceFile.readText()
        assertTrue("Estado imediato precisa existir", "in_app_bubble_immediate_state_0_1_98" in source)
        assertTrue(
            "Central precisa renderizar o estado local imediato",
            "settings = bubbleControlSettings," in source,
        )
        assertTrue(
            "Toque precisa reduzir a partir do estado visual atual",
            "val updated = QuickBubbleToggleReducer.toggle(bubbleControlSettings, toggle)" in source,
        )
        assertTrue(
            "Estado visual precisa mudar antes da persistencia",
            source.indexOf("bubbleControlSettings = updated") < source.indexOf("repository.saveSettings(updated)"),
        )

        val callStart = source.indexOf("UnifiedAppControlBubbles(")
        val callEnd = source.indexOf("\n                )", callStart)
        assertTrue("Chamada da Central de bolinhas precisa existir", callStart >= 0 && callEnd > callStart)
        val call = source.substring(callStart, callEnd)
        assertFalse(
            "Central nao pode depender do estado atrasado capturado do DataStore",
            "QuickBubbleToggleReducer.toggle(settings, toggle)" in call,
        )
    }
}
