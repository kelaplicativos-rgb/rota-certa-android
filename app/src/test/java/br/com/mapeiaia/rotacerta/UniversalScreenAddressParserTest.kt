package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniversalScreenAddressParserTest {
    @Test
    fun oneAddressIsEnoughAndBecomesDestination() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Foto recebida
            Rua das Flores, 120 - Centro, Sao Paulo - SP
            """.trimIndent(),
        )

        assertEquals("Rua das Flores, 120 - Centro, Sao Paulo - SP", fields.destination)
        assertNull(fields.pickup)
    }

    @Test
    fun alwaysUsesLastVisibleAddress() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Origem: Avenida Brasil, 1000 - Centro, Sao Paulo - SP
            Texto qualquer
            Rua das Acacias, 45 - Jardim Azul, Santo Andre - SP
            Destino final: Alameda Santos, 900 - Bela Vista, Sao Paulo - SP
            """.trimIndent(),
        )

        assertEquals("Avenida Brasil, 1000 - Centro, Sao Paulo - SP", fields.pickup)
        assertEquals("Alameda Santos, 900 - Bela Vista, Sao Paulo - SP", fields.destination)
    }

    @Test
    fun appOrScreenTypeDoesNotMatter() {
        val webText = "Confira no navegador: Rodovia Fernao Dias, 1500 - Extrema - MG"
        val photoText = "Imagem\nHospital Modelo, Avenida Central, 55 - Varginha - MG"

        assertEquals(
            "Rodovia Fernao Dias, 1500 - Extrema - MG",
            UniversalScreenAddressParser.parse(webText).destination,
        )
        assertEquals(
            "Hospital Modelo, Avenida Central, 55 - Varginha - MG",
            UniversalScreenAddressParser.parse(photoText).destination,
        )
    }

    @Test
    fun priceDistanceAndButtonsDoNotBecomeAddresses() {
        val fields = UniversalScreenAddressParser.parse(
            """
            R$ 35,00
            12,5 km
            Aceitar por R$ 35
            """.trimIndent(),
        )

        assertNull(fields.destination)
    }
}
