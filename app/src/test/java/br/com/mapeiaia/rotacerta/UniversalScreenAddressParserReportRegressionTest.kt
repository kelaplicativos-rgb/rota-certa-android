package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UniversalScreenAddressParserReportRegressionTest {
    @Test
    fun multipleCardsOnOneAccessibilityLineDoNotMergeAddresses() {
        val text = "Rua Coronel Benedito Ferreira de Souza, 33 (Jardim Cinco de Julho, São Paulo - SP) " +
            "Rua Capitão José Leite, 361 (Vila Matilde, São Paulo - SP) " +
            "Avenida Olga Fadel Abarca, 211 (Jardim Santa Teresinha, São Paulo - SP) " +
            "EMEI Ministro Pedro Chaves (Rua Figueira da Barbária - Jardim Brasilia (Zona Leste), São Paulo - SP)"

        val addresses = UniversalScreenAddressParser.findAddresses(text)

        assertEquals(3, addresses.size)
        assertEquals(
            "Avenida Olga Fadel Abarca, 211 (Jardim Santa Teresinha, São Paulo - SP)",
            addresses.last(),
        )
        assertFalse(addresses.last().contains("EMEI Ministro Pedro Chaves"))
        assertFalse(addresses.last().contains("Rua Figueira da Barbária"))
    }

    @Test
    fun lastNumberedAddressOnFlattenedScreenRemainsTheDestination() {
        val fields = UniversalScreenAddressParser.parse(
            "Rua Ministro Apolônio Salles 598 (Jardim Tiete) " +
                "Rua Almeida Falcão, 149 (Jardim Helena, São Paulo - SP) " +
                "Hospital Estadual de Sapopemba (Rua Manuel França dos Santos - Vila Sapopemba, São Paulo - SP) " +
                "Rua Luís Vaz de Toledo Piza, 33 (Jardim Nice, São Paulo - SP)",
        )

        assertEquals("Rua Luís Vaz de Toledo Piza, 33 (Jardim Nice, São Paulo - SP)", fields.destination)
    }
}
