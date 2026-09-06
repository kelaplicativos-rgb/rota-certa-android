package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModuleExpansionPolicy0174Test {
    @Test
    fun `opening a module replaces the previous expanded module`() {
        assertEquals("alerts", HomeModuleExpansionPolicy0174.toggle("route", "alerts"))
        assertTrue(HomeModuleExpansionPolicy0174.isExpanded("alerts", "alerts"))
        assertFalse(HomeModuleExpansionPolicy0174.isExpanded("alerts", "route"))
    }

    @Test
    fun `tapping the expanded module collapses it`() {
        assertNull(HomeModuleExpansionPolicy0174.toggle("alerts", "alerts"))
    }
}
