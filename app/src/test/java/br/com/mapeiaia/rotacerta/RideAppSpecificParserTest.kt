package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class RideAppSpecificParserTest {
    @Test
    fun parsesNinetyNineTemplateLikeCardUsingPackage() {
        val text = """
            Dinheiro
            R$12,40
            4,97 409 corridas
            Perfil Essencial
            3min (462m)
            Comercial Esperanca, Avenida
            Sapopemba, 13309 - Jardim Aduto
            5min (1,9km)
            Rua Augustin Luberti, 1053, Fazenda
            da Juta
            Selecionar
        """.trimIndent()

        val result = RideTextParser().parseWithMetadata(text, "com.app99.driver")

        assertEquals("99-card-template", result.parserName)
        assertEquals("Comercial Esperanca, Avenida Sapopemba, 13309 - Jardim Aduto", result.fields.pickup)
        assertEquals("Rua Augustin Luberti, 1053, Fazenda da Juta", result.fields.destination)
        assertEquals("R$12,40", result.fields.fare)
    }

    @Test
    fun parsesUberCardUsingPackage() {
        val text = """
            UberX
            Exclusivo
            R$ 29,65
            4 min (1.3 km)
            Rua Paulo Laurito, Sao Mateus, Sao
            Paulo
            47 minutos (16.9 km)
            rua icem, 57, Tatuape, Sao Paulo
            Viagem longa (mais de 45 min)
            Aceitar
        """.trimIndent()

        val result = RideTextParser().parseWithMetadata(text, "com.ubercab.driver")

        assertEquals("uber-trip-card", result.parserName)
        assertEquals("Rua Paulo Laurito, Sao Mateus, Sao Paulo", result.fields.pickup)
        assertEquals("rua icem, 57, Tatuape, Sao Paulo", result.fields.destination)
        assertEquals("R$ 29,65", result.fields.fare)
    }

    @Test
    fun parsesInDriveOrderCardUsingPackageAndIgnoresFareActions() {
        val text = """
            Pedido de viagem
            R$ 1,6/km ~3,7 km
            R$ 29
            A Rua Baltazar Vidal 95 (Jardim Nossa Sra. do Carmo)
            B Rua Coelho Lisboa, 419 (Cidade Mae do Ceu, Sao Paulo - SP)
            PIX
            Aceitar por R$ 29
            Ofereca sua tarifa
            R$ 32
            R$ 34
            Fechar
        """.trimIndent()

        val result = RideTextParser().parseWithMetadata(text, "sinet.startup.inDriver")

        assertEquals("indrive-order-card", result.parserName)
        assertEquals("Rua Baltazar Vidal 95 (Jardim Nossa Sra. do Carmo)", result.fields.pickup)
        assertEquals("Rua Coelho Lisboa, 419 (Cidade Mae do Ceu, Sao Paulo - SP)", result.fields.destination)
        assertEquals("R$ 29", result.fields.fare)
    }
}
