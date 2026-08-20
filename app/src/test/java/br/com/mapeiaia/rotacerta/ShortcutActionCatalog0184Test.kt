package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutActionCatalog0184Test {
    @Test
    fun sameModuleCanExposeIndependentActions() {
        val backup = ShortcutActionCatalog0184.actionsForModule("backup").map { it.id }
        assertTrue("backup" in backup)
        assertTrue("action_create_backup" in backup)
        assertTrue("action_restore_backup" in backup)

        val clear = ShortcutActionCatalog0184.actionsForModule("clear_clipboard").map { it.id }
        assertTrue("clear_clipboard" in clear)
        assertTrue("action_clear_cache" in clear)
    }

    @Test
    fun noArbitraryActionIsAccepted() {
        val entry = ShortcutGridEntry0179("x", "intent:https://example.com", "x", "x")
        assertTrue(ShortcutGridCustomizationPolicy0179.normalize(listOf(entry)).isEmpty())
        assertFalse(ShortcutActionCatalog0184.allSpecs().any { it.id.startsWith("intent:") })
    }

    @Test
    fun legacyMigrationIsExact() {
        assertEquals(17, ShortcutActionCatalog0184.legacyDefaultSpecs().size)
        assertEquals(ShortcutActionCatalog0184.legacyModuleIds, ShortcutActionCatalog0184.legacyDefaultSpecs().map { it.id })
    }
}
