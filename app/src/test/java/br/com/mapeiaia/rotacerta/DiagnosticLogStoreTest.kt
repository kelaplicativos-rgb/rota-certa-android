package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticLogStoreTest {
    @Before
    fun setUp() {
        DiagnosticRuntimeGate.setEnabled(true)
        DiagnosticLogStore.clear()
    }

    @Test
    fun keepsOnlyMostRecentEvents() {
        repeat(1_505) { index ->
            DiagnosticLogStore.record("test", "event=$index", nowMillis = index.toLong())
        }

        val lines = DiagnosticLogStore.dump().lines()

        assertFalse(lines.any { it.endsWith(" event=0") })
        assertFalse(lines.any { it.endsWith(" event=4") })
        assertTrue(lines.any { it.endsWith(" event=5") })
        assertTrue(lines.any { it.endsWith(" event=1504") })
        assertEquals(1_500, lines.size)
    }

    @Test
    fun sanitizesSourceAndMessageForSingleLineDump() {
        DiagnosticLogStore.record("main screen", "clicked\ncopy\rdiagnostic", nowMillis = 123L)

        val lines = DiagnosticLogStore.dump().lines()

        assertEquals(1, lines.size)
        assertEquals("123 main_screen clicked copy diagnostic", lines.single())
    }

    @Test
    fun limitsDumpToRequestedNumberOfEvents() {
        repeat(10) { index ->
            DiagnosticLogStore.record("source", "event=$index", nowMillis = index.toLong())
        }

        val dump = DiagnosticLogStore.dump(maxEvents = 3)

        assertEquals(listOf("7 source event=7", "8 source event=8", "9 source event=9"), dump.lines())
    }
}
