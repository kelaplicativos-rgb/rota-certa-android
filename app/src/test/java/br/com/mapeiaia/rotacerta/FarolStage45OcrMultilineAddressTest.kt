package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage45OcrMultilineAddressTest {
    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(dir, "app/src/main/java/br/com/mapeiaia/rotacerta").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("project root not found from ${System.getProperty("user.dir")}")
    }

    private fun source(name: String): String = File(
        projectRoot(),
        "app/src/main/java/br/com/mapeiaia/rotacerta/$name",
    ).readText()

    @Test
    fun stage45_contract_markers_are_present() {
        val h = source("FarolOcrMultilineAddressStage45.kt")
        listOf(
            "FAROL_OCR_MULTILINE_ADDRESS_STAGE45",
            "OCR_RECONSTRUCTION_SAME_SPATIAL_CLUSTER_ONLY_STAGE45",
            "OCR_SAFE_DIRECT_HOUSE_NUMBER_NORMALIZATION_STAGE45",
            "OCR_PARENTHESES_CONTINUATION_RECONSTRUCTION_STAGE45",
            "STAGE21_SEMANTIC_GATE_REMAINS_AUTHORITY_STAGE45",
            "NO_POLLING_NO_CONTINUOUS_OCR_STAGE45",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test
    fun exact_physical_keyle_pickup_is_reconstructed_with_safe_number_context() {
        val raw = """Rua Keyle Emilia Lemos
Santos 292 (Parque
Continental l)"""
        val r = FarolOcrMultilineAddressStage45.reconstruct(raw)
        assertTrue(r.changed)
        assertEquals("Rua Keyle Emilia Lemos Santos, 292 (Parque Continental l)", r.text)
        assertTrue(r.mergedStreetLines >= 2)
        assertEquals(1, r.normalizedDirectNumbers)
    }

    @Test
    fun direct_house_number_without_comma_is_normalized_only_when_safe() {
        val r = FarolOcrMultilineAddressStage45.reconstruct("Rua Gomes de Carvalho 1005")
        assertEquals("Rua Gomes de Carvalho, 1005", r.text)
        assertEquals(1, r.normalizedDirectNumbers)
    }

    @Test
    fun already_explicit_house_number_is_not_rewritten() {
        val r = FarolOcrMultilineAddressStage45.reconstruct("Rua Gomes de Carvalho, 1005")
        assertEquals("Rua Gomes de Carvalho, 1005", r.text)
        assertEquals(0, r.normalizedDirectNumbers)
    }

    @Test
    fun money_line_is_never_borrowed_as_address_continuation() {
        val raw = "Rua Keyle Emilia Lemos\nR$ 14"
        val r = FarolOcrMultilineAddressStage45.reconstruct(raw)
        assertEquals("Rua Keyle Emilia Lemos\nR$ 14", r.text)
        assertEquals(0, r.mergedStreetLines)
    }

    @Test
    fun time_and_measurement_lines_are_never_borrowed() {
        val raw = "Rua Keyle Emilia Lemos\n5 min.\n21,6 km"
        val r = FarolOcrMultilineAddressStage45.reconstruct(raw)
        assertEquals("Rua Keyle Emilia Lemos\n5 min.\n21,6 km", r.text)
        assertEquals(0, r.mergedStreetLines)
    }

    @Test
    fun a_second_independent_street_is_not_joined_to_the_first() {
        val raw = "Rua Alpha\nRua Beta, 22"
        val r = FarolOcrMultilineAddressStage45.reconstruct(raw)
        assertEquals("Rua Alpha\nRua Beta, 22", r.text)
        assertEquals(0, r.mergedStreetLines)
    }

    @Test
    fun physical_parenthesized_destination_is_reconstructed_until_closing_parenthesis() {
        val raw = """Jorginho Mota (Rua Maria
de Castro Mesquita -
Jardim São Paulo,
Guarulhos - SP)"""
        val r = FarolOcrMultilineAddressStage45.reconstruct(raw)
        assertTrue(r.changed)
        assertEquals(
            "Jorginho Mota (Rua Maria de Castro Mesquita - Jardim São Paulo, Guarulhos - SP)",
            r.text,
        )
        assertTrue(r.mergedParenthesisLines >= 3)
    }

    @Test
    fun exact_physical_combined_ocr_group_now_reaches_stage21_as_complete_pair() {
        val raw = """R$ 14
Rua Keyle Emilia Lemos
Santos 292 (Parque
Continental l)
E Espaço Azul - Vereador
Jorginho Mota (Rua Maria
de Castro Mesquita -
Jardim São Paulo,
Guarulhos - SP)
Aceitar por R$ 14"""
        val rebuilt = FarolOcrMultilineAddressStage45.reconstructClusterText(raw)
        val evaluation = FarolCausalCorrectionStage21.evaluate(
            listOf(
                FarolUniversalVisualPipelineStage19.VisualBlock(
                    id = "stage45-physical-keyle",
                    metadataPackageName = "sinet.startup.indriver",
                    windowId = 4669,
                    windowLayer = Int.MAX_VALUE,
                    depth = 1,
                    text = rebuilt,
                    source = FarolUniversalVisualPipelineStage19.Source.Ocr,
                    left = 180,
                    top = 1000,
                    right = 960,
                    bottom = 1700,
                ),
            ),
        )
        assertNotNull(rebuilt, evaluation)
        evaluation!!
        assertTrue(evaluation.pickup.contains("292"))
        assertTrue(evaluation.pickup.contains("Parque Continental", ignoreCase = true))
        assertTrue(evaluation.destination.contains("Guarulhos", ignoreCase = true))
        assertTrue(FarolCausalCorrectionStage21.validateEvaluation(evaluation).accepted)
    }

    @Test
    fun classic_wrapped_number_case_remains_supported() {
        val raw = "Rua das Flores\n120 - Centro"
        val rebuilt = FarolOcrMultilineAddressStage45.reconstructClusterText(raw)
        assertEquals("Rua das Flores, 120 - Centro", rebuilt)
    }

    @Test
    fun single_line_complete_address_is_stable() {
        val raw = "Avenida Miguel Ignácio Curi, 777 - Artur Alvim, São Paulo - SP"
        assertEquals(raw, FarolOcrMultilineAddressStage45.reconstructClusterText(raw))
    }

    @Test
    fun integration_runs_after_spatial_cluster_and_before_visual_block_evaluation() {
        val s = source("LiveRideAccessibilityService.kt")
        val cluster = s.indexOf("FarolVisualPriority0189.cluster(\"stage19-ocr:\$serialStage19\", fragmentsStage19)")
        val sanitize = s.indexOf("FarolVisualEpochNoResultStage46.sanitizeForReconstruction(groupStage19.text)", cluster)
        val reconstruct = s.indexOf("FarolOcrMultilineAddressStage45.reconstruct(sanitizedStage46.text)", sanitize)
        val visualBlock = s.indexOf("FarolUniversalVisualPipelineStage19.VisualBlock(", reconstruct)
        val evaluate = s.indexOf("FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)", visualBlock)
        assertTrue(cluster >= 0 && sanitize > cluster && reconstruct > sanitize && visualBlock > reconstruct && evaluate > visualBlock)
        assertTrue(s.substring(reconstruct, visualBlock).contains("S45_OCR_MULTILINE_RECONSTRUCTED"))
    }

    @Test
    fun stage45_does_not_relax_or_replace_stage21_semantic_gate() {
        val s = source("LiveRideAccessibilityService.kt")
        val h = source("FarolOcrMultilineAddressStage45.kt")
        assertTrue(h.contains("STAGE21_SEMANTIC_GATE_REMAINS_AUTHORITY_STAGE45"))
        assertTrue(s.contains("FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)"))
        assertFalse(h.contains("validateEvaluation ="))
    }

    @Test
    fun stage44_final_lease_contract_remains_materialized() {
        val h = source("FarolSemanticFinalLeaseStage44.kt")
        assertTrue(h.contains("FAROL_SEMANTIC_FINAL_LEASE_STAGE44"))
        assertTrue(source("LiveRideAccessibilityService.kt").contains("S44_SEMANTIC_SAME_CARD_FINAL_PRESERVED"))
    }

    @Test
    fun stage43_physical_off_commit_remains_materialized() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("S43_MANUAL_OFF_RENDER_COMMIT"))
        assertTrue(source("FarolManualOffVisualCommitStage43.kt").contains("MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43"))
    }

    @Test
    fun freshness_and_no_polling_contracts_remain_intact() {
        val h = source("FarolOcrMultilineAddressStage45.kt")
        assertFalse(h.contains("Thread.sleep"))
        assertFalse(h.contains("delay("))
        assertFalse(h.contains("Timer("))
        assertTrue(source("FarolFinalPaintFreshnessStage41.kt").contains("FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41"))
    }
}
