package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideScreenTextClassifierTest {
    @Test
    fun ignoresFilesByGoogleViewerWhenNoRideTextIsVisible() {
        val text = """
            Screenshot_20260701_165939_Files by Google.jpg
            Editar
            Pesquisar na imagem
            Navegar para cima
            Compartilhar
            Adicionar à pasta "Com estrela"
            Mais opções
        """.trimIndent()

        assertTrue(RideScreenTextClassifier.shouldIgnore(text))
        assertFalse(RideScreenTextClassifier.looksLikeRideCard(text))
    }

    @Test
    fun acceptsInDriverTextWithoutParenthesizedRouteSteps() {
        val text = """
            Pedido de viagem
            Vivian
            5.0
            (30)
            1 min.
            R$ 1,7/km
            ~3,0 km
            R$ 26
            R. Zacarias Alves de Melo, 108 (Jardim Ibitirama)
            Rua Conego Antonio Dias Pequeno, 475 (Jardim Tiete, Sao Paulo - State of Sao Paulo)
            PIX
            Aceitar por R$ 26
        """.trimIndent()

        assertTrue(RideScreenTextClassifier.looksLikeRideCard(text))
    }

    @Test
    fun acceptsRideTextEvenWhenImageViewerToolbarIsVisible() {
        val text = """
            Screenshot_20260701_165939_Files by Google.jpg
            Editar
            Compartilhar
            Pedido de viagem
            R$ 26
            R$ 1,7/km
            ~3,0 km
            R. Zacarias Alves de Melo, 108 (Jardim Ibitirama)
            Rua Conego Antonio Dias Pequeno, 475 (Jardim Tiete, Sao Paulo - State of Sao Paulo)
            Aceitar por R$ 26
        """.trimIndent()

        assertFalse(RideScreenTextClassifier.shouldIgnore(text))
        assertTrue(RideScreenTextClassifier.looksLikeRideCard(text))
    }
}
