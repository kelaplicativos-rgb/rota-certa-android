package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyRecorder0666ContractTest {
    @Test
    fun catalogExposesDistinctFalaAndCenaActions() {
        val module = BubbleShortcutCatalog.modules.singleOrNull { it.spec.id == "safety_recorder" }
        assertNotNull(module)
        val actions = ShortcutActionCatalog0184.actionsForModule("safety_recorder")
        assertTrue(actions.any {
            it.id == "action_record_audio" && it.emoji == "🗣️" && it.displayLabel == "Fala"
        })
        assertTrue(actions.any {
            it.id == "action_record_video" && it.emoji == "🎬" && it.displayLabel == "Cena"
        })
    }

    @Test
    fun recorderActionsDefaultToLongPressModule() {
        var entries = emptyList<ShortcutGridEntry0179>()
        entries = ShortcutGridCustomizationPolicy0179.add(entries, "action_record_audio", 1L)
        entries = ShortcutGridCustomizationPolicy0179.add(entries, "action_record_video", 2L)

        assertEquals(2, entries.size)
        assertTrue(entries.all {
            it.holdActionType0186 == ShortcutHoldActionType0186.OPEN_MODULE
        })
        assertTrue(entries.any { it.shortcutId == "action_record_audio" })
        assertTrue(entries.any { it.shortcutId == "action_record_video" })
    }

    @Test
    fun recorderActionsResolveBackToSafetyModule() {
        assertEquals(
            "safety_recorder",
            ShortcutActionCatalog0184.moduleIdForAction("action_record_audio"),
        )
        assertEquals(
            "safety_recorder",
            ShortcutActionCatalog0184.moduleIdForAction("action_record_video"),
        )
    }
}
