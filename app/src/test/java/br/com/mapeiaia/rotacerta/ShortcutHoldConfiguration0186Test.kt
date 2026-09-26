package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutHoldConfiguration0186Test {
    @Test
    fun migrationPreservesQuickIdentityAndDefaultsRelatedModule() {
        val entry = ShortcutGridEntry0179(
            entryId = "one",
            shortcutId = "action_create_alert_here",
            label = "Alerta",
            emoji = "⚠️",
        )
        val normalized = ShortcutGridCustomizationPolicy0179.normalize(listOf(entry)).single()
        assertEquals("action_create_alert_here", normalized.shortcutId)
        assertEquals(ShortcutGestureAction0180.PRIMARY_ACTION, normalized.quickAction0180)
        assertEquals(ShortcutHoldActionType0186.OPEN_MODULE, normalized.holdActionType0186)
        assertNull(normalized.holdShortcutId0186)
    }

    @Test
    fun anotherActionMustComeFromTypedCatalog() {
        val invalid = ShortcutGridEntry0179(
            entryId = "one",
            shortcutId = "route",
            label = "Rota",
            emoji = "⚡",
            holdActionType0186 = ShortcutHoldActionType0186.SAFE_ACTION,
            holdShortcutId0186 = "shell:rm",
        )
        val normalized = ShortcutGridCustomizationPolicy0179.normalize(listOf(invalid)).single()
        assertEquals(ShortcutHoldActionType0186.NONE, normalized.holdActionType0186)
        assertNull(normalized.holdShortcutId0186)
        assertTrue(ShortcutActionCatalog0184.allSpecs().none { it.id == "shell:rm" })
    }
}
