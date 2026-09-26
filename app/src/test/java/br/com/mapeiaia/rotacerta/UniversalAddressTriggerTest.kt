package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAddressTriggerTest {
    @Test
    fun twoStreetsWithoutNumbersActivateAndUseLastStreetAsDestination() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores
            Avenida Brasil
            """.trimIndent(),
        )

        assertEquals(2, decision.addresses.size)
        assertTrue(decision.active)
        assertFalse(decision.shouldClearPreviousResult)
        assertEquals("Rua das Flores", decision.pickup)
        assertEquals("Avenida Brasil", decision.destination)
    }

    @Test
    fun oneStreetWithoutNumberStaysInactive() {
        val decision = UniversalAddressTrigger.evaluate("Rua das Flores")

        assertEquals(listOf("Rua das Flores"), decision.addresses)
        assertFalse(decision.active)
        assertTrue(decision.shouldClearPreviousResult)
        assertNull(decision.destination)
    }

    @Test
    fun numberedPickupAndUnnumberedDestinationActivateNormally() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores, 120 - Centro, Sao Paulo - SP
            Avenida Brasil - Bela Vista, Santo Andre - SP
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertEquals("Rua das Flores, 120 - Centro, Sao Paulo - SP", decision.pickup)
        assertEquals("Avenida Brasil - Bela Vista, Santo Andre - SP", decision.destination)
    }

    @Test
    fun twoNumberedStreetsStillActivateAndUseLastAddressAsDestination() {
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
    fun unnumberedLastAddressIsNotDiscarded() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua Um, 10 - Centro, Sao Paulo - SP
            Rua Dois, 20 - Centro, Santo Andre - SP
            Rua Tres - Centro, Maua - SP
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertEquals(3, decision.addresses.size)
        assertEquals("Rua Tres - Centro, Maua - SP", decision.destination)
    }

    @Test
    fun losingHouseNumberDoesNotInvalidateRecognizedStreet() {
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
        assertTrue(after.active)
        assertEquals(2, after.addresses.size)
        assertEquals("Rua Dois - Centro, Santo Andre - SP", after.destination)
        assertNotEquals(before.screenHash, after.screenHash)
    }

    @Test
    fun pricePhoneTimeDistanceAndRatingNeverBecomeAdditionalAddresses() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores
            R$ 35,00
            (11) 99999-8888
            18:30
            12,5 km
            Nota 4,9
            Avenida Brasil
            """.trimIndent(),
        )

        assertEquals(listOf("Rua das Flores", "Avenida Brasil"), decision.addresses)
        assertTrue(decision.active)
        assertEquals("Avenida Brasil", decision.destination)
    }

    @Test
    fun noNumberMarkersAreAcceptedAsStreetAddresses() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Rua das Flores, s/n - Centro, Sao Paulo - SP
            Avenida Brasil, SN - Bela Vista, Santo Andre - SP
            """.trimIndent(),
        )

        assertEquals(2, decision.addresses.size)
        assertTrue(decision.active)
        assertEquals("Avenida Brasil, SN - Bela Vista, Santo Andre - SP", decision.destination)
    }

    @Test
    fun galleryOcrWrappedHouseNumbersActivateAndUseLastAddress() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Google Fotos
            Origem
            Rua das Flores,
            120 - Centro, Sao Paulo - SP
            Destino
            Avenida Brasil
            900 - Bela Vista, Santo Andre - SP
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertEquals(2, decision.addresses.size)
        assertEquals("Rua das Flores, 120 - Centro, Sao Paulo - SP", decision.pickup)
        assertEquals("Avenida Brasil 900 - Bela Vista, Santo Andre - SP", decision.destination)
    }

    @Test
    fun inDriveStyleCardAcceptsDestinationWithoutHouseNumber() {
        val decision = UniversalAddressTrigger.evaluate(
            """
            Pedido de viagem
            5,9 km
            10 min
            R$ 14,50
            A Travessa Voa Voa Beija-Flor 37 (Jardim da Conquista)
            B Rua Erundina (Jardim Rodolfo
            Pirani, Sao Paulo - SP)
            Aceitar por R$ 14,50
            """.trimIndent(),
        )

        assertTrue(decision.active)
        assertEquals(2, decision.addresses.size)
        assertEquals("Travessa Voa Voa Beija-Flor 37 (Jardim da Conquista)", decision.pickup)
        assertEquals("Rua Erundina (Jardim Rodolfo Pirani, Sao Paulo - SP)", decision.destination)
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
