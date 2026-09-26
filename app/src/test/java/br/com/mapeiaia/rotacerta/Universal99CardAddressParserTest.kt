package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Universal99CardAddressParserTest {
    @Test
    fun essentialCardJoinsWrappedAvenueAndAcceptsNamedCondominiumDestination() {
        val text = """
            R$2,08/km
            4,76 - 493 corridas
            Perfil Essencial
            9min (1,3km) Area de risco
            Yogui Stlo e Sports, Avenida Mateo
            Bei, 2651 - Cidade Sao Mateus
            9min (2,9km)
            Condominio Parque Residencial
            Santa Barbara, Cidade Satelite San..
        """.trimIndent()

        val addresses = UniversalScreenAddressParser.findAddresses(text)
        val fields = UniversalScreenAddressParser.parse(text)

        assertEquals(
            listOf(
                "Avenida Mateo Bei, 2651 - Cidade Sao Mateus",
                "Condominio Parque Residencial Santa Barbara, Cidade Satelite San",
            ),
            addresses,
        )
        assertEquals("Avenida Mateo Bei, 2651 - Cidade Sao Mateus", fields.pickup)
        assertEquals(
            "Condominio Parque Residencial Santa Barbara, Cidade Satelite San",
            fields.destination,
        )
    }

    @Test
    fun premiumCardJoinsBothWrappedAddresses() {
        val text = """
            R$ 10,82
            6min (773m) Area de risco
            Lojas Barracao Sao Mateus, Avenida Mateo
            Bei, 3220 - Cidade Sao Mateus
            12min (3,8km)
            Rua Ana Santesso, 373, Jardim Sao
            Jose (Sao Mateus)
        """.trimIndent()

        val fields = UniversalScreenAddressParser.parse(text)

        assertEquals("Avenida Mateo Bei, 3220 - Cidade Sao Mateus", fields.pickup)
        assertEquals(
            "Rua Ana Santesso, 373, Jardim Sao Jose (Sao Mateus)",
            fields.destination,
        )
    }

    @Test
    fun genericCondominiumUiTextWithoutNameOrLocalityIsRejected() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            """
            Condominio
            Perfil Essencial
            Area de risco
            Fechar
            """.trimIndent(),
        )

        assertTrue(addresses.isEmpty())
    }
}
