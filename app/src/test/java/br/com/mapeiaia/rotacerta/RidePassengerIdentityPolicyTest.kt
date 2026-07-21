package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePassengerIdentityPolicyTest {
    @Test
    fun realSingleNameCardIsAccepted() {
        val decision = RidePassengerIdentityPolicy.evaluate(
            """
                Pedido de viagem
                Rayssa
                4.7
                (465)
                3 min.
                R$ 11
                Rua Angelo Mingotti 59
                Rua Acacio Antunes
            """.trimIndent(),
        )

        assertTrue(decision.accepted)
        assertEquals(listOf("Rayssa"), decision.candidates)
    }

    @Test
    fun fullPassengerNameIsAccepted() {
        val decision = RidePassengerIdentityPolicy.evaluate(
            """
                Nova corrida
                Maria Clara Souza
                4,9
                327 avaliacoes
                Avenida A, 10
                Rua B, 20
            """.trimIndent(),
        )

        assertTrue(decision.accepted)
        assertEquals(listOf("Maria Clara Souza"), decision.candidates)
    }

    @Test
    fun requestListWithSeveralPassengersIsRejected() {
        val decision = RidePassengerIdentityPolicy.evaluate(
            """
                Joao Silva
                4.8
                (120)
                Rua A, 10
                Rua B, 20
                Ana Paula
                4.9
                (450)
                Rua C, 30
                Rua D, 40
            """.trimIndent(),
        )

        assertFalse(decision.accepted)
        assertEquals("varios_passageiros_lista_de_pedidos", decision.reason)
        assertEquals(2, decision.candidates.size)
    }

    @Test
    fun addressAndUiLabelsAreNotPassengerNames() {
        val decision = RidePassengerIdentityPolicy.evaluate(
            """
                Pedido de viagem
                Rua Acacio Antunes
                4.7
                Aceitar por R$ 11
                4.9
                Rua A, 10
                Rua B, 20
            """.trimIndent(),
        )

        assertFalse(decision.accepted)
        assertEquals("passageiro_nao_identificado", decision.reason)
    }
}
