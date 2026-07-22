package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalBubbleHome118ContractTest {
    private fun source(name: String): String = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/$name"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
    ).firstOrNull(File::exists)?.readText() ?: error("$name nao encontrado")

    @Test
    fun operationalControlsUseSeparatedPopupCatalog() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }

        assertEquals(15, ids.size)
        listOf(
            "route",
            "destination",
            "alerts",
            "saved_places",
            "radars",
            "appearance",
            "permissions",
            "backup",
            "whatsapp",
            "collector",
            "clear_clipboard",
            "diagnostic",
            "stop_app",
            "cards",
            "reading",
        ).forEach { id -> assertTrue("Atalho ausente: $id", id in ids) }

        assertFalse("Relatorios foram eliminados do popup", "reports" in ids)
        assertFalse("Alerta duplicado foi eliminado", "alert" in ids)
        assertFalse("Local rapido duplicado foi eliminado", "saved_place" in ids)
        assertFalse("Card rapido antigo foi eliminado", "ride_card" in ids)
    }

    @Test
    fun modulesRemainStrictlySeparatedInsideTheApp() {
        val main = source("MainActivity.kt")

        assertTrue("Modulo de Alertas ausente", "BUBBLE_GROUP_ALERTS -> SavedPlacesModuleCard(" in main)
        assertTrue("Modulo de Locais ausente", "BUBBLE_GROUP_SAVED_PLACES -> SavedPlacesModuleCard(" in main)
        assertTrue("Modulo de Radares ausente", "BUBBLE_GROUP_RADARS -> RadarImportCard(" in main)
        assertTrue("Modulo de Cards ausente", "BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard(" in main)
        assertTrue("Modulo de Cards precisa listar modelos", "registered_cards_module_0_1_120" in main)
        assertTrue("Filtro por tipo precisa existir", "val items = savedPlaces.filter { it.type == type }" in main)
        assertTrue("Inicio em Permissoes ausente", "startup_permissions_0_1_120" in main)
    }

    @Test
    fun diagnosticRemainsDirectWithoutReportsBubble() {
        val service = source("LiveRideAccessibilityService.kt")
        val diagnostic = BubbleShortcutCatalog.modules.first { it.spec.id == "diagnostic" }.spec

        assertEquals(BubbleShortcutAction.ExportDiagnostic, diagnostic.action)
        assertTrue("Despacho do diagnostico ausente", "BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()" in service)
        assertTrue("Exportacao automatica precisa ser solicitada", ".putExtra(\"auto_export_report\", true)" in service)
    }
}
