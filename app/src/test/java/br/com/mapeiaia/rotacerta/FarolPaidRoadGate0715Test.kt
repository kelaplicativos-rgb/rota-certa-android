package br.com.mapeiaia.rotacerta

import br.com.mapeiaia.rotacerta.trips.FarolPaidRoadTarget0715
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FarolPaidRoadGate0715Test {
    private val targets = listOf(
        FarolPaidRoadTarget0715(-23.5505, -46.6333),
        FarolPaidRoadTarget0715(-23.5000, -46.6200),
    )

    @Test
    fun oneNetworkAttemptPerStableFingerprintThenCache() {
        val values = LinkedHashMap<String, String>()
        var now = 1_000L
        val gate = FarolPaidRoadGate0715.inMemoryForTests(values) { now }
        val first = assertIs<FarolPaidRoadGate0715.Start.Network>(
            gate.start("com.test.driver", "Shopping X", "Destino\nShopping X\nMoema", targets),
        )
        val duplicate = assertIs<FarolPaidRoadGate0715.Start.Suppressed>(
            gate.start("com.test.driver", "Shopping X", "Destino\nShopping X\nMoema", targets),
        )
        assertEquals("already_in_flight", duplicate.reason)

        gate.success(first.ticket, "Shopping X, Moema, São Paulo - SP", 0.96, 5.569, 1, "osrm")
        val cached = assertIs<FarolPaidRoadGate0715.Start.Cached>(
            gate.start("com.test.driver", "Shopping X", "Destino\nShopping X\nMoema", targets),
        )
        assertEquals(5.569, cached.roadKm)
        assertEquals(1, cached.targetIndex)
        assertEquals("osrm", cached.routeProvider)
        assertTrue(cached.normalizedAddress.contains("Shopping X"))
    }

    @Test
    fun failedFingerprintEntersCooldownInsteadOfSpendingAgain() {
        val values = LinkedHashMap<String, String>()
        var now = 10_000L
        val gate = FarolPaidRoadGate0715.inMemoryForTests(values) { now }
        val first = assertIs<FarolPaidRoadGate0715.Start.Network>(
            gate.start("com.test.driver", "Rua A, 10", "Destino Rua A, 10", targets),
        )
        gate.failure(first.ticket)
        now += 1_000L
        val suppressed = assertIs<FarolPaidRoadGate0715.Start.Suppressed>(
            gate.start("com.test.driver", "Rua A, 10", "Destino Rua A, 10", targets),
        )
        assertEquals("failure_cooldown", suppressed.reason)
    }
}
