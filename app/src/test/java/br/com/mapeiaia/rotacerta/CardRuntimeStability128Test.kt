package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRuntimeStability128Test {
    @Test
    fun `saida precisa de duas ausencias confirmadas`() {
        val gate = CardExitConfirmationGate(requiredMisses = 2, minimumGapMillis = 50L, maximumGraceMillis = 500L)
        gate.observeActive("card-a")
        assertFalse(gate.shouldClear(1_000L))
        assertFalse(gate.shouldClear(1_020L))
        assertTrue(gate.shouldClear(1_080L))
    }

    @Test
    fun `foco preserva primeiro card durante reordenacao transitoria`() {
        val lock = PrimaryCardFocusLock(releaseMisses = 2, releaseGraceMillis = 500L)
        val first = lock.select(MULTI_CARD, nowMillis = 1_000L)
        assertNotNull(first.selection)
        assertFalse(first.holdPrevious)

        val transient = lock.select("", nowMillis = 1_050L)
        assertTrue(transient.holdPrevious)

        val confirmed = lock.select("", nowMillis = 1_120L)
        assertFalse(confirmed.holdPrevious)
    }

    private companion object {
        val MULTI_CARD = """
            Alex
            4.78
            (33)
            2 min.
            R$ 1,4/km
            6,5 km
            R$ 15
            Rua Isaar Carlos de Camargo 121
            Mc Donald's Vila Carmosina Avenida Jacu-Pessego
            Melissa
            4.91
            (120)
            3 min.
            R$ 2,0/km
            8 km
            R$ 20
            Rua Um, 10
            Avenida Dois, 20
        """.trimIndent()
    }
}
