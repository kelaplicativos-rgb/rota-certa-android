package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolPaidAiGate0695Test {
    @Test
    fun repeatedCardCanStartOnlyOneNetworkCall() {
        val gate = FarolPaidAiGate0695.inMemoryForTests()
        val text = "Pedido de viagem\nDestino: Rua Vergueiro, 1000, São Paulo - SP\nAceitar"
        val first = gate.start("sinet.startup.indriver", text)
        val second = gate.start("sinet.startup.indriver", text)
        assertTrue(first is FarolPaidAiGate0695.Start.Network)
        assertTrue(second is FarolPaidAiGate0695.Start.Suppressed)
    }

    @Test
    fun successfulResultBecomesPersistentCacheForSameFingerprint() {
        val values = LinkedHashMap<String, String>()
        val gate = FarolPaidAiGate0695.inMemoryForTests(values)
        val text = "Pedido de viagem\nDestino: Rua Vergueiro, 1000, São Paulo - SP\nAceitar"
        val first = gate.start("sinet.startup.indriver", text) as FarolPaidAiGate0695.Start.Network
        gate.success(first.ticket, "Rua Vergueiro, 1000, São Paulo - SP", 0.94)

        val recreated = FarolPaidAiGate0695.inMemoryForTests(values)
        val cached = recreated.start("sinet.startup.indriver", text) as FarolPaidAiGate0695.Start.Cached
        assertEquals("Rua Vergueiro, 1000, São Paulo - SP", cached.address)
        assertTrue(cached.confidence >= 0.9)
    }

    @Test
    fun sanitizerRedactsPhoneAndMoney() {
        val sanitized = FarolPaidAiGate0695.sanitizeForRemote(
            "Pedido de viagem\n+55 11 99999-8888\nR$ 52,30\nDestino: Rua Vergueiro, 1000, São Paulo - SP",
        )
        assertTrue("[telefone]" in sanitized)
        assertTrue("[valor]" in sanitized)
    }

    @Test
    fun dynamicEtaDoesNotChangeFingerprint() {
        val a = FarolPaidAiGate0695.sanitizeForRemote("Pedido de viagem\n5 min\nDestino: Rua Vergueiro, 1000, São Paulo - SP")
        val b = FarolPaidAiGate0695.sanitizeForRemote("Pedido de viagem\n6 min\nDestino: Rua Vergueiro, 1000, São Paulo - SP")
        assertEquals(
            FarolPaidAiGate0695.fingerprint("sinet.startup.indriver", a),
            FarolPaidAiGate0695.fingerprint("sinet.startup.indriver", b),
        )
    }
}
