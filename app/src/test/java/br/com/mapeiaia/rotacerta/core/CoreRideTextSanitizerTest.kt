package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRideTextSanitizerTest {
    @Test
    fun inDriveRemovesRatingAndPerKmNoiseButKeepsRouteAndAction() {
        val raw = """
            Pedido de viagem
            4,9 (346)
            Agora mesmo
            R$2,03/km
            2,3 km
            Cidade Sao Mateus
            Rua Sao Miguel do Guama, 43
            Aceitar por R$ 10
            Ofereca sua tarifa
        """.trimIndent()

        val sanitized = CoreRideTextSanitizer.sanitize(raw, RideCardTemplateMatcher.INDRIVE_PACKAGE)

        assertFalse(sanitized.contains("346"))
        assertFalse(sanitized.contains("Agora mesmo", ignoreCase = true))
        assertFalse(sanitized.contains("R$2,03/km"))
        assertTrue(sanitized.contains("2,3 km"))
        assertTrue(sanitized.contains("Aceitar por R$ 10"))
        assertTrue(sanitized.contains("Rua Sao Miguel do Guama"))
    }

    @Test
    fun duplicateLinesWithFormattingDifferencesCollapseToOne() {
        val raw = """
            Aceitar por R$ 10
            ACEITAR POR R$10
            Cidade Sao Mateus
            Cidade São Mateus
        """.trimIndent()

        val sanitized = CoreRideTextSanitizer.sanitize(raw, RideCardTemplateMatcher.INDRIVE_PACKAGE)

        assertTrue(sanitized.lines().size == 2)
    }
}
