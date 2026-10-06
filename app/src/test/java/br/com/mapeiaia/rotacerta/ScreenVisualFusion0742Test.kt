package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenVisualFusion0742Test {
    @Test
    fun mergeKeepsRecoveredVisualLinesAndDeduplicatesAccessibility() {
        val passes = listOf(
            OcrStructuredText0188(
                text = "Contato via WhatsApp\n+55 11 99499-1598",
                blocks = emptyList(),
            ),
            OcrStructuredText0188(
                text = "+55 11 99499-1598\nTexto pequeno recuperado",
                blocks = emptyList(),
            ),
        )

        val merged = ScreenVisualFusion0742.mergeText(
            accessibilityText = "Contato via WhatsApp\nBotão do Instagram",
            passes = passes,
        )

        assertTrue(merged.contains("Contato via WhatsApp"))
        assertTrue(merged.contains("+55 11 99499-1598"))
        assertTrue(merged.contains("Texto pequeno recuperado"))
        assertTrue(merged.contains("Botão do Instagram"))
        assertEquals(1, merged.lines().count { it == "+55 11 99499-1598" })
    }

    @Test
    fun phoneConsensusFindsExactNumberFromVideoAcrossBlocks() {
        val pass = OcrStructuredText0188(
            text = "Contato via WhatsApp\n+55 11\n99499-1598",
            blocks = listOf(
                OcrTextBlock0188("a", "Contato via WhatsApp", 40, 500, 700, 570),
                OcrTextBlock0188("b", "+55 11", 60, 585, 300, 645),
                OcrTextBlock0188("c", "99499-1598", 60, 650, 430, 720),
            ),
        )

        val target = ScreenVisualFusion0742.findPhone("", listOf(pass))

        requireNotNull(target)
        assertEquals("11994991598", target.nationalDigits)
    }

    @Test
    fun recoveryPlanAddsAnchorAndOverlappingBandsForPhone() {
        val specs = ScreenVisualFusion0742.recoveryCrops(
            width = 1080,
            height = 2340,
            blocks = listOf(OcrTextBlock0188("a", "Contato via WhatsApp", 80, 1200, 700, 1280)),
            phoneMode = true,
        )

        assertTrue(specs.any { it.id.startsWith("anchor-") })
        assertTrue(specs.count { it.id.startsWith("band-") } >= 2)
        assertTrue(specs.all { it.left >= 0 && it.top >= 0 && it.right <= 1080 && it.bottom <= 2340 })
    }

    @Test
    fun fullTextPlanIsBounded() {
        val specs = ScreenVisualFusion0742.recoveryCrops(1080, 2340, emptyList(), phoneMode = false)
        assertEquals(3, specs.size)
        assertFalse(specs.any { it.scale > 2.0f })
    }
}
