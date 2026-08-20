package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCardConfirmationPolicy0185Test {
    private val packageName = "sinet.startup.indriver"

    @Test
    fun blocksTheMultiOfferFeedThatPreviouslyMixedPickupAndDestination() {
        val feed = """
            Nova notificação Alice 4.57 7 min. R$ 50
            Rua Sana Mundo 128 (Jardim Eliane)
            CDP de Vila Independência (Avenida Doutor Francisco Mesquita - São Paulo - SP)
            Evely 4.78 6 min. R$ 55
            Rua Laranja da Bahia, 109 (Jardim Fernandes, São Paulo - SP)
            Avenida Marechal Tito, 6577 (Itaim Paulista, São Paulo - SP)
            Claudia 4.81 R$ 22 Rua Barcelos Leite 236
            Decon Assessoria (Rua Engenheiro Pegado - Vila Carrão, São Paulo - SP)
            Mostrar novos pedidos Pedidos de viagem Demanda Desempenho
        """.trimIndent()

        val result = RideCardConfirmationPolicy0185.prepare(packageName, feed)

        assertTrue(result.rejectedFeed)
        assertFalse(result.confirmedIndividualCard)
    }

    @Test
    fun isolatesOnlyTheOpenedIndividualCardFromBackgroundOffers() {
        val screen = """
            Nova notificação Claudia Rua Barcelos Leite 236 Decon Assessoria Rua Engenheiro Pegado
            Pedido de viagem Tenha cuidado nesse percurso Evely 4.78 (594) 6 min.
            R$ 2/km R$ 55 Rua Laranja da Bahia, 109 (Jardim Fernandes, São Paulo - SP)
            Avenida Marechal Tito, 6577 (Itaim Paulista, São Paulo - SP)
            Aceitar por R$ 55 Ofereça sua tarifa R$ 61 R$ 66 Fechar
            Pedidos de viagem Demanda Desempenho
        """.trimIndent()

        val result = RideCardConfirmationPolicy0185.prepare(packageName, screen)

        assertTrue(result.confirmedIndividualCard)
        assertFalse(result.rejectedFeed)
        assertTrue(result.analysisText.startsWith("Pedido de viagem"))
        assertTrue(result.analysisText.endsWith("Fechar"))
        assertTrue("Avenida Marechal Tito" in result.analysisText)
        assertFalse("Decon Assessoria" in result.analysisText)
        assertFalse("Pedidos de viagem Demanda" in result.analysisText)
    }

    @Test
    fun acceptsFlattenedIndividualCardTextFromAccessibilityOrOcr() {
        val card = "Pedido de viagem Alice Rua Sana Mundo 128 CDP de Vila Independência Avenida Doutor Francisco Mesquita Aceitar por R$ 50 Ofereca sua tarifa R$ 55 Fechar"

        val result = RideCardConfirmationPolicy0185.prepare(packageName, card)

        assertTrue(result.confirmedIndividualCard)
        assertFalse(result.rejectedFeed)
    }

    @Test
    fun leavesOtherSelectedApplicationsForTheirExistingParser() {
        val text = "Rua A, 10\nRua B, 20"

        val result = RideCardConfirmationPolicy0185.prepare("com.app99.driver", text)

        assertFalse(result.rejectedFeed)
        assertTrue(result.confirmedIndividualCard)
        assertTrue(result.analysisText == text)
    }
}
