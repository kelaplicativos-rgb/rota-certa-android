package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleCardPresenceDetectorTest {
    @Test
    fun sameRegisteredCardRequiresSamePackageHashAndTemplate() {
        val text = """
            9min (1,3km)
            Yogui Stilo e Sports, Avenida Mateo Bei, 2651 - Cidade Sao Mateus
            9min (2,9km)
            Condominio Parque Residencial Santa Barbara, Cidade Satelite
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
                text = text.replace("9min (2,9km)", "12min (5,8km)"),
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
