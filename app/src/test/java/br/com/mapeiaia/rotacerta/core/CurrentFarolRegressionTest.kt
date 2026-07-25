package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentFarolRegressionTest {
    @Test
    fun accessibilityAndOcrCopiesOfSameCardDoNotBecomeRideFeed() {
        val accessibility = """
            Pedido de viagem
            R$ 10
            2,3 km
            Cidade Sao Mateus
            Rua Sao Miguel do Guama, 43
            Aceitar por R$ 10
            Ofereca sua tarifa
        """.trimIndent()
        val ocr = """
            PEDIDO DE VIAGEM
            R$10
            2,3 KM
            Cidade São Mateus
            Rua São Miguel do Guamá, 43
            Aceitar por R$10
            Ofereça sua tarifa
        """.trimIndent()

        val merged = CoreScreenReadEngine.merge(accessibility, ocr)

        assertFalse(CoreCardMatchEngine.isListLikeRideFeed(merged))
        assertTrue(merged.lines().count { it.contains("Pedido de viagem", ignoreCase = true) } == 1)
        assertTrue(merged.lines().count { it.contains("Aceitar por", ignoreCase = true) } == 1)
    }

    @Test
    fun registeredInDriveCardWorksWhenLiveReadMissesFragileMapMarkers() {
        val learnedScreenshot = """
            Pedido de viagem
            2,3 km
            R$ 10
            4 min (1,6 km)
            A
            Cidade Sao Mateus
            B
            Rua Sao Miguel do Guama, 43
            PIX
            Aceitar por R$ 10
            Ofereca sua tarifa
        """.trimIndent()
        val liveRead = """
            Pedido de viagem
            2,3 km
            R$ 10
            4 min
            1,6 km
            Cidade Sao Mateus
            Rua Sao Miguel do Guama, 43
            PIX
            Aceitar por R$ 10
            Ofereca sua tarifa
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate(
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            text = learnedScreenshot,
        )

        val result = CoreCardMatchEngine.match(
            text = liveRead,
            packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE,
            templates = listOf(template),
        )

        assertTrue(result.reason, result.accepted)
        assertTrue(result.match != null)
        assertTrue(result.contractName == "inDrive")
    }

    @Test
    fun realFeedWithDifferentOffersStillStaysBlocked() {
        val feed = """
            Pedidos de viagem
            Pedido de viagem
            A Rua A, 10
            B Rua B, 20
            3 km
            Aceitar por R$ 20
            Pedido de viagem
            A Rua C, 30
            B Rua D, 40
            8 km
            Aceitar por R$ 45
        """.trimIndent()

        assertTrue(CoreCardMatchEngine.isListLikeRideFeed(feed))
    }
}
