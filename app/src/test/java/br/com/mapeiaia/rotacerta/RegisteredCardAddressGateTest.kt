package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisteredCardAddressGateTest {
    @Test
    fun requiresBothParsedEndpointsToContainHouseNumber() {
        val decision = RegisteredCardAddressGate.evaluate(
            RideFields(
                pickup = "Rua das Flores, 120 - Centro",
                destination = "Avenida Brasil - Bela Vista",
            ),
        )

        assertFalse(decision.active)
        assertEquals(1, decision.addresses.size)
        assertNull(decision.destination)
    }

    @Test
    fun usesParsedDestinationInsteadOfLastAddressFromAnotherOffer() {
        val fullScreen = """
            Pedidos de viagem
            R$ 29
            Rua Primeira, 10 (Bairro Um)
            Avenida Primeira, 20 (Bairro Dois)
            PIX
            R$ 37
            Rua Segunda, 30 (Bairro Tres)
            Avenida Segunda, 40 (Bairro Quatro)
            PIX
        """.trimIndent()
        val parsed = RideTextParser().parse(fullScreen, RideCardTemplateMatcher.INDRIVE_PACKAGE)
        val decision = RegisteredCardAddressGate.evaluate(parsed)

        assertTrue(decision.active)
        assertEquals("Rua Primeira, 10 (Bairro Um)", decision.pickup)
        assertEquals("Avenida Primeira, 20 (Bairro Dois)", decision.destination)
    }

    @Test
    fun rejectsSnEvenWhenBothLinesLookLikeStreets() {
        val decision = RegisteredCardAddressGate.evaluate(
            RideFields(
                pickup = "Rua Um, 10",
                destination = "Rua Dois, s/n",
            ),
        )

        assertFalse(decision.active)
        assertNull(decision.destination)
    }

    @Test
    fun acceptsLetterAndBlockHouseNumberFormats() {
        val decision = RegisteredCardAddressGate.evaluate(
            RideFields(
                pickup = "Rua Um, 123-A",
                destination = "Rua Dois, 456 bloco B",
            ),
        )

        assertTrue(decision.active)
        assertEquals("Rua Dois, 456 bloco B", decision.destination)
    }
}
