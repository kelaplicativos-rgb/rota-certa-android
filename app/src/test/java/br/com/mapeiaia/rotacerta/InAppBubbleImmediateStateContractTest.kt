package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppBubbleImmediateStateContractTest {
    @Test
    fun popupNavigationReplacesHomeGroupSelection() {
        fun sourceFile(name: String): File = listOf(
            File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        ).firstOrNull(File::exists) ?: error("$name nao encontrado")

        val main = sourceFile("MainActivity.kt").readText()
        val service = sourceFile("LiveRideAccessibilityService.kt").readText()
        val catalog = sourceFile("BubbleShortcutModule.kt").readText()

        assertTrue("Contrato popup-only precisa existir", "popup_only_control_center_0_1_119" in main)
        assertFalse(
            "A Home nao pode renderizar a Central de bolinhas",
            "\n            ProfessionalBubbleDashboard(" in main,
        )
        assertTrue(
            "Destino precisa navegar diretamente pelo popup",
            "BubbleShortcutAction.OpenDestination" in service &&
                "openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))" in service,
        )
        assertTrue(
            "Relatorios precisam navegar ao historico pelo popup",
            "object ReportsBubbleShortcutModule" in catalog && "targetTab = \"history\"" in catalog,
        )
        assertTrue(
            "Permissoes precisam navegar a configuracao filtrada",
            "object PermissionsBubbleShortcutModule" in catalog && "targetGroup = \"access\"" in catalog,
        )
        assertTrue(
            "WhatsApp precisa executar imediatamente",
            "BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()" in service,
        )
        assertTrue(
            "Encerrar precisa executar imediatamente",
            "BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()" in service,
        )
        assertFalse(
            "Estado antigo da grade ON/OFF nao deve controlar a Home",
            "settings = bubbleControlSettings," in main,
        )
    }
}
