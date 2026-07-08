package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleCardPresenceDetectorTest {
    @Test
    fun sameRegisteredCardRequiresSamePackageHashAndTemplate() {
        val text = """
            Pedido de viagem
            R$ 20
            3,2 km
            Rua A, 10
            Avenida B, 200
            Aceitar
        """.trimIndent()
        val template = RideCardTemplateMatcher.createTemplate("com.regional.driver", text)
        val match = RideCardTemplateMatcher.match(text, "com.regional.driver", listOf(template))!!
        val token = BubbleCardPresenceDetector.createToken(
            packageName = "com.regional.driver",
            snapshotHash = BubbleCardPresenceDetector.stableSnapshotHash(text),
            match = match,
        )!!

        assertTrue(
            BubbleCardPresenceDetector.sameRegisteredCard(
                token = token,
                text = text,
                packageName = "com.regional.driver",
                templates = listOf(template),
            ),
        )
        assertFalse(
            BubbleCardPresenceDetector.sameRegisteredCard(
                token = token,
                text = text.replace("R$ 20", "R$ 31"),
                packageName = "com.regional.driver",
                templates = listOf(template),
            ),
        )
        assertFalse(
            BubbleCardPresenceDetector.sameRegisteredCard(
                token = token,
                text = text,
                packageName = "com.other.driver",
                templates = listOf(template),
            ),
        )
    }
}
