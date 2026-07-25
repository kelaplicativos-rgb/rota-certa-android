package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleSavedAppFarolPolicyTest {
    @Test
    fun savedAppWithTwoAddressesUsesTheLastOneImmediately() {
        val result = SimpleSavedAppFarolPolicy.evaluate(
            packageName = "sinet.startup.indriver",
            savedPackages = setOf("sinet.startup.indriver"),
            text = """
                Pedido de viagem
                Rua Maria Amália Lopes de Azevedo, 100
                Avenida Mateo Bei, 3518
                R$ 31
            """.trimIndent(),
        )

        assertTrue(result.active)
        assertEquals(2, result.addresses.size)
        assertEquals("Rua Maria Amália Lopes de Azevedo, 100", result.pickup)
        assertEquals("Avenida Mateo Bei, 3518", result.destination)
        assertTrue(result.addressSignature.startsWith("sinet.startup.indriver|"))
    }

    @Test
    fun modelPassengerPriceAndCardPhrasesAreNotRequired() {
        val result = SimpleSavedAppFarolPolicy.evaluate(
            packageName = "com.app99.driver",
            savedPackages = setOf("com.app99.driver"),
            text = "Rua A, 10\nRua B, 20",
        )
        assertTrue(result.active)
        assertEquals("Rua B, 20", result.destination)
    }

    @Test
    fun unsavedAppOrOneAddressNeverStartsRoute() {
        assertFalse(
            SimpleSavedAppFarolPolicy.evaluate(
                packageName = "sinet.startup.indriver",
                savedPackages = emptySet(),
                text = "Rua A, 10\nRua B, 20",
            ).active,
        )
        assertFalse(
            SimpleSavedAppFarolPolicy.evaluate(
                packageName = "sinet.startup.indriver",
                savedPackages = setOf("sinet.startup.indriver"),
                text = "Rua A, 10",
            ).active,
        )
    }

    @Test
    fun anyRealScreenTextChangeProducesANewFingerprint() {
        val first = SimpleSavedAppFarolPolicy.screenFingerprint(
            "sinet.startup.indriver",
            "Rua A, 10\nRua B, 20",
            4,
        )
        val second = SimpleSavedAppFarolPolicy.screenFingerprint(
            "sinet.startup.indriver",
            "Rua A, 10\nRua C, 30",
            4,
        )
        assertNotEquals(first, second)
        assertTrue(SimpleSavedAppFarolPolicy.changed(first, second))
        assertFalse(SimpleSavedAppFarolPolicy.changed(first, first))
    }

    @Test
    fun manualCaptureCanTeachExternalAppButNotSystemOrOwnApp() {
        assertEquals(
            "sinet.startup.indriver",
            SimpleSavedAppFarolPolicy.teachablePackage(
                "sinet.startup.indriver",
                "br.com.mapeiaia.rotacerta",
            ),
        )
        assertNull(SimpleSavedAppFarolPolicy.teachablePackage("android", "br.com.mapeiaia.rotacerta"))
        assertNull(
            SimpleSavedAppFarolPolicy.teachablePackage(
                "br.com.mapeiaia.rotacerta",
                "br.com.mapeiaia.rotacerta",
            ),
        )
    }
}
