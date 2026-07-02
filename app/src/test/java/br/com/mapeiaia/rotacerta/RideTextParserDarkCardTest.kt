package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class RideTextParserDarkCardTest {
    @Test
    fun parsesNinetyNineDarkCardWithWrappedConnectorNeighborhood() {
        val text = """
            Negocia · Dinheiro
            R$22,24
            R$2,36/km
            4,93 · 219 corridas
            Perfil Premium
            7min (2,3km)
            Avenida Exemplo das Pedras, 4129,
            Jardim Sao Cristovao
            18min (7,1km)
            Travessa Exemplo Baba, 117 , Jardim da
            Conquista
            Aceitar por R$22,24
            R$23,35
            R$24,02
            R$24,46
        """.trimIndent()

        val fields = RideTextParser().parse(text, "com.app99.driver")

        assertEquals("Avenida Exemplo das Pedras, 4129, Jardim Sao Cristovao", fields.pickup)
        assertEquals("Travessa Exemplo Baba, 117 , Jardim da Conquista", fields.destination)
        assertEquals("R$22,24", fields.fare)
        assertEquals("2,3km", fields.distance)
        assertEquals("7min", fields.time)
    }
}
