package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreVisibleCardLifecycleTest {
    @Test
    fun firstObservationEntersAndClearsPreviousDecision() {
        val lifecycle = CoreVisibleCardLifecycle(nowMillis = { 100L })

        val event = lifecycle.observe(
            packageName = " SINET.STARTUP.INDRIVER ",
            snapshotHash = 10,
            text = "Pedido de viagem\nAceitar por R$ 44",
        )

        assertEquals(CoreVisibleCardAction.Entered, event.action)
        assertNull(event.previousSignature)
        assertNotNull(event.currentSignature)
        assertTrue(event.shouldClearPreviousDecision)
        assertEquals(event.currentSignature, lifecycle.currentSignature())
    }

    @Test
    fun sameObservationDoesNotClearPreviousDecision() {
        val lifecycle = CoreVisibleCardLifecycle(nowMillis = { 200L })

        lifecycle.observe("sinet.startup.indriver", 10, "Pedido de viagem")
        val event = lifecycle.observe("sinet.startup.indriver", 10, "Pedido de viagem")

        assertEquals(CoreVisibleCardAction.Same, event.action)
        assertFalse(event.shouldClearPreviousDecision)
        assertTrue(lifecycle.isCurrent(event.currentSignature))
    }

    @Test
    fun changedTextChangesSignatureAndClearsPreviousDecision() {
        val lifecycle = CoreVisibleCardLifecycle(nowMillis = { 300L })

        val first = lifecycle.observe("sinet.startup.indriver", 10, "Pedido de viagem A")
        val second = lifecycle.observe("sinet.startup.indriver", 10, "Pedido de viagem B")

        assertEquals(CoreVisibleCardAction.Changed, second.action)
        assertEquals(first.currentSignature, second.previousSignature)
        assertTrue(second.shouldClearPreviousDecision)
    }

    @Test
    fun changedPackageIsReplacedAndClearsPreviousDecision() {
        val lifecycle = CoreVisibleCardLifecycle(nowMillis = { 400L })

        val first = lifecycle.observe("sinet.startup.indriver", 10, "Pedido de viagem")
        val second = lifecycle.observe("com.ubercab.driver", 10, "Pedido de viagem")

        assertEquals(CoreVisibleCardAction.Replaced, second.action)
        assertEquals(first.currentSignature, second.previousSignature)
        assertTrue(second.shouldClearPreviousDecision)
    }

    @Test
    fun clearWithoutCurrentCardDoesNothing() {
        val lifecycle = CoreVisibleCardLifecycle(nowMillis = { 500L })

        val event = lifecycle.clear("Nada ativo.")

        assertEquals(CoreVisibleCardAction.None, event.action)
        assertNull(event.previousSignature)
        assertNull(event.currentSignature)
        assertFalse(event.shouldClearPreviousDecision)
    }

    @Test
    fun clearWithCurrentCardExitsAndClearsPreviousDecision() {
        val lifecycle = CoreVisibleCardLifecycle(nowMillis = { 600L })

        val observed = lifecycle.observe("sinet.startup.indriver", 10, "Pedido de viagem")
        val event = lifecycle.clear("Saiu da tela.")

        assertEquals(CoreVisibleCardAction.Exited, event.action)
        assertEquals(observed.currentSignature, event.previousSignature)
        assertNull(event.currentSignature)
        assertNull(lifecycle.currentSignature())
        assertTrue(event.shouldClearPreviousDecision)
    }
}
