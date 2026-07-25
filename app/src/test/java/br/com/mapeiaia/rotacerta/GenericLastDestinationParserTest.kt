package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class GenericLastDestinationParserTest {
    @Test
    fun usesLastAddressAsDestinationForUnknownRegionalAppText() {
        val text = """
            Oferta de corrida
            R$ 21,90
            6,4 km
            Rua Primeira, 100 - Centro
            Avenida Segunda, 200 - Jardim Brasil
            Rua Final, 300 - Vila Nova
            Aceitar
        """.trimIndent()

        val fields = RideTextParser().parse(text, packageName = "com.regional.qualquer")

        assertEquals("Rua Primeira, 100 - Centro", fields.pickup)
        assertEquals("Rua Final, 300 - Vila Nova", fields.destination)
    }

    @Test
    fun oneAddressUnknownTextIsAlsoPointB() {
        val text = """
            Chamada disponível
            R$ 12,50
            Rua Única, 777 - Centro
            Aceitar
        """.trimIndent()

        val fields = RideTextParser().parse(text, packageName = "com.app.local")

        assertEquals("Rua Única, 777 - Centro", fields.destination)
    }
}
