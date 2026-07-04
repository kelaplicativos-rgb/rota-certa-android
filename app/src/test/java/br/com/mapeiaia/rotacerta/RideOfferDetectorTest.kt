package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideOfferDetectorTest {
    @Test
    fun rejectsRideCardMixedWithAndroidPermissionScreen() {
        val text = """
            Pedido de viagem
            R$ 10
            R$ 2,9/km
            ~839 m
            Rua Euchario Reboucas de Carvalho 76
            Rua Rio Bonito, 1060
            Aceitar por R$ 10
            Ofereça sua tarifa
            Permissões do app
            Abrir Rota Certa
            Salvar card de corrida
            Configurações de apps não usados
        """.trimIndent()
        val fields = RideFields(
            pickup = "Rua Euchario Reboucas de Carvalho 76",
            destination = "Rua Rio Bonito, 1060",
            fare = "R$ 10",
        )

        assertFalse(RideOfferDetector.looksLikeRideOffer(text, fields, "sinet.startup.indriver"))
    }

    @Test
    fun acceptsCleanRegisteredRideOfferTextShape() {
        val text = """
            Pedido de viagem
            R$ 10
            R$ 2,9/km
            ~839 m
            Rua Euchario Reboucas de Carvalho 76
            Rua Rio Bonito, 1060
            Aceitar por R$ 10
            Ofereça sua tarifa
        """.trimIndent()
        val fields = RideFields(
            pickup = "Rua Euchario Reboucas de Carvalho 76",
            destination = "Rua Rio Bonito, 1060",
            fare = "R$ 10",
        )

        assertTrue(RideOfferDetector.looksLikeRideOffer(text, fields, "sinet.startup.indriver"))
    }
}
