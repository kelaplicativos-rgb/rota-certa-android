package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLinkCapacityPolicy0186Test {
    @Test
    fun allowsUntilThirtyNineAndBlocksForty() {
        assertTrue(QuickLinkCapacityPolicy0186.canCreate(39))
        assertFalse(QuickLinkCapacityPolicy0186.canCreate(40))
        assertFalse(QuickLinkCapacityPolicy0186.canCreate(-1))
    }
}
