package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideCardTemplateMatcher
import br.com.mapeiaia.rotacerta.RideTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GiguReferencePipelineRegressionTest {
    private val packageName = RideCardTemplateMatcher.INDRIVE_PACKAGE
    private val parser = RideTextParser()

    @Test
    fun noisyMarkerlessInDriveReadReachesRegisteredCardCore() {
        val learnedCard = """
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
        val noisyLiveRead = """
            4,9 (346)
            Agora mesmo
            R$ 1,9/km ~4,0 km
            R$30
            Rua Joaquim Meira de Siqueira, 591 (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)
            Rua Emilia Marengo, 179 (Vila Regente Feijo, Sao Paulo - SP)
            Aceitar por R$30
            Ofereça sua tarifa
        """.trimIndent()

        val sanitized = CoreRideTextSanitizer.sanitize(noisyLiveRead, packageName)
        val fields = parser.parse(sanitized, packageName)
        val classification = RotaCertaCore.classifyScreen(packageName, sanitized, fields)
        val template = RideCardTemplateMatcher.createTemplate(packageName, learnedCard)
        val match = CoreCardMatchEngine.match(sanitized, packageName, listOf(template))

        assertTrue(sanitized.contains("4,0 km"))
        assertTrue(!sanitized.contains("Agora mesmo", ignoreCase = true))
        assertNotNull(fields.destination)
        assertEquals(RideScreenKind.OpenRideCard, classification.kind)
        assertTrue(classification.reason, classification.canAnalyzeRoute)
        assertTrue(match.reason, match.accepted)
        assertNotNull(match.match)
    }

    @Test
    fun sanitizedFormattingProducesStableHash() {
        val accessibility = """
            Aceitar por R$ 10
            Cidade Sao Mateus
        """.trimIndent()
        val ocr = """
            ACEITAR POR R$10
            Cidade São Mateus
        """.trimIndent()

        val first = CoreRideTextSanitizer.sanitize(
            CoreScreenReadEngine.merge(accessibility, ocr),
            packageName,
        )
        val second = CoreRideTextSanitizer.sanitize(
            "Aceitar por R$10\nCidade São Mateus",
            packageName,
        )

        assertEquals(CoreScreenReadEngine.stableHash(first), CoreScreenReadEngine.stableHash(second))
    }
}
