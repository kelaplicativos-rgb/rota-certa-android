package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolPairDestinationAuthority0749Test {
    private fun evaluation(
        addresses: List<String>,
        windowId: Int = 7,
    ): FarolUniversalVisualPipelineStage19.Evaluation {
        val destination = addresses.last()
        return FarolUniversalVisualPipelineStage19.Evaluation(
            windowId = windowId,
            blockId = "test",
            source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
            analysisText = addresses.joinToString("\n"),
            addresses = addresses,
            pickup = addresses.first(),
            destination = destination,
            addressSignature = DestinationAddressIdentityPolicy.signature("visual", destination),
            screenHash = addresses.hashCode(),
        )
    }

    @Test fun freshPartialPickupCannotReplaceProvenDestinationB() {
        val previous = evaluation(
            listOf(
                "Avenida dos Latinos 1009 (Jardim Santa Teresinha)",
                "Rua Beckman 300 (Utinga)",
            ),
        )
        val candidate = evaluation(listOf("Avenida dos Latinos 1009 (Jardim Santa Teresinha)"))
        assertTrue(
            FarolPairDestinationAuthority0749.evaluate(
                previousPair = previous,
                candidate = candidate,
                samePackage = true,
                previousPairAtElapsedMillis = 1_000L,
                nowElapsedMillis = 1_900L,
            ).suppressCandidate,
        )
    }

    @Test fun singleObservationOfDestinationBRemainsAllowed() {
        val previous = evaluation(listOf("Avenida dos Latinos 1009", "Rua Beckman 300"))
        val candidate = evaluation(listOf("Rua Beckman 300"))
        assertFalse(
            FarolPairDestinationAuthority0749.evaluate(
                previousPair = previous,
                candidate = candidate,
                samePackage = true,
                previousPairAtElapsedMillis = 1_000L,
                nowElapsedMillis = 1_500L,
            ).suppressCandidate,
        )
    }

    @Test fun oldPairCannotBlockARealLaterCard() {
        val previous = evaluation(listOf("Avenida dos Latinos 1009", "Rua Beckman 300"))
        val candidate = evaluation(listOf("Avenida dos Latinos 1009"))
        assertFalse(
            FarolPairDestinationAuthority0749.evaluate(
                previousPair = previous,
                candidate = candidate,
                samePackage = true,
                previousPairAtElapsedMillis = 1_000L,
                nowElapsedMillis = 5_000L,
            ).suppressCandidate,
        )
    }
}
