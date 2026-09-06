package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerValueFormatterTest {
    @Test
    fun extractsProvidedBlaBlaCarExample() {
        val raw = """
            José Paulo
            5/5 - 1 avaliação
            2 lugares
            São Tomé das Letras → São Paulo
            R$
            204
            ,00
            O preço que você definir é definitivo.
            Enviar mensagem via BlaBlaCar
            Ligar
            Reportar que vocês não compartilharam a viagem
            Buscar José Paulo
        """.trimIndent()
        val data = requireNotNull(PassengerValueFormatter.extract(raw))
        assertEquals("José Paulo", data.passengerName)
        assertEquals(2, data.seats)
        assertEquals("São Tomé das Letras", data.origin)
        assertEquals("São Paulo", data.destination)
        assertEquals(20_400L, data.amountCents)
        assertEquals(
            "Olá, José Paulo! O valor exibido para sua reserva de 2 lugares, de São Tomé das Letras para São Paulo, é R$ 204,00.",
            PassengerValueFormatter.format(data),
        )
    }

    @Test
    fun acceptsSingleLineAmountAndOneSeat() {
        val text = """
            Ana Clara
            1 lugar
            Campinas -> São Paulo
            R$ 90,50
            Buscar Ana Clara
        """.trimIndent()
        assertEquals(
            "Olá, Ana Clara! O valor exibido para sua reserva de 1 lugar, de Campinas para São Paulo, é R$ 90,50.",
            PassengerValueFormatter.extractAndFormat(text),
        )
    }

    @Test
    fun ratingAndSeatCountAreNeverMoney() {
        assertNull(PassengerValueFormatter.extract("José\n5/5 - 1 avaliação\n2 lugares\nA → B"))
    }

    @Test
    fun refusesAmbiguousAmounts() {
        val text = """
            José Paulo
            2 lugares
            São Tomé das Letras → São Paulo
            R$ 204,00
            R$ 102,00
            Buscar José Paulo
        """.trimIndent()
        assertNull(PassengerValueFormatter.extract(text))
    }

    @Test
    fun refusesMissingEssentialField() {
        assertNull(PassengerValueFormatter.extract("José Paulo\n2 lugares\nR$ 204,00"))
    }

    @Test
    fun identityIgnoresAccentsAndCase() {
        val first = PassengerValueData("José Paulo", 2, "São Tomé", "São Paulo", 20_400)
        val second = PassengerValueData("JOSE PAULO", 2, "Sao Tome", "sao paulo", 20_400)
        assertEquals(PassengerValueFormatter.normalizedIdentity(first), PassengerValueFormatter.normalizedIdentity(second))
        assertTrue(PassengerValueFormatter.formatCurrency(125_000).contains("1.250,00"))
    }
}
