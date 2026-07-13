package br.com.mapeiaia.rotacerta

import br.com.mapeiaia.rotacerta.core.InDriveCardContract
import br.com.mapeiaia.rotacerta.core.InDriveCoreModule
import br.com.mapeiaia.rotacerta.core.RideScreenKind
import br.com.mapeiaia.rotacerta.core.RideScreenSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InDriveCardFamilyRecognitionTest {
    private val parser = RideTextParser()

    @Test
    fun offlineStatusDoesNotTurnOpenedCardIntoRideListing() {
        val text = openedCard(
            pickup = "A Rua Modelo Um, 311 (Bairro Azul, Sao Paulo - SP)",
            destination = "B Rua Modelo Dois, 527 (Bairro Verde, Maua - SP)",
        )
        val fields = parser.parse(text, RideCardTemplateMatcher.INDRIVE_PACKAGE)

        val classification = InDriveCoreModule.classify(
            RideScreenSnapshot(
                packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                text = text,
                fields = fields,
            ),
        )

        assertEquals(RideScreenKind.OpenRideCard, classification.kind)
        assertEquals("Rua Modelo Dois, 527 (Bairro Verde, Maua - SP)", fields.destination)
    }

    @Test
    fun joinsDestinationStreetNameSplitAfterConnector() {
        val text = openedCard(
            pickup = "A Rua Modelo Um, 280 (Centro, Sao Paulo - SP)",
            destination = "B Rua Doutor Exemplo do\nNascimento, 638 (Bras, Sao Paulo - SP)",
        )

        val fields = parser.parse(text, RideCardTemplateMatcher.INDRIVE_PACKAGE)

        assertEquals("Rua Doutor Exemplo do Nascimento, 638 (Bras, Sao Paulo - SP)", fields.destination)
    }

    @Test
    fun joinsAddressWhenNumberStartsTheNextOcrLine() {
        val text = openedCard(
            pickup = "A Rua Joaquim Modelo de Siqueira\n591 (Jardim Central, Sao Paulo - SP)",
            destination = "B Rua Emilia Modelo, 179 (Vila Regente, Sao Paulo - SP)",
        )

        val fields = parser.parse(text, RideCardTemplateMatcher.INDRIVE_PACKAGE)

        assertEquals("Rua Joaquim Modelo de Siqueira 591 (Jardim Central, Sao Paulo - SP)", fields.pickup)
        assertEquals("Rua Emilia Modelo, 179 (Vila Regente, Sao Paulo - SP)", fields.destination)
    }

    @Test
    fun acceptsPlaceNameAsFinalDestinationAndStopsBeforePayment() {
        val text = openedCard(
            pickup = "A Avenida Modelo, 464 (Jardim Azul, Sao Paulo - SP)",
            destination = "B Restaurante Modelo (Avenida Central - Cidade Modelo, Sao Paulo - SP)\nPIX",
            paymentLine = "",
        )

        val fields = parser.parse(text, RideCardTemplateMatcher.INDRIVE_PACKAGE)

        assertEquals("Restaurante Modelo (Avenida Central - Cidade Modelo, Sao Paulo - SP)", fields.destination)
    }

    @Test
    fun flexibleContractAcceptsCardWhenOcrMissesTitleButKeepsRouteAndAction() {
        val text = """
            Online
            R$ 2,7/km ~975 m
            R$ 16 Preco justo
            A Avenida Modelo 14446
            B Rua dos Jasmins, 14 (Sao Rafael, Sao Paulo - SP)
            Aceitar por R$ 16
        """.trimIndent()
        val features = RideCardTemplateMatcher.featuresFor(text)

        val result = InDriveCardContract.evaluate(
            text = text,
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            features = features,
        )

        assertTrue(result.accepted)
        assertFalse(result.isListLike)
    }

    @Test
    fun oneLearnedInDriveTemplateRecognizesDynamicCardVariants() {
        val learnedText = openedCard(
            pickup = "A Rua Modelo Um, 56 (Parque Azul)",
            destination = "B Avenida Modelo, 1800 (Jardim Verde, Cidade Modelo - SP)",
            paymentLine = "PIX",
        )
        val liveText = openedCard(
            pickup = "A Rua Modelo Tres, 408 (Lapa, Sao Paulo - SP)",
            destination = "B Rua Modelo Quatro, 18 (Imirim, Sao Paulo - SP)",
            paymentLine = "Maquininha de cartao",
        ).replace("Ofereca sua tarifa", "")
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = learnedText,
        )

        val match = RideCardTemplateMatcher.match(
            text = liveText,
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            templates = listOf(template),
        )

        assertNotNull(match)
    }

    @Test
    fun pluralRideFeedRemainsBlocked() {
        val text = """
            Pedidos de viagem
            Offline
            R$ 2,4/km ~1,8 km
            R$ 23 Preco justo
            Avenida Modelo 1400 (Jardim Azul)
            Poupatempo - Cidade Modelo (Rua Central - Sao Paulo - SP)
            PIX
            R$ 1,4/km ~2,7 km
            R$ 28
            Rua Outra, 16 (Bairro Dois)
            Avenida Final, 900 (Sao Paulo - SP)
        """.trimIndent()
        val fields = parser.parse(text, RideCardTemplateMatcher.INDRIVE_PACKAGE)

        val classification = InDriveCoreModule.classify(
            RideScreenSnapshot(
                packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                text = text,
                fields = fields,
            ),
        )

        assertEquals(RideScreenKind.RideListing, classification.kind)
    }

    private fun openedCard(
        pickup: String,
        destination: String,
        paymentLine: String = "PIX",
    ): String = """
        Offline
        Pedido de viagem
        R$ 1,7/km ~2,3 km
        R$ 25
        $pickup
        $destination
        $paymentLine
        Aceitar por R$ 25
        Ofereca sua tarifa
    """.trimIndent()
}
