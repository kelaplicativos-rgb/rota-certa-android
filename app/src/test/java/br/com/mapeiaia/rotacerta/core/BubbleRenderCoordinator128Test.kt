package br.com.mapeiaia.rotacerta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleRenderCoordinator128Test {
    @Test
    fun `evento systemui nao apaga decisao recente`() {
        var now = 1_000L
        val coordinator = BubbleRenderCoordinator(transientProtectionMillis = 450L, nowMillis = { now })
        assertTrue(coordinator.request(1, CoreBubbleMode.Good, 4.2, "rota pronta").accepted)
        now += 100L
        val rejected = coordinator.request(1, CoreBubbleMode.Hidden, null, "com.android.systemui janela transitoria")
        assertFalse(rejected.accepted)
        assertEquals(CoreBubbleMode.Good, coordinator.current().mode)
    }

    @Test
    fun `comando de geracao antiga e descartado`() {
        val coordinator = BubbleRenderCoordinator()
        coordinator.request(5, CoreBubbleMode.Bad, 12.0, "rota atual")
        val old = coordinator.request(4, CoreBubbleMode.Good, 2.0, "rota antiga")
        assertFalse(old.accepted)
        assertEquals(CoreBubbleMode.Bad, coordinator.current().mode)
    }
}
