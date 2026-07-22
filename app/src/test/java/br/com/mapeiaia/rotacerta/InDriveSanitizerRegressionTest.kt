package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InDriveSanitizerRegressionTest {
    @Test
    fun ignoresPassengerCountsAndParsesFirstInDriveListOffer() {
        val text = """
            Nova notificação
            Ative outras tarifas para ver mais pedidos de viagem
            Configurar tarifas
            Passageiro Um
            4.87
            (346)
            Agora mesmo
            R$ 2,9/km
            ~2,1 km
            R$ 41
            Preço justo
            Travessa Exemplo 31 (Jardim Modelo)
            Restaurante Modelo (Bairro Centro, São Paulo - State of São Paulo)
            Reclamar
            Ocultar
            Escolher no mapa
            Passageiro Dois
            4.8
            (114)
            5 min.
            R$ 2,7/km
            ~3,1 km
            R$ 26,50
            Rua Segunda 81 (Parque Modelo)
            Av. Exemplo, 1012 (São Paulo - SP)
            Pedidos de viagem
            Demanda
            Desempenho
        """.trimIndent()

        val parse = RideTextParser().parseWithMetadata(text, "sinet.startup.indriver")

        assertEquals("indrive-order-card", parse.parserName)
        assertEquals("Travessa Exemplo 31 (Jardim Modelo)", parse.fields.pickup)
        assertEquals("Restaurante Modelo (Bairro Centro, São Paulo - State of São Paulo)", parse.fields.destination)
        assertEquals("R$ 41", parse.fields.fare)
        assertEquals("2,1 km", parse.fields.distance)
    }

    @Test
    fun acceptsInDriveOfferWithPlaceDestination() {
        val text = """
            Pedidos de viagem
            Passageiro Um
            4.87
            (346)
            Agora mesmo
            R$ 2,9/km
            ~2,1 km
            R$ 41
            Preço justo
            Travessa Exemplo 31 (Jardim Modelo)
            Restaurante Modelo (Bairro Centro, São Paulo - State of São Paulo)
        """.trimIndent()
        val fields = RideTextParser().parse(text, "sinet.startup.indriver")

        assertTrue(RideOfferDetector.looksLikeRideOffer(text, fields, "sinet.startup.indriver"))
    }
}
