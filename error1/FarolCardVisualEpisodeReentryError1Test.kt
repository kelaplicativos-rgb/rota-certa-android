package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolCardVisualEpisodeReentryError1Test {
    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(dir, "app/src/main/java/br/com/mapeiaia/rotacerta").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("project root not found")
    }

    private fun source(name: String): String =
        File(projectRoot(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    private fun signal(
        structure: String = "com.app99.driver:2048:0:0:22:48:card-surface",
        address: String = "",
        bootstrap: String = "",
    ) = FarolReadingActivationStage26.CheapVisualSignal(
        ownOverlay = false,
        windowSignature = "15701:com.app99.driver",
        sourceText = address,
        sourceSlot = "15701::0:0:1080:2340",
        contentChangeTypes = 1,
        eventType = 2048,
        structuralSignature = structure,
        bootstrapText = bootstrap,
        bootstrapEligible = true,
    )

    @Test fun scenario_a_same_episode_keeps_heavy_work_coalesced() {
        val gate = FarolReadingActivationStage26.PreCollectGate()
        val first = gate.admit(true, signal())
        val duplicate = gate.admit(true, signal())
        assertTrue(first.heavyCollect)
        assertEquals("stage40_structural_bootstrap", first.reason)
        assertFalse(duplicate.heavyCollect)
        assertEquals("stage40_bootstrap_duplicate_coalesced", duplicate.reason)
    }

    @Test fun scenario_b_identical_card_after_proven_exit_is_read_again() {
        val gate = FarolReadingActivationStage26.PreCollectGate()
        val first = gate.admit(true, signal())
        assertTrue(first.heavyCollect)
        gate.endVisualEpisode()
        val reentry = gate.admit(true, signal())
        assertTrue(reentry.heavyCollect)
        assertEquals("stage40_structural_bootstrap", reentry.reason)
        assertTrue(reentry.visualGeneration > first.visualGeneration)
    }

    @Test fun scenario_c_card_b_after_exit_is_read() {
        val gate = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(gate.admit(true, signal(structure = "surface:A", bootstrap = "card A")).heavyCollect)
        gate.endVisualEpisode()
        val b = gate.admit(true, signal(structure = "surface:B", bootstrap = "card B"))
        assertTrue(b.heavyCollect)
        assertEquals("stage40_structural_bootstrap", b.reason)
    }

    @Test fun scenario_d_old_ocr_surface_cannot_block_reentry_after_exit() {
        val gate = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(gate.admit(true, signal()).heavyCollect)
        val oldOcrSurface = FarolVisualEpochNoResultStage46.SurfaceToken("com.app99.driver", 15701, 6L)
        gate.endVisualEpisode()
        val reentry = gate.admit(true, signal())
        assertTrue(reentry.heavyCollect)
        assertFalse(FarolTargetSurfaceStage46R2.surfaceFresh(oldOcrSurface, "com.app99.driver", 15701, 7L))
    }

    @Test fun scenario_e_old_result_after_reentry_stays_stale_and_cannot_paint() {
        val oldRouteOrPaint = FarolVisualEpochNoResultStage46.SurfaceToken("com.app99.driver", 15701, 6L)
        assertFalse(FarolTargetSurfaceStage46R2.surfaceFresh(oldRouteOrPaint, "com.app99.driver", 15701, 7L))
        val service = source("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("stage46BindingSurfaceToken.clear()"))
        assertTrue(service.contains("stage19OcrSerial += 1L"))
        assertTrue(service.contains("S46_STALE_ROUTE_SURFACE_DROPPED"))
    }

    @Test fun scenario_f_same_address_text_and_hash_are_not_history_after_exit() {
        val gate = FarolReadingActivationStage26.PreCollectGate()
        val address = "Hospital Santa Marcelina, Rua Santa Marcelina, 177 - Vila Carmosina"
        val first = gate.admit(true, signal(address = address, structure = ""))
        val duplicate = gate.admit(true, signal(address = address, structure = ""))
        assertTrue(first.heavyCollect)
        assertFalse(duplicate.heavyCollect)
        assertEquals("stage40_same_address_evidence", duplicate.reason)
        gate.endVisualEpisode()
        val reentry = gate.admit(true, signal(address = address, structure = ""))
        assertTrue(reentry.heavyCollect)
        assertEquals("stage40_first_address_evidence", reentry.reason)
    }

    @Test fun scenario_g_duplicate_events_without_exit_still_coalesce() {
        val gate = FarolReadingActivationStage26.PreCollectGate()
        assertTrue(gate.admit(true, signal(structure = "surface:stable", bootstrap = "same card")).heavyCollect)
        repeat(4) {
            val duplicate = gate.admit(true, signal(structure = "surface:stable", bootstrap = "same card"))
            assertFalse(duplicate.heavyCollect)
            assertEquals("stage40_bootstrap_duplicate_coalesced", duplicate.reason)
        }
    }

    @Test fun proven_target_empty_is_the_only_new_runtime_reset_site() {
        val service = source("LiveRideAccessibilityService.kt")
        assertEquals(1, Regex("stage26PreCollectGate\\.endVisualEpisode\\(\\)").findAll(service).count())
        val targetEmpty = service.indexOf("private fun revokeEmptyTargetStage46(")
        val reset = service.indexOf("stage26PreCollectGate.endVisualEpisode()", targetEmpty)
        val marker = service.indexOf("S46_VISUAL_EPISODE_PRECOLLECT_RESET", reset)
        assertTrue(targetEmpty >= 0 && reset > targetEmpty && marker > reset)
    }

    @Test fun target_empty_resets_episode_after_stale_authorities_are_revoked() {
        val service = source("LiveRideAccessibilityService.kt")
        val start = service.indexOf("private fun revokeEmptyTargetStage46(")
        val end = service.indexOf("private fun advanceHardVisualEpochStage46(", start).takeIf { it > start } ?: service.length
        val block = service.substring(start, end)
        val epoch = block.indexOf("stage46VisualEpoch += 1L")
        val lease = block.indexOf("clearVisualLease(\"stage46_r2_target_empty\")")
        val route = block.indexOf("universalRouteJob?.cancel()")
        val tokens = block.indexOf("stage46BindingSurfaceToken.clear()")
        val reset = block.indexOf("stage26PreCollectGate.endVisualEpisode()")
        assertTrue(epoch >= 0 && lease > epoch && route > lease && tokens > route && reset > tokens)
    }

    @Test fun reset_is_memory_only_and_does_not_weaken_generation_or_freshness() {
        val activation = source("FarolReadingActivationStage26.kt")
        val a = activation.indexOf("fun endVisualEpisode()")
        val b = activation.indexOf("fun invalidate()", a)
        val resetBlock = activation.substring(a, b)
        assertTrue(resetBlock.contains("bootstrapValueByStructure.clear()"))
        assertTrue(resetBlock.contains("lastRelevantValue = null"))
        assertFalse(resetBlock.contains("generation +="))

        val service = source("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("stage46VisualEpoch += 1L"))
        assertTrue(service.contains("isStage46OcrWorkFresh"))
        assertTrue(service.contains("S46_STALE_OCR_SURFACE_DROPPED"))
    }

    @Test fun correction_adds_no_polling_debounce_cooldown_or_delay() {
        val activation = source("FarolReadingActivationStage26.kt")
        val a = activation.indexOf("fun endVisualEpisode()")
        val b = activation.indexOf("fun invalidate()", a)
        val resetBlock = activation.substring(a, b)
        listOf("Thread.sleep", "delay(", "Timer(", "scheduleAtFixedRate", "cooldown", "debounce").forEach {
            assertFalse(it, resetBlock.contains(it, ignoreCase = true))
        }
    }
}
