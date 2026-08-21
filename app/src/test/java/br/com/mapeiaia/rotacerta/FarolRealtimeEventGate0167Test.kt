package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolRealtimeEventGate0167Test {
    @Test
    fun `primeiro evento util passa imediatamente e rajada identica e confluida`() {
        val gate = FarolRealtimeEventGate0167(duplicateWindowMillis = 72L)
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 10, 2048, "RecyclerView", 1_000L))
        assertFalse(gate.shouldCollect("app.driver", "app.driver", 10, 2048, "RecyclerView", 1_050L))
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 10, 2048, "RecyclerView", 1_073L))
    }

    @Test
    fun `troca de janela pacote classe ou tipo passa sem esperar`() {
        val gate = FarolRealtimeEventGate0167(duplicateWindowMillis = 72L)
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 10, 2048, "RecyclerView", 1_000L))
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 11, 2048, "RecyclerView", 1_010L))
        assertTrue(gate.shouldCollect("app.driver", "system.overlay", 11, 2048, "RecyclerView", 1_020L))
        assertTrue(gate.shouldCollect("app.driver", "system.overlay", 11, 2048, "FrameLayout", 1_030L))
        assertTrue(gate.shouldCollect("app.driver", "system.overlay", 11, 4096, "FrameLayout", 1_040L))
    }

    @Test
    fun `mudanca de janela do Android nunca e bloqueada`() {
        val gate = FarolRealtimeEventGate0167(duplicateWindowMillis = 500L)
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 10, 32, "Activity", 1_000L))
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 10, 32, "Activity", 1_001L))
        assertTrue(gate.shouldCollect("app.driver", null, 10, 4_194_304, null, 1_002L))
    }

    @Test
    fun `reset libera imediatamente a mesma assinatura`() {
        val gate = FarolRealtimeEventGate0167(duplicateWindowMillis = 72L)
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 10, 2048, "View", 1_000L))
        assertFalse(gate.shouldCollect("app.driver", "app.driver", 10, 2048, "View", 1_010L))
        gate.reset()
        assertTrue(gate.shouldCollect("app.driver", "app.driver", 10, 2048, "View", 1_011L))
    }
}
