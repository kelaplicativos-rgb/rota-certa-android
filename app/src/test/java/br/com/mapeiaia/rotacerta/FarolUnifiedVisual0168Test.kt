package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolUnifiedVisual0168Test {
    @Test
    fun truncatedStreetNeverBecomesRouteDestination() {
        assertTrue(FarolUnifiedVisual0168.isClearlyTruncatedStreet("Rua Joaquim"))
        assertFalse(FarolUnifiedVisual0168.isClearlyTruncatedStreet("Rua Joaquim Meira de Siqueira, 260, São Paulo - SP"))
    }

    @Test
    fun namedPickupInsideConfirmedCardIsAccepted() {
        assertTrue(FarolUnifiedVisual0168.isNamedPlaceWithLocation("Casa Vip (Cidade São Mateus)"))
        assertTrue(FarolUnifiedVisual0168.isNamedPlaceWithLocation("G M Hotel (Jardim Três Marias, São Paulo - SP)"))
        assertFalse(FarolUnifiedVisual0168.isNamedPlaceWithLocation("Dagmar 4.81 (271)"))
    }

    @Test
    fun countdownDoesNotCreateANewSemanticCard() {
        val first = "Pedido de viagem 38 seg. Rua A, 10 Rua B, 20 Aceitar por R$ 36"
        val second = "Pedido de viagem 51 seg. Rua A, 10 Rua B, 20 Aceitar por R$ 45"
        assertEquals(
            FarolUnifiedVisual0168.semanticSignature(first),
            FarolUnifiedVisual0168.semanticSignature(second),
        )
    }

    @Test
    fun listCardsAreNotMixed() {
        val text = """
            Pedido de viagem
            Hotel Alfa (Centro, São Paulo - SP)
            Rua A, 10, São Paulo - SP
            Aceitar por R$ 30
            Pular
            Pedido de viagem
            Hotel Beta (Vila Mariana, São Paulo - SP)
            Rua B, 20, São Paulo - SP
            Aceitar por R$ 40
            Pular
        """.trimIndent()
        val selected = FarolUnifiedVisual0168.normalizeForAnalysis(text)
        assertTrue(selected.contains("Pedido de viagem"))
        assertFalse(selected.contains("Rua A, 10") && selected.contains("Rua B, 20"))
    }

    @Test
    fun incompleteOcrLineIsRemovedBeforeParser() {
        val normalized = FarolUnifiedVisual0168.normalizeForAnalysis(
            "Nova solicitação\nRua Joaquim\nAceitar",
        )
        assertFalse(normalized.lines().any { it.trim() == "Rua Joaquim" })
    }
}
