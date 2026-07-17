package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalScreenAddressParserTest {
    @Test
    fun oneCompleteAddressIsDetectedButDoesNotCreatePickup() {
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
    fun alwaysUsesLastCompleteNumberedAddress() {
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
    fun acceptedHouseNumberFormatsAreNormalizedAsValidAddresses() {
        val validAddresses = listOf(
            "Rua X, 123",
            "Rua X nº 123",
            "Rua X numero 123",
            "Rua X, 123-A",
            "Rua X, 123 bloco B",
        )

        validAddresses.forEach { address ->
            assertTrue(address, UniversalScreenAddressParser.isCompleteNumberedAddress(address))
            assertEquals(listOf(address), UniversalScreenAddressParser.findAddresses(address))
        }
    }

    @Test
    fun appOrScreenTypeDoesNotMatterWhenStreetAndNumberArePresent() {
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
    fun incompleteStreetCepAndNoNumberMarkersAreRejected() {
        val invalidAddresses = listOf(
            "Rua das Flores",
            "Avenida Brasil - Centro",
            "Rua das Flores, s/n",
            "Avenida Brasil, SN",
            "Rua Central, sem numero",
            "Rua das Flores, 09000-000",
        )

        invalidAddresses.forEach { address ->
            assertFalse(address, UniversalScreenAddressParser.isCompleteNumberedAddress(address))
            assertEquals(emptyList<String>(), UniversalScreenAddressParser.findAddresses(address))
        }
    }

    @Test
    fun numberOnAnotherLineIsNotBorrowed() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            """
            Rua das Flores,
            120
            """.trimIndent(),
        )

        assertEquals(emptyList<String>(), addresses)
    }

    @Test
    fun priceDistancePhoneTimeAndButtonsDoNotBecomeAddresses() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Rua das Flores
            R$ 35,00
            (11) 99999-8888
            18:30
            12,5 km
            Aceitar por R$ 35
            """.trimIndent(),
        )

        assertNull(fields.destination)
    }

    @Test
    fun datesFilesAndAndroidUiNeverBecomeAddresses() {
        val fields = UniversalScreenAddressParser.parse(
            """
            qua., 15 de jul.
            14 de jul., 95,66 kB, Documento em PDF
            rota-certa-relatorio-falha (8).txt
            Radares importados (0)
            Wi-Fi
            Bluetooth
            Planos de fundo
            """.trimIndent(),
        )

        assertNull(fields.destination)
    }

    @Test
    fun ownSettingsAndSavedGpsLabelsNeverBecomeAddresses() {
        val fields = UniversalScreenAddressParser.parse(
            """
            GPS salvo: -21,37907, -46,16011
            Configuracoes
            Aparencia da bolinha
            Leitura ao vivo ativa
            Backup dos dados
            """.trimIndent(),
        )

        assertNull(fields.destination)
    }

    @Test
    fun realAddressStillWinsAfterNoisyLines() {
        val fields = UniversalScreenAddressParser.parse(
            """
            qua., 15 de jul.
            Documento em PDF
            R$ 42,00
            Rua Doutor Paulo, 77 - Centro, Tres Coracoes - MG
            """.trimIndent(),
        )

        assertEquals("Rua Doutor Paulo, 77 - Centro, Tres Coracoes - MG", fields.destination)
    }
}
