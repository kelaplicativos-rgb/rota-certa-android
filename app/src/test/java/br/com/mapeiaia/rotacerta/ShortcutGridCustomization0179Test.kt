package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutGridCustomization0179Test {
    @Test
    fun newInstallStartsEmptyAndUpgradeKeepsExactlySeventeenLegacyActions() {
        assertTrue(ShortcutGridCustomizationPolicy0179.initialEntries(isUpgrade = false).isEmpty())
        val legacy = ShortcutGridCustomizationPolicy0179.initialEntries(isUpgrade = true)
        assertEquals(17, legacy.size)
        assertEquals(ShortcutActionCatalog0184.legacyModuleIds, legacy.map { it.shortcutId })
    }

    @Test
    fun disabledEntriesAreNotRenderedButRemainEditable() {
        val legacy = ShortcutGridCustomizationPolicy0179.legacyDefaults()
        val disabled = legacy.first().copy(enabled = false)
        val resolved = ShortcutGridCustomizationPolicy0179.resolve(listOf(disabled) + legacy.drop(1))
        assertFalse(resolved.any { it.entryId == disabled.entryId })
        assertEquals(legacy.size - 1, resolved.size)
    }

    @Test
    fun customNameAndIconDoNotChangeTheRegisteredAction() {
        val original = ShortcutGridCustomizationPolicy0179.legacyDefaults().first()
        val resolved = ShortcutGridCustomizationPolicy0179.resolve(
            listOf(original.copy(label = "Minha rota", emoji = "⭐")),
        ).single().spec
        assertEquals("Minha rota", resolved.displayLabel)
        assertEquals("⭐", resolved.emoji)
        assertEquals(BubbleShortcutCatalog.findSpec(original.shortcutId)?.action, resolved.action)
        assertEquals(original.shortcutId, resolved.id)
    }

    @Test
    fun addRemoveDuplicateAndMaximumAreDeterministic() {
        val start = ShortcutGridCustomizationPolicy0179.legacyDefaults().take(2)
        val added = ShortcutGridCustomizationPolicy0179.add(start, "action_clear_cache", 123L)
        assertEquals(3, added.size)
        assertEquals("action_clear_cache", added.last().shortcutId)
        assertEquals(added, ShortcutGridCustomizationPolicy0179.add(added, "action_clear_cache", 124L))
        val removed = ShortcutGridCustomizationPolicy0179.remove(added, "action_clear_cache")
        assertFalse(ShortcutGridCustomizationPolicy0179.contains(removed, "action_clear_cache"))

        var bounded = emptyList<ShortcutGridEntry0179>()
        ShortcutActionCatalog0184.allSpecs().forEachIndexed { index, spec ->
            bounded = ShortcutGridCustomizationPolicy0179.add(bounded, spec.id, index.toLong())
        }
        assertTrue(bounded.size <= ShortcutGesturePolicy0179.MAX_GRID_ITEMS)
    }

    @Test
    fun directTapContractRemainsTwoTotalTaps() {
        assertEquals(1_500L, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)
        assertEquals(5_000L, ShortcutGesturePolicy0179.MAIN_CUSTOMIZATION_HOLD_MILLIS)
    }
}
