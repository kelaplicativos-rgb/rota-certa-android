package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePassengerUnicodeRegressionTest {
    @Test
    fun decomposedAccentFromRealInDriveAccessibilityCardIsAccepted() {
        val decomposedName = "Lu\u0301cio Ramos"
        val card = """
            Mapa do Google
            Pedido de viagem
            $decomposedName
            5.0
            (4)
            42 seg.
            R$ 1,6/km
            ~3,3 km
            R$ 92
            Preço justo
            Rua Normanda 61 (Parque Capuava)
            R. Pomerânia, 100 (Interlagos, São Paulo - SP, 04783-120)
            Maquininha de cartão
            Aceitar por R$ 92
            Ofereça sua tarifa
            R$ 102
            R$ 110
            Fechar
        """.trimIndent()

        val decision = RidePassengerIdentityPolicy.evaluate(card)

        assertTrue("O passageiro real com acento Unicode decomposto deve liberar o card", decision.accepted)
        assertEquals("um_passageiro_identificado", decision.reason)
        assertEquals(
            listOf(Normalizer.normalize(decomposedName, Normalizer.Form.NFC)),
            decision.candidates,
        )
    }
}
