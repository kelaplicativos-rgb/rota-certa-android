package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutActivityLaunchPolicy0176Test {
    @Test
    fun pendingIntentIsRequiredFromAndroid14() {
        assertFalse(ShortcutActivityLaunchPolicy0176.usePendingIntent(33))
        assertTrue(ShortcutActivityLaunchPolicy0176.usePendingIntent(34))
        assertTrue(ShortcutActivityLaunchPolicy0176.usePendingIntent(36))
    }

    @Test
    fun requestCodeStaysPositiveAndNeverUsesZero() {
        assertEquals(17_601, ShortcutActivityLaunchPolicy0176.requestCode(17_601))
        assertEquals(17_600, ShortcutActivityLaunchPolicy0176.requestCode(Int.MIN_VALUE))
        assertEquals(Int.MAX_VALUE, ShortcutActivityLaunchPolicy0176.requestCode(-1))
    }
}
