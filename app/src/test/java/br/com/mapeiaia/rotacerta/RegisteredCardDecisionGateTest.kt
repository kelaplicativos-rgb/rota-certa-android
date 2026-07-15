package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisteredCardDecisionGateTest {
    @Test
    fun resetsOnlyAfterRegisteredCardBecomesStaleWhileDecisionColorIsVisible() {
        var now = 10_000L
        val gate = RegisteredCardDecisionGate(nowProvider = { now })

        assertFalse(gate.shouldResetStale(hasDecisionColor = true))

        gate.markSeen()
        now += 349L

        assertFalse(gate.shouldResetStale(hasDecisionColor = true))
        assertFalse(gate.shouldResetStale(hasDecisionColor = false))

        now += 1L

        assertTrue(gate.shouldResetStale(hasDecisionColor = true))

        gate.clear()

        assertFalse(gate.shouldResetStale(hasDecisionColor = true))
    }

    @Test
    fun naturalFreshnessWindowStillWorks() {
        var now = 20_000L
        val gate = RegisteredCardDecisionGate(nowProvider = { now })

        gate.markSeen()
        now += 349L

        assertTrue(gate.hasSeenRecently())

        now += 1L

        assertFalse(gate.hasSeenRecently())
    }

    @Test
    fun callerCannotExtendFreshnessAndKeepPreviousDecisionOnScreen() {
        var now = 30_000L
        val gate = RegisteredCardDecisionGate(nowProvider = { now })

        gate.markSeen()

        assertFalse(gate.hasSeenRecently(maxAgeMillis = 2_800L))
        assertFalse(gate.hasSeenRecently(maxAgeMillis = 3_500L))

        now += 50L

        assertFalse(gate.hasSeenRecently(maxAgeMillis = 2_800L))
    }

    @Test
    fun clearAlwaysInvalidatesFreshness() {
        val gate = RegisteredCardDecisionGate()

        gate.markSeen()
        gate.clear()

        assertFalse(gate.hasSeenRecently())
    }
}
