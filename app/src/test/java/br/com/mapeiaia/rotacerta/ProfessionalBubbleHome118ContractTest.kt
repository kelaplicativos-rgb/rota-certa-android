package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalBubbleHome118ContractTest {
    private fun serviceSource(): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText() ?: error("LiveRideAccessibilityService.kt nao encontrado")

    @Test
    fun operationalControlsWereTransferredToPopupCatalog() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }

        assertEquals(16, ids.size)
        listOf(
            "route",
            "destination",
            "alerts",
            "appearance",
            "permissions",
            "backup",
            "reports",
            "whatsapp",
            "collector",
            "clear_clipboard",
            "diagnostic",
            "stop_app",
            "alert",
            "saved_place",
            "ride_card",
            "reading",
        ).forEach { id -> assertTrue("Atalho transferido ausente: $id", id in ids) }
    }

    @Test
    fun diagnosticCanBeRequestedDirectlyFromPopup() {
        val service = serviceSource()
        val diagnostic = BubbleShortcutCatalog.modules.first { it.spec.id == "diagnostic" }.spec

        assertEquals(BubbleShortcutAction.ExportDiagnostic, diagnostic.action)
        assertTrue("Despacho do diagnostico ausente", "BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()" in service)
        assertTrue("Pedido de exportacao precisa abrir Relatorios", ".putExtra(EXTRA_OPEN_BUBBLE_GROUP, \"reports\")" in service)
        assertTrue("Exportacao automatica precisa ser solicitada", ".putExtra(\"auto_export_report\", true)" in service)
    }
}
