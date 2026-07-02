package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideScreenTextClassifierPassiveScreenTest {
    @Test
    fun ignoresNinetyNineHomeFuelAndRequestsScreen() {
        val text = """
            23:17 99
            R$0,00
            Av. Exemplo
            99 Abastece
            Solicitacoes (1) Nova
            R$3,28 R$3,47
            Rede Exemplo - Posto Central
            Av. Exemplo Norte
            Buscando
            Mais >
        """.trimIndent()

        assertTrue(RideScreenTextClassifier.shouldIgnore(text))
        assertEquals(false, RideScreenTextClassifier.looksLikeRideCard(text))
    }

    @Test
    fun keepsNinetyNineOfferEligibleWhenAcceptActionIsVisible() {
        val text = """
            Negocia · Dinheiro
            R$22,24
            R$2,36/km
            Perfil Premium
            7min (2,3km)
            Avenida Exemplo das Pedras, 4129,
            Jardim Sao Cristovao
            18min (7,1km)
            Travessa Exemplo Baba, 117 , Jardim da
            Conquista
            Aceitar por R$22,24
        """.trimIndent()

        assertEquals(false, RideScreenTextClassifier.shouldIgnore(text))
        assertEquals(true, RideScreenTextClassifier.looksLikeRideCard(text))
    }
}
