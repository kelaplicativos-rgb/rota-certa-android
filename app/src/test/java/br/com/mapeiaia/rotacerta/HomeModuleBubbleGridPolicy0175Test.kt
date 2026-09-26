package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeModuleBubbleGridPolicy0175Test {
    @Test
    fun seventeenModulesCreateSixOrderedRows() {
        val ids = (1..17).map { "module-$it" }
        val rows = HomeModuleBubbleGridPolicy0175.rows(ids)

        assertEquals(6, rows.size)
        assertEquals(listOf("module-1", "module-2", "module-3"), rows.first())
        assertEquals(listOf("module-16", "module-17"), rows.last())
        assertEquals(ids, rows.flatten())
    }

    @Test
    fun expandedContentBelongsOnlyToItsOwnRow() {
        val first = listOf("a", "b", "c")
        val second = listOf("d", "e", "f")

        assertEquals("e", HomeModuleBubbleGridPolicy0175.expandedIdInRow(second, "e"))
        assertNull(HomeModuleBubbleGridPolicy0175.expandedIdInRow(first, "e"))
        assertNull(HomeModuleBubbleGridPolicy0175.expandedIdInRow(second, null))
    }
}
