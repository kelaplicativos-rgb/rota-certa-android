package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAddressTriggerTest {
    @Test
    fun zeroOrOneAddressNeverActivatesBubble() {
        val empty = UniversalAddressTrigger.evaluate("Aceitar por R$ 22")
        val one = UniversalAddressTrigger.evaluate("Rua das Flores, 120 - Centro, Sao Paulo - SP")

        assertFalse(empty.active)
        assertNull(empty.destination)
        assertFalse(one.active)
        assertNull(one.destination)
    }

    @Test
    fun twoAddressesActivateImmediatelyAndLastOneIsDestination() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            A Rua das Flores, 120 - Centro, Sao Paulo - SP
            B Avenida Brasil, 900 - Bela Vista, Sao Paulo - SP
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertEquals("Rua das Flores, 120 - Centro, Sao Paulo - SP", decision.pickup)
        assertEquals("Avenida Brasil, 900 - Bela Vista, Sao Paulo - SP", decision.destination)
        assertEquals(2, decision.addresses.size)
    }

    @Test
    fun threeAddressesStillUseTheLastVisibleOne() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua Um, 10 - Centro, Sao Paulo - SP
            Rua Dois, 20 - Centro, Santo Andre - SP
            Rua Tres, 30 - Centro, Maua - SP
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertEquals("Rua Tres, 30 - Centro, Maua - SP", decision.destination)
    }

    @Test
    fun anyScreenModificationInvalidatesPreviousHashEvenWithSameAddresses() {
        val before = UniversalAddressTrigger.evaluate(
            """
            Rua Um, 10 - Centro, Sao Paulo - SP
            Rua Dois, 20 - Centro, Santo Andre - SP
            Aceitar
            """.trimIndent(),
        )
        val after = UniversalAddressTrigger.evaluate(
            """
            Rua Um, 10 - Centro, Sao Paulo - SP
            Rua Dois, 20 - Centro, Santo Andre - SP
            Fechar
            """.trimIndent(),
        )

        assertEquals(before.addressSignature, after.addressSignature)
        assertNotEquals(before.screenHash, after.screenHash)
    }
}
