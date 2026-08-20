package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolUnifiedVisualCriticalPath0168Test {
    private val service = listOf(
        File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
        File("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"),
    ).firstOrNull(File::exists)?.readText()
        ?: error("LiveRideAccessibilityService.kt não encontrado")

    @Test
    fun visualNormalizationIsInTheLiveProcessingPath() {
        assertTrue(service.contains("FarolUnifiedVisual0168.normalizeForAnalysis"))
        assertTrue(service.contains("FarolUnifiedVisual0168.semanticHash"))
    }

    @Test
    fun firstOcrFrameIsNotArtificiallyDelayed() {
        val marker = service.indexOf("OCR_FALLBACK_SCHEDULED")
        assertTrue(marker >= 0)
        val window = service.substring(marker, (marker + 5_000).coerceAtMost(service.length))
        assertFalse(window.contains("postDelayed("))
        assertFalse(window.contains("delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS)"))
    }
}
