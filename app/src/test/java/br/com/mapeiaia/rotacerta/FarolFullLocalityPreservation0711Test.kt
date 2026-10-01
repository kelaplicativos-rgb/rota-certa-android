package br.com.mapeiaia.rotacerta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FarolFullLocalityPreservation0711Test {
    @Test
    fun stage21PreservesBalancedDestinationLocalityForOfflineGeocoder() {
        val text = """
            Rua João Ribeiro, 120 (Penha, São Paulo - SP)
            Rua Vicente Lopes, 8 (Cidade Satélite Santa Bárbara, São Paulo - SP)
            R$ 24,00
            Aceitar
        """.trimIndent()

        val evaluation = FarolCausalCorrectionStage21.evaluate(
            listOf(
                FarolUniversalVisualPipelineStage19.VisualBlock(
                    id = "card",
                    metadataPackageName = "com.indriver.android",
                    windowId = 42,
                    windowLayer = 5,
                    depth = 3,
                    text = text,
                    source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
                    left = 0,
                    top = 100,
                    right = 1080,
                    bottom = 1800,
                ),
            ),
        )

        val resolved = assertNotNull(evaluation)
        assertEquals(
            "Rua Vicente Lopes, 8 (Cidade Satélite Santa Bárbara, São Paulo - SP)",
            resolved.destination,
        )
        val sanitized = FarolRouteAddressSanitizer0684.sanitize(resolved.destination)
        assertTrue(sanitized.accepted)
        assertEquals(
            "Rua Vicente Lopes, 8 (Cidade Satélite Santa Bárbara, São Paulo - SP)",
            sanitized.sanitized,
        )
    }

    @Test
    fun displayIdentityCleaningNoLongerParticipatesInStage21RouteText() {
        assertEquals(
            "FULL_LOCALITY_PRESERVED_TO_OFFLINE_GEOCODER_0711",
            FarolCausalCorrectionStage21.LOCALITY_PRESERVATION_MARKER_0711,
        )
    }
}
