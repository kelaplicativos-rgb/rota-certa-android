package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolAddressFirst0695Test {
    @Test
    fun layoutAcceptedKeepsLegacyPath() {
        val result = FarolAddressFirst0695.evaluate("sinet.startup.indriver", "qualquer tela", false)
        assertTrue(result.allowPipeline)
    }

    @Test
    fun safeSingleAddressWithActionGetsSecondChance() {
        val text = """
            Pedido de viagem
            Aceitar
            Destino
            Rua Vergueiro, 1000, Vila Mariana, São Paulo - SP
        """.trimIndent()
        val result = FarolAddressFirst0695.evaluate("sinet.startup.indriver", text, true)
        assertTrue(result.allowPipeline)
        assertTrue(result.uniqueAddressCount == 1)
    }

    @Test
    fun safePairNeedsSingleCardProof() {
        val text = """
            Pedido de viagem
            R$ 38,00
            Origem: Rua Tito, 120, Vila Romana, São Paulo - SP
            Destino: Rua Vergueiro, 1000, Vila Mariana, São Paulo - SP
            Aceitar
        """.trimIndent()
        val result = FarolAddressFirst0695.evaluate("sinet.startup.indriver", text, true)
        assertTrue(result.allowPipeline)
        assertTrue(result.uniqueAddressCount == 2)
    }

    @Test
    fun multipleCardsRemainBlocked() {
        val text = """
            Pedido de viagem
            Rua Tito, 120, Vila Romana, São Paulo - SP
            Aceitar
            Pedido de viagem
            Rua Vergueiro, 1000, Vila Mariana, São Paulo - SP
            Aceitar
        """.trimIndent()
        val result = FarolAddressFirst0695.evaluate("sinet.startup.indriver", text, true)
        assertFalse(result.allowPipeline)
    }

    @Test
    fun threeAddressesRemainBlockedEvenWithRideCues() {
        val text = """
            Pedido de viagem
            R$ 50,00
            Rua Afonso, 10, Centro, São Paulo - SP
            Rua Tito, 120, Vila Romana, São Paulo - SP
            Rua Vergueiro, 1000, Vila Mariana, São Paulo - SP
            Aceitar
        """.trimIndent()
        val result = FarolAddressFirst0695.evaluate("sinet.startup.indriver", text, true)
        assertFalse(result.allowPipeline)
    }
}
