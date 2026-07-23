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
    fun operationalControlsUsePopupWithoutGeneralControlDuplicates() {
        BubbleShortcutCatalog.requireValid()
        val ids = BubbleShortcutCatalog.modules.map { it.spec.id }

        assertEquals(14, ids.size)
        listOf(
            "route",
            "destination",
            "alerts",
            "saved_places",
            "radars",
            "appearance",
            "backup",
            "whatsapp",
            "collector",
            "clear_clipboard",
            "diagnostic",
            "quick_replies",
            "stop_app",
            "cards",
        ).forEach { id -> assertTrue("Atalho ausente: $id", id in ids) }

        assertFalse("Leitura deve ficar em Controles gerais", "reading" in ids)
        assertFalse("Permissao deve ficar em Controles gerais", "permissions" in ids)
        assertFalse("Relatorios foram eliminados do popup", "reports" in ids)
        assertFalse("Alerta duplicado foi eliminado", "alert" in ids)
        assertFalse("Local rapido duplicado foi eliminado", "saved_place" in ids)
        assertFalse("Card rapido antigo foi eliminado", "ride_card" in ids)
    }

    @Test
    fun modulesRemainSeparatedAndGeneralControlsAreTheEntryPoint() {
        val main = source("MainActivity.kt")

        assertTrue("Modulo de Alertas ausente", "BUBBLE_GROUP_ALERTS -> SavedPlacesModuleCard(" in main)
        assertTrue("Modulo de Locais ausente", "BUBBLE_GROUP_SAVED_PLACES -> SavedPlacesModuleCard(" in main)
        assertTrue("Modulo de Radares ausente", "BUBBLE_GROUP_RADARS -> RadarImportCard(" in main)
        assertTrue("Modulo de Cards ausente", "BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard(" in main)
        assertTrue("Modulo de Cards precisa listar modelos", "registered_cards_module_0_1_120" in main)
        assertTrue(
            "Locais precisam de ordem alfabetica",
            "SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == type })" in main,
        )
        assertTrue("Inicio deve ocorrer em Controles gerais", "mutableStateOf(BUBBLE_GROUP_GENERAL)" in main)
        assertTrue("Grupos antigos devem apontar ao geral", "legacy_access_groups_to_general_checklist_7" in main)
        assertTrue("Leitura deve estar no controle geral", "label = \"Leitura ao vivo\"" in main)
        assertTrue("Permissao deve estar no controle geral", "Permissão de acessibilidade" in main)
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
