package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAddressTriggerTest {
    @Test
    fun twoStreetsWithoutNumbersProduceZeroValidAddressesAndStayInactive() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores
            Avenida Brasil
            """.trimIndent(),
        )

        assertEquals(0, decision.addresses.size)
        assertFalse(decision.active)
        assertTrue(decision.shouldClearPreviousResult)
        assertNull(decision.destination)
    }

    @Test
    fun oneNumberedStreetAndOneIncompleteStreetProduceOnlyOneValidAddress() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores, 120 - Centro, Sao Paulo - SP
            Avenida Brasil - Bela Vista, Santo Andre - SP
            """.trimIndent(),
        )

        assertEquals(listOf("Rua das Flores, 120 - Centro, Sao Paulo - SP"), decision.addresses)
        assertFalse(decision.active)
        assertTrue(decision.shouldClearPreviousResult)
        assertNull(decision.destination)
    }

    @Test
    fun twoNumberedStreetsActivateAndUseLastNumberedAddressAsDestination() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            A Rua das Flores, 120 - Centro, Sao Paulo - SP
            B Avenida Brasil, 900 - Bela Vista, Santo Andre - SP
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertFalse(decision.shouldClearPreviousResult)
        assertEquals("Rua das Flores, 120 - Centro, Sao Paulo - SP", decision.pickup)
        assertEquals("Avenida Brasil, 900 - Bela Vista, Santo Andre - SP", decision.destination)
        assertEquals(2, decision.addresses.size)
    }

    @Test
    fun incompleteLastAddressIsIgnoredAndPreviousCompleteAddressRemainsDestination() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua Um, 10 - Centro, Sao Paulo - SP
            Rua Dois, 20 - Centro, Santo Andre - SP
            Rua Tres - Centro, Maua - SP
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertEquals(2, decision.addresses.size)
        assertEquals("Rua Dois, 20 - Centro, Santo Andre - SP", decision.destination)
    }

    @Test
    fun losingOneHouseNumberImmediatelyInvalidatesPreviousDecision() {
        val before = UniversalAddressTrigger.evaluate(
            """
            Rua Um, 10 - Centro, Sao Paulo - SP
            Rua Dois, 20 - Centro, Santo Andre - SP
            """.trimIndent(),
        )
        val after = UniversalAddressTrigger.evaluate(
            """
            Rua Um, 10 - Centro, Sao Paulo - SP
            Rua Dois - Centro, Santo Andre - SP
            """.trimIndent(),
        )

        assertTrue(before.active)
        assertFalse(after.active)
        assertTrue(after.shouldClearPreviousResult)
        assertEquals(1, after.addresses.size)
        assertNull(after.destination)
        assertEquals("", after.addressSignature)
        assertNotEquals(before.screenHash, after.screenHash)
    }

    @Test
    fun pricePhoneTimeDistanceAndRatingNeverSupplyMissingHouseNumbers() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores
            Avenida Brasil
            R$ 35,00
            (11) 99999-8888
            18:30
            12,5 km
            Nota 4,9
            """.trimIndent(),
        )

        assertEquals(0, decision.addresses.size)
        assertFalse(decision.active)
        assertTrue(decision.shouldClearPreviousResult)
    }

    @Test
    fun noNumberMarkersAreRejectedAndNeverParticipateInTrigger() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores, s/n - Centro, Sao Paulo - SP
            Avenida Brasil, SN - Bela Vista, Santo Andre - SP
            Rua Central, sem numero - Centro, Maua - SP
            """.trimIndent(),
        )

        assertEquals(0, decision.addresses.size)
        assertFalse(decision.active)
        assertNull(decision.destination)
    }

    @Test
    fun numberFromAnotherLineIsNeverBorrowedByIncompleteStreet() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores,
            120
            Avenida Brasil
            900
            """.trimIndent(),
        )

        assertEquals(0, decision.addresses.size)
        assertFalse(decision.active)
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
