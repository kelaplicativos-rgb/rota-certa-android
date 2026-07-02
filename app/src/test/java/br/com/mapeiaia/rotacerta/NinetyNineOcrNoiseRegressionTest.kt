package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NinetyNineOcrNoiseRegressionTest {
    @Test
    fun acceptsNinetyNineCardWithOcrZeroFareAndConnectNoise() {
        val text = """
            14:52 99
            SAPOPEMBA
            R$O,00
            PASSAGEIRO TESTE
            Av. Afons
            FAÇA UMA
            GRANA EXTRA
            Indicando um motora que
            parou de correr com a 99
            R. Oratório Conectar Unidade Básic
            de Saúde - Jardim S
            6.!55
            Cavid eta
            R$ 29,99
            R$2.0km
        """.trimIndent()

        assertTrue(RideScreenTextClassifier.looksLikeRideCard(text))

        val fields = RideTextParser().parse(text, "com.app99.driver")

        assertEquals("Av. Afons", fields.pickup)
        assertEquals("R. Oratório Unidade Básic de Saúde - Jardim S", fields.destination)
        assertEquals("R$ 29,99", fields.fare)
        assertFalse(fields.pickup.orEmpty().contains("R$"))
        assertFalse(fields.destination.orEmpty().contains("Conectar", ignoreCase = true))
    }
}
