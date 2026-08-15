package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46VisualEpochNoResultTest {
    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(dir, "app/src/main/java/br/com/mapeiaia/rotacerta").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("project root not found")
    }

    private fun source(name: String): String = File(projectRoot(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    @Test fun stage46_contract_markers_present() {
        val h = source("FarolVisualEpochNoResultStage46.kt")
        listOf(
            "FAROL_VISUAL_SURFACE_EPOCH_STAGE46",
            "OCR_CANNOT_CROSS_VISIBLE_SURFACE_STAGE46",
            "ROUTE_CANNOT_CROSS_VISIBLE_SURFACE_STAGE46",
            "RAW_EVENT_SAME_SURFACE_DOES_NOT_CANCEL_STAGE46",
            "SELF_OVERLAY_DECIMAL_EXCLUDED_BEFORE_CLUSTER_STAGE46",
            "OPEN_ADDRESS_PARENTHESIS_CANNOT_CONSUME_DECIMAL_NOISE_STAGE46",
            "NO_RESULT_RECOVERY_USES_LOCAL_NON_OVERLAPPING_ADDRESS_PAIRS_STAGE46",
            "STAGE21_REVALIDATES_EVERY_RECOVERED_PAIR_STAGE46",
            "NO_POLLING_NO_CONTINUOUS_OCR_STAGE46",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test fun physical_indrive_to_launcher_surface_change_is_stale() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("sinet.startup.indriver", null, 6306)
        assertFalse(FarolVisualEpochNoResultStage46.surfaceFresh(token, "com.sec.android.app.launcher"))
    }

    @Test fun same_surface_raw_recyclerview_churn_remains_fresh() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("sinet.startup.indriver", null, 6277)
        assertTrue(FarolVisualEpochNoResultStage46.surfaceFresh(token, "sinet.startup.indriver"))
    }

    @Test fun missing_current_surface_fails_closed() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("sinet.startup.indriver", null, 6277)
        assertFalse(FarolVisualEpochNoResultStage46.surfaceFresh(token, null))
    }

    @Test fun root_package_has_precedence_over_event_package() {
        val token = FarolVisualEpochNoResultStage46.captureSurface("sinet.startup.indriver", "com.samsung.android.app.smartcapture", 6277)
        assertEquals("sinet.startup.indriver", token.packageName)
    }

    @Test fun physical_top_right_farol_decimal_2_4_is_removed_before_cluster() {
        assertTrue(FarolVisualEpochNoResultStage46.shouldDropSelfOverlayDecimal("2,4", 940, 231, 1027, 289, 1080, 2340))
    }

    @Test fun decimal_elsewhere_is_not_blanket_removed() {
        assertFalse(FarolVisualEpochNoResultStage46.shouldDropSelfOverlayDecimal("2,4", 200, 1400, 287, 1458, 1080, 2340))
    }

    @Test fun physical_mercadocar_decimal_cannot_become_address_locality() {
        val raw = """MercadoCar Aricanduva
(Avenida Aricanduva -
Aricanduva, São Paulo
7,9
R$ 1,5/km ~5,9 km"""
        val sanitized = FarolVisualEpochNoResultStage46.sanitizeForReconstruction(raw)
        assertTrue(sanitized.changed)
        assertEquals(1, sanitized.removedStandaloneDecimals)
        val rebuilt = FarolOcrMultilineAddressStage45.reconstructClusterText(sanitized.text)
        assertFalse(rebuilt.contains("7,9"))
        assertTrue(rebuilt.contains("Avenida Aricanduva"))
    }

    @Test fun valid_closed_parenthesis_does_not_trigger_synthetic_cleanup() {
        val raw = "MercadoCar (Avenida Aricanduva - Aricanduva, São Paulo - SP)\n7,9"
        val sanitized = FarolVisualEpochNoResultStage46.sanitizeForReconstruction(raw)
        assertEquals(0, sanitized.removedStandaloneDecimals)
        assertTrue(sanitized.text.endsWith("7,9"))
    }

    @Test fun odd_address_fragment_count_fails_closed_in_recovery() {
        val f = listOf(
            FarolVisualEpochNoResultStage46.Fragment("a", "Rua A, 10", 100, 500, 900, 560),
            FarolVisualEpochNoResultStage46.Fragment("b", "Rua B, 20", 100, 700, 900, 760),
            FarolVisualEpochNoResultStage46.Fragment("c", "Rua C, 30", 100, 1000, 900, 1060),
        )
        assertTrue(FarolVisualEpochNoResultStage46.buildLocalAddressPairBands(f).isEmpty())
    }

    @Test fun four_addresses_become_two_non_overlapping_local_pairs() {
        val f = listOf(
            FarolVisualEpochNoResultStage46.Fragment("a", "Avenida Afonso de Sampaig1", 180, 500, 900, 560),
            FarolVisualEpochNoResultStage46.Fragment("b", "Rua de Servidão, 239 (Jardim da Conquista, São Paulo - SP)", 180, 700, 900, 790),
            FarolVisualEpochNoResultStage46.Fragment("c", "Rua Leon Burbure, 60 (Fazenda da Juta, São Paulo - SP)", 180, 1100, 900, 1190),
            FarolVisualEpochNoResultStage46.Fragment("d", "Estação Capuava (Avenida Manoel da Nobrega - Capuava, Mauá - SP)", 180, 1350, 900, 1450),
        )
        val bands = FarolVisualEpochNoResultStage46.buildLocalAddressPairBands(f)
        assertEquals(2, bands.size)
        assertTrue(bands[0].text.contains("Afonso"))
        assertTrue(bands[0].text.contains("Servidão"))
        assertFalse(bands[0].text.contains("Leon Burbure"))
        assertTrue(bands[1].text.contains("Leon Burbure"))
        assertTrue(bands[1].text.contains("Estação Capuava"))
    }

    @Test fun recovered_pair_still_must_pass_stage21() {
        val raw = """Rua Leon Burbure, 60 (Fazenda da Juta, São Paulo - SP)
Estação Capuava (Avenida Manoel da Nobrega - Capuava, Mauá - SP)"""
        val e = FarolCausalCorrectionStage21.evaluate(
            listOf(FarolUniversalVisualPipelineStage19.VisualBlock(
                id = "stage46-safe-pair", metadataPackageName = "sinet.startup.indriver", windowId = 6317,
                windowLayer = Int.MAX_VALUE, depth = 1, text = raw,
                source = FarolUniversalVisualPipelineStage19.Source.Ocr,
                left = 180, top = 1100, right = 900, bottom = 1450,
            )),
        )
        assertNotNull(e)
        assertTrue(FarolCausalCorrectionStage21.validateEvaluation(e!!).accepted)
    }

    @Test fun distant_address_fragments_do_not_form_recovery_pair() {
        val f = listOf(
            FarolVisualEpochNoResultStage46.Fragment("a", "Rua A, 10 (Centro, São Paulo - SP)", 100, 200, 900, 260),
            FarolVisualEpochNoResultStage46.Fragment("b", "Rua B, 20 (Centro, São Paulo - SP)", 100, 1900, 900, 1960),
        )
        assertTrue(FarolVisualEpochNoResultStage46.buildLocalAddressPairBands(f).isEmpty())
    }

    @Test fun service_ocr_freshness_uses_physical_surface_at_all_stage36_checkpoints() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("val surfaceTokenStage46 = FarolVisualEpochNoResultStage46.captureSurface"))
        assertTrue(s.contains("private fun isStage46OcrWorkFresh("))
        assertFalse(s.contains("if (!isStage36WorkFresh(workTokenStage36))"))
        assertTrue(s.contains("S46_STALE_OCR_SURFACE_DROPPED"))
    }

    @Test fun route_and_final_paint_binding_require_same_physical_surface() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("stage46BindingSurfaceToken"))
        assertTrue(s.contains("S46_STALE_ROUTE_SURFACE_DROPPED"))
        assertTrue(s.contains("FarolVisualEpochNoResultStage46.surfaceFresh(surfaceStage46, currentRootPackageName())"))
    }

    @Test fun no_result_recovery_runs_only_after_normal_evaluation_is_null() {
        val s = source("LiveRideAccessibilityService.kt")
        val normal = s.indexOf("FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)")
        val recovery = s.indexOf("S46_NO_RESULT_RECOVERY_ATTEMPT", normal)
        val processing = s.indexOf("if (evaluationStage19 != null)", recovery)
        assertTrue(normal >= 0 && recovery > normal && processing > recovery)
        assertTrue(s.contains("FarolCausalCorrectionStage21.evaluate(listOf(blockStage46))"))
        assertTrue(s.contains("FarolCausalCorrectionStage21::validateEvaluation"))
    }

    @Test fun stage45_keyle_regression_remains_intact() {
        val raw = "Rua Keyle Emilia Lemos\nSantos 292 (Parque\nContinental l)"
        assertEquals("Rua Keyle Emilia Lemos Santos, 292 (Parque Continental l)", FarolOcrMultilineAddressStage45.reconstructClusterText(raw))
    }

    @Test fun stage44_and_stage43_contracts_remain_materialized() {
        assertTrue(source("FarolSemanticFinalLeaseStage44.kt").contains("FAROL_SEMANTIC_FINAL_LEASE_STAGE44"))
        assertTrue(source("FarolManualOffVisualCommitStage43.kt").contains("MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43"))
    }

    @Test fun stage41_same_frame_freshness_remains_materialized() {
        assertTrue(source("FarolFinalPaintFreshnessStage41.kt").contains("FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41"))
    }

    @Test fun no_polling_or_continuous_ocr_added() {
        val h = source("FarolVisualEpochNoResultStage46.kt")
        assertFalse(h.contains("Thread.sleep"))
        assertFalse(h.contains("delay("))
        assertFalse(h.contains("Timer("))
        assertFalse(h.contains("scheduleAtFixedRate"))
    }

    @Test fun version_is_stage46_0_1_219_5503() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5503"))
        assertTrue(b.contains("versionName = \"0.1.219\""))
    }
}
