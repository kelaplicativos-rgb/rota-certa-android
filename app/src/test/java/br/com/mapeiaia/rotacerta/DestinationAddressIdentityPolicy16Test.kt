package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationAddressIdentityPolicy16Test {
    @Test
    fun removesUnmatchedWrappersFromDestinationButPreservesLocalityContinuation() {
        assertEquals(
            "Avenida Lucas Nogueira",
            DestinationAddressIdentityPolicy.cleanDisplayAddress(" ((Avenida Lucas Nogueira, "),
        )
        assertEquals(
            "Avenida Lucas Nogueira",
            DestinationAddressIdentityPolicy.cleanParserSegment("(Avenida Lucas Nogueira"),
        )
        assertEquals(
            "(Cidade Lider)",
            DestinationAddressIdentityPolicy.cleanParserSegment("(Cidade Lider)"),
        )
    }

    @Test
    fun partialAndCompleteVersionsOfTheSameDestinationAreCompatible() {
        val partial = DestinationAddressIdentityPolicy.signature(
            "com.exemplo.qualquer",
            "(Avenida Lucas Nogueira",
        )
        val complete = DestinationAddressIdentityPolicy.signature(
            "com.exemplo.qualquer",
            "Avenida Lucas Nogueira, 123 - Sao Paulo",
        )

        assertTrue(DestinationAddressIdentityPolicy.sameDestinationSignatures(partial, complete))
    }

    @Test
    fun confirmedDifferentHouseNumbersAreDifferentDestinations() {
        val first = DestinationAddressIdentityPolicy.identity("Avenida Lucas Nogueira, 123")
        val second = DestinationAddressIdentityPolicy.identity("Avenida Lucas Nogueira, 456")

        assertFalse(DestinationAddressIdentityPolicy.areCompatible(first, second))
    }


    @Test
    fun sameStreetAndNumberSurviveCorruptedOcrSuffix() {
        val complete = DestinationAddressIdentityPolicy.signature(
            "visual",
            "Rua Leopoldo Delisle, 555 (Jardim Sao Vicente, São Paulo - SP)",
        )
        val short = DestinationAddressIdentityPolicy.signature(
            "visual",
            "Rua Leopoldo Delisle, 555",
        )
        val contaminated = DestinationAddressIdentityPolicy.signature(
            "visual",
            "Rua Leopoldo Delisle, 555 (Jardim Sao Vicente, São Pedidos de viagem",
        )

        assertTrue(DestinationAddressIdentityPolicy.sameDestinationSignatures(complete, short))
        assertTrue(DestinationAddressIdentityPolicy.sameDestinationSignatures(complete, contaminated))
        assertTrue(DestinationAddressIdentityPolicy.sameDestinationSignatures(short, contaminated))
    }

    @Test
    fun sameStreetWithDifferentConfirmedNumberNeverCoalesces() {
        val first = DestinationAddressIdentityPolicy.signature("visual", "Rua Leopoldo Delisle, 555")
        val second = DestinationAddressIdentityPolicy.signature("visual", "Rua Leopoldo Delisle, 556")
        assertFalse(DestinationAddressIdentityPolicy.sameDestinationSignatures(first, second))
    }

    @Test
    fun pickupChangesDoNotChangeDestinationSignature() {
        val first = SimpleSavedAppFarolPolicy.evaluate(
            packageName = "com.exemplo.qualquer",
            savedPackages = setOf("com.exemplo.qualquer"),
            text = "Rua das Flores, 10\n(Avenida Lucas Nogueira",
        )
        val second = SimpleSavedAppFarolPolicy.evaluate(
            packageName = "com.exemplo.qualquer",
            savedPackages = setOf("com.exemplo.qualquer"),
            text = "Rua dos Pinheiros, 20\nAvenida Lucas Nogueira, 123 - Sao Paulo",
        )

        assertTrue(first.active)
        assertTrue(second.active)
        assertEquals("Avenida Lucas Nogueira", first.destination)
        assertTrue(
            DestinationAddressIdentityPolicy.sameDestinationSignatures(
                first.addressSignature,
                second.addressSignature,
            ),
        )
    }

    @Test
    fun compatiblePartialDestinationKeepsTheCurrentBubble() {
        val previous = DestinationAddressIdentityPolicy.signature(
            "com.exemplo.qualquer",
            "Avenida Lucas Nogueira",
        )
        val current = DestinationAddressIdentityPolicy.signature(
            "com.exemplo.qualquer",
            "Avenida Lucas Nogueira, 123 - Sao Paulo",
        )

        assertEquals(
            FarolDisplayStabilityPolicy.Action.KeepCurrent,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.exemplo.qualquer",
                previousWindowId = 1,
                activeAddressSignature = previous,
                currentPackageName = "com.exemplo.qualquer",
                currentWindowId = 4,
                currentAddressSignature = current,
                hasTwoAddresses = true,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOWS_CHANGED,
            ),
        )
    }

    @Test
    fun materiallyDifferentDestinationStillClearsBeforeProcessing() {
        val previous = DestinationAddressIdentityPolicy.signature(
            "com.exemplo.qualquer",
            "Avenida Lucas Nogueira, 123",
        )
        val current = DestinationAddressIdentityPolicy.signature(
            "com.exemplo.qualquer",
            "Avenida Lucas Nogueira, 456",
        )

        assertEquals(
            FarolDisplayStabilityPolicy.Action.ClearThenProcess,
            FarolDisplayStabilityPolicy.decide(
                previousPackageName = "com.exemplo.qualquer",
                previousWindowId = 1,
                activeAddressSignature = previous,
                currentPackageName = "com.exemplo.qualquer",
                currentWindowId = 1,
                currentAddressSignature = current,
                hasTwoAddresses = true,
                eventType = AccessibilityEventFloodGate.TYPE_WINDOW_CONTENT_CHANGED,
            ),
        )
    }
}
