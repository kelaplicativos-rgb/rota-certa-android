package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.BubbleShortcutAction
import br.com.mapeiaia.rotacerta.BubbleShortcutCatalog
import br.com.mapeiaia.rotacerta.ShortcutModuleFocusPolicy0177
import br.com.mapeiaia.rotacerta.TripAgendaBubbleShortcutModuleStage47
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripInternalShortcutStage47Test {
    @Test
    fun agendaHasDedicatedExecutableShortcutWithoutChangingLegacyIds() {
        BubbleShortcutCatalog.requireValid()
        val spec = BubbleShortcutCatalog.findSpec("trip_agenda")
        requireNotNull(spec)
        assertEquals(TripAgendaBubbleShortcutModuleStage47.spec, spec)
        assertEquals(BubbleShortcutAction.OpenTrips, spec.action)
        assertTrue(ShortcutModuleFocusPolicy0177.routesByModuleIdentity(spec.action))
        assertTrue(
            listOf(
                "route", "destination", "alerts", "saved_places", "radars", "appearance", "backup", "whatsapp",
                "copy_trip_confirmation", "passenger_value", "finance", "clear_clipboard", "diagnostic",
                "quick_replies", "quick_links", "manual_capture", "stop_app",
            ).all { BubbleShortcutCatalog.findSpec(it) != null },
        )
    }
}
