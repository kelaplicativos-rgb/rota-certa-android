package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage41SubsecondFinalPaintTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Stage41 source not found: $name; cwd=${cwd.absolutePath}")
    }

    private val physicalSignature = "visual|rua vitorio ramalho 80 tatuape sao paulo sp"

    @Test fun contractMarkerIsStage41() {
        assertEquals("FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41", FarolFinalPaintFreshnessStage41.CONTRACT_MARKER)
    }

    @Test fun hardEndToEndBudgetIsOneSecond() {
        assertEquals(1_000_000_000L, FarolFinalPaintFreshnessStage41.HARD_END_TO_END_BUDGET_NS)
    }

    @Test fun physicalFalseStaleFixtureNowPaints() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(86, 89, 1279743022, physicalSignature)
        assertTrue(
            FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(
                binding, 88, 91, 1279743022, physicalSignature, true,
            ),
        )
    }

    @Test fun pendingVerificationChangedFrameStillFailsClosed() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(86, 89, 1279743022, physicalSignature)
        assertFalse(
            FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(
                binding, 88, 91, 1279743023, physicalSignature, true,
            ),
        )
    }

    @Test fun pendingVerificationUnknownFrameFailsClosed() {
        assertFalse(
            FarolFinalPaintFreshnessStage41.bindingMayPaint(
                null, physicalSignature, null, physicalSignature, true,
            ),
        )
    }

    @Test fun differentDestinationAlwaysFailsClosed() {
        assertFalse(
            FarolFinalPaintFreshnessStage41.bindingMayPaint(
                10, "visual|rua a 10", 10, "visual|rua b 20", false,
            ),
        )
        assertFalse(
            FarolFinalPaintFreshnessStage41.bindingMayPaint(
                10, "visual|rua a 10", 10, "visual|rua b 20", true,
            ),
        )
    }

    @Test fun verifiedSameDestinationPreservesStage34AuthorityAcrossFrameChurn() {
        assertTrue(
            FarolFinalPaintFreshnessStage41.bindingMayPaint(
                10, physicalSignature, 99, physicalSignature, false,
            ),
        )
    }

    @Test fun generationAndWindowChurnDoNotBlockSamePendingFrame() {
        val binding = FarolUniversalVisualPipelineStage19.Binding(1, 2, 77, physicalSignature)
        assertTrue(
            FarolUniversalVisualPipelineStage19.bindingMatchesCurrent(
                binding, 900, 901, 77, physicalSignature, true,
            ),
        )
    }

    @Test fun pipelineDelegatesFreshnessToStage41() {
        val s = source("FarolUniversalVisualPipelineStage19.kt")
        assertTrue(s.contains("FarolFinalPaintFreshnessStage41.bindingMayPaint("))
        assertFalse(s.contains("return !visualVerificationPending && binding.addressSignature == currentAddressSignature"))
    }

    @Test fun stage41AddsNoPollingSleepOrDelay() {
        val s = source("FarolFinalPaintFreshnessStage41.kt")
        listOf("Thread.sleep(", "SystemClock.sleep(", "Timer(", "scheduleAtFixedRate(", "fixedRateTimer(", "delay(").forEach {
            assertFalse("forbidden timing primitive: $it", s.contains(it))
        }
    }
}
