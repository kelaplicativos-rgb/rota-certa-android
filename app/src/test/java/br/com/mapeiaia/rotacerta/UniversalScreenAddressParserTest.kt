package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalScreenAddressParserTest {
    @Test
    fun oneStreetWithoutNumberIsDetectedButDoesNotCreatePickup() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Foto recebida
            Rua das Flores - Centro, Sao Paulo - SP
            """.trimIndent(),
        )

        assertEquals("Rua das Flores - Centro, Sao Paulo - SP", fields.destination)
        assertNull(fields.pickup)
    }

    @Test
    fun alwaysUsesLastRecognizedAddressWithOrWithoutNumber() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Origem: Avenida Brasil, 1000 - Centro, Sao Paulo - SP
            Texto qualquer
            Rua das Acacias, 45 - Jardim Azul, Santo Andre - SP
            Destino final: Alameda Santos - Bela Vista, Sao Paulo - SP
            """.trimIndent(),
        )

        assertEquals("Avenida Brasil, 1000 - Centro, Sao Paulo - SP", fields.pickup)
        assertEquals("Alameda Santos - Bela Vista, Sao Paulo - SP", fields.destination)
    }

    @Test
    fun acceptedHouseNumberFormatsRemainValidNumberedAddresses() {
        val validAddresses = listOf(
            "Rua X, 123",
            "Rua X nº 123",
            "Rua X numero 123",
            "Rua X, 123-A",
            "Rua X, 123 bloco B",
        )

        validAddresses.forEach { address ->
            assertTrue(address, UniversalScreenAddressParser.isCompleteNumberedAddress(address))
            assertTrue(address, UniversalScreenAddressParser.isRecognizedAddress(address))
            assertEquals(listOf(address), UniversalScreenAddressParser.findAddresses(address))
        }
    }

    @Test
    fun streetsWithoutNumberAndNoNumberMarkersAreRecognized() {
        val addresses = listOf(
            "Rua das Flores",
            "Avenida Brasil - Centro",
            "Rua das Flores, s/n",
            "Avenida Brasil, SN",
            "Rua Central, sem numero",
            "Rua Erundina (Jardim Rodolfo Pirani, Sao Paulo - SP)",
        )

        addresses.forEach { address ->
            assertFalse(address, UniversalScreenAddressParser.isCompleteNumberedAddress(address))
            assertTrue(address, UniversalScreenAddressParser.isRecognizedAddress(address))
            assertEquals(listOf(address), UniversalScreenAddressParser.findAddresses(address))
        }
    }

    @Test
    fun appOrScreenTypeDoesNotMatterWhenStreetIsRecognizable() {
        val webText = "Confira no navegador: Rodovia Fernao Dias - Extrema - MG"
        val photoText = "Imagem\nHospital Modelo, Avenida Central - Varginha - MG"

        assertEquals(
            "Rodovia Fernao Dias - Extrema - MG",
            UniversalScreenAddressParser.parse(webText).destination,
        )
        assertEquals(
            "Hospital Modelo, Avenida Central - Varginha - MG",
            UniversalScreenAddressParser.parse(photoText).destination,
        )
    }

    @Test
    fun numberOnAnotherLineIsNotMistakenForASeparateAddress() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            """
            Rua das Flores,
            120
            """.trimIndent(),
        )

        assertEquals(listOf("Rua das Flores"), addresses)
    }

    @Test
    fun priceDistancePhoneTimeAndButtonsDoNotBecomeAdditionalAddresses() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            """
            Rua das Flores
            R$ 35,00
            (11) 99999-8888
            18:30
            12,5 km
            Aceitar por R$ 35
            """.trimIndent(),
        )

        assertEquals(listOf("Rua das Flores"), addresses)
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
    fun inDriveDestinationWithoutNumberIsPreservedWithWrappedLocality() {
        val fields = UniversalScreenAddressParser.parse(
            """
            A Travessa Voa Voa Beija-Flor 37 (Jardim da Conquista)
            B Rua Erundina (Jardim Rodolfo
            Pirani, Sao Paulo - SP)
            """.trimIndent(),
        )

        assertEquals("Travessa Voa Voa Beija-Flor 37 (Jardim da Conquista)", fields.pickup)
        assertEquals("Rua Erundina (Jardim Rodolfo Pirani, Sao Paulo - SP)", fields.destination)
    }

    @Test
    fun realAddressStillWinsAfterNoisyLines() {
        val fields = UniversalScreenAddressParser.parse(
            """
            qua., 15 de jul.
            Documento em PDF
            R$ 42,00
            Rua Doutor Paulo - Centro, Tres Coracoes - MG
            """.trimIndent(),
        )

        assertEquals("Rua Doutor Paulo - Centro, Tres Coracoes - MG", fields.destination)
    }

    @Test
    fun wrappedNeighborhoodAfterDanglingSaoIsJoined() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Av. Mateo Bei, Sao Mateus, Sao
            12 minutos (3.4 km)
            Rua Pedro da Lomba, 188, Sao
            Rafael, Sao Paulo
            """.trimIndent(),
        )

        assertEquals("Av. Mateo Bei, Sao Mateus, Sao", fields.pickup)
        assertEquals("Rua Pedro da Lomba, 188, Sao Rafael, Sao Paulo", fields.destination)
    }


    @Test
    fun realInDrivePickupSplitAfterAvenidaIsRecomposed() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Pedido de viagem
            R$ 2,3/km  ~1,4 km
            R$ 15
            Comercial Esperanca - Sao
            Paulo Sao Mateus (Avenida
            Mateo Bei - Cidade Sao Mateus,
            Sao Paulo - State of Sao Paulo)
            Av. Maria Luiza Americano, 2673
            (Cidade Lider)
            PIX
            Aceitar por R$ 15
            """.trimIndent(),
        )

        assertEquals(
            "Avenida Mateo Bei - Cidade Sao Mateus, Sao Paulo - State of Sao Paulo",
            fields.pickup,
        )
        assertEquals("Av. Maria Luiza Americano, 2673 (Cidade Lider)", fields.destination)
    }

    @Test
    fun isolatedStreetWordWithoutContinuationDoesNotBecomeAddress() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            """
            Catalogo de vias
            Avenida
            Produto em promocao
            Rua
            """.trimIndent(),
        )

        assertTrue(addresses.isEmpty())
    }

}
