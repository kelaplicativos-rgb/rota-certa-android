package br.com.mapeiaia.rotacerta

import br.com.mapeiaia.rotacerta.core.CoreCardMatchEngine
import br.com.mapeiaia.rotacerta.core.InDriveCoreModule
import br.com.mapeiaia.rotacerta.core.RideScreenKind
import br.com.mapeiaia.rotacerta.core.RideScreenSnapshot
import br.com.mapeiaia.rotacerta.core.RotaCertaCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InDriveMarkerlessLiveCardTest {
    private val parser = RideTextParser()

    @Test
    fun realOpenedCardWorksWhenABAndMapBadgesAreGraphicsOnly() {
        val learnedText = """
            Offline
            Pedido de viagem
            10 min (4,0 km)
            41 min (11,9 km)
            R$ 1,9/km ~4,0 km
            R$ 30
            A Rua Joaquim Meira de Siqueira, 591 (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)
            B Rua Emilia Marengo, 179 (Vila Regente Feijo, Sao Paulo - SP)
            Aceitar por R$ 30
            Ofereca sua tarifa
        """.trimIndent()
        val liveAccessibilityText = """
            Offline
            Pedido de viagem
            R$ 1,9/km ~4,0 km
            R$ 30
            Rua Joaquim Meira de Siqueira, 591 (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)
            Rua Emilia Marengo, 179 (Vila Regente Feijo, Sao Paulo - SP)
            Aceitar por R$ 30
            Ofereca sua tarifa
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = learnedText,
        )
        val fields = parser.parse(liveAccessibilityText, RideCardTemplateMatcher.INDRIVE_PACKAGE)
        val features = RideCardTemplateMatcher.featuresFor(liveAccessibilityText)

        assertNotNull(fields.destination)
        assertTrue(fields.destination.orEmpty().contains("Emilia", ignoreCase = true))
        assertTrue("card.route.two_addresses" in features)
        assertTrue("card.crop.route_block" in features)
        assertTrue("card.contract.indrive_opened_single" in features)

        val classification = InDriveCoreModule.classify(
            RideScreenSnapshot(
                packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                text = liveAccessibilityText,
                fields = fields,
            ),
        )
        assertEquals(RideScreenKind.OpenRideCard, classification.kind)

        val match = CoreCardMatchEngine.match(
            text = liveAccessibilityText,
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            templates = listOf(template),
        )
        assertTrue(match.accepted)
        assertNotNull(match.match)

        val coreDecision = RotaCertaCore.matchRegisteredOpenCard(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = liveAccessibilityText,
            fields = fields,
            templates = listOf(template),
        )
        assertTrue(coreDecision.canAnalyzeRoute)
    }

    @Test
    fun pluralFeedWithoutTextualMarkersRemainsBlocked() {
        val listText = """
            Pedidos de viagem
            R$ 2,4/km ~1,8 km
            R$ 23
            Avenida Ragueb Chohfi, 1400 (Jardim Tres Marias, Sao Paulo - SP)
            Poupatempo Cidade Tiradentes (Rua Sara Kubitscheck, Sao Paulo - SP)
            R$ 1,4/km ~2,7 km
            R$ 28
            Rua Outra, 16 (Bairro Dois, Sao Paulo - SP)
            Avenida Final, 900 (Sao Paulo - SP)
        """.trimIndent()
        val fields = parser.parse(listText, RideCardTemplateMatcher.INDRIVE_PACKAGE)
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = """
                Pedido de viagem
                R$ 1,7/km ~2,3 km
                R$ 25
                A Rua Modelo Um, 10
                B Rua Modelo Dois, 20
                Aceitar por R$ 25
            """.trimIndent(),
        )

        val classification = InDriveCoreModule.classify(
            RideScreenSnapshot(
                packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
                text = listText,
                fields = fields,
            ),
        )
        assertEquals(RideScreenKind.RideListing, classification.kind)

        val match = CoreCardMatchEngine.match(
            text = listText,
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            templates = listOf(template),
        )
        assertFalse(match.accepted)
        assertTrue(match.isListLike)
    }
}
