package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class UniversalPoiDestinationRegressionTest {
    @Test
    fun establishmentWithStreetInsideParenthesesBecomesSecondDestination() {
        val text = """
            Avenida Juscelino Kubitschek de Oliveira, 1974 (Portal D'oeste, Osasco - SP)
            Pronto Socorro José Agostinho dos Santos Parque Imperial (Rua José Martinho - Parque Imperial, Barueri - SP)
        """.trimIndent()

        val addresses = UniversalScreenAddressParser.findAddresses(text)
        val fields = UniversalScreenAddressParser.parse(text)

        assertEquals(2, addresses.size)
        assertEquals(
            "Pronto Socorro José Agostinho dos Santos Parque Imperial (Rua José Martinho - Parque Imperial, Barueri - SP)",
            addresses.last(),
        )
        assertEquals(addresses.last(), fields.destination)
    }
}
