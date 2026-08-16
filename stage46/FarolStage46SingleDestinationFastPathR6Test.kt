package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46SingleDestinationFastPathR6Test {
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

    private fun block(
        text: String,
        id: String = "w/card",
        parentId: String? = "w",
        pkg: String? = "visual.unknown",
        windowId: Int = 7,
        layer: Int = 10,
        depth: Int = 3,
        left: Int = 20,
        top: Int = 500,
        right: Int = 1000,
        bottom: Int = 650,
        source: FarolUniversalVisualPipelineStage19.Source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
    ) = FarolUniversalVisualPipelineStage19.VisualBlock(
        id = id,
        parentId = parentId,
        metadataPackageName = pkg,
        windowId = windowId,
        windowLayer = layer,
        depth = depth,
        text = text,
        source = source,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )

    private val destination = "Rua Benjamin Carr, 596, Sapopemba, São Paulo"
    private val pickup = "Rua Origem, 10, Centro, São Paulo"

    @Test fun r6_contract_markers_are_present() {
        val h = source("FarolSingleDestinationFastPathStage46R6.kt")
        listOf(
            "FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6",
            "TWO_ADDRESSES_NOT_MANDATORY_WHEN_SINGLE_DESTINATION_HIGH_CONFIDENCE_STAGE46_R6",
            "LAST_GEOMETRIC_VISIBLE_ADDRESS_IS_DESTINATION_STAGE46_R6",
            "SPLIT_ADDRESS_BLOCKS_CAN_FORM_CURRENT_VISUAL_DESTINATION_STAGE46_R6",
            "SINGLE_DESTINATION_STILL_USES_STAGE21_ADDRESS_VALIDATION_STAGE46_R6",
            "SINGLE_PICKUP_OR_ORIGIN_CUE_CANNOT_AUTHORIZE_ROUTE_STAGE46_R6",
            "AMBIGUOUS_SINGLE_ADDRESS_FALLS_BACK_TO_LEGACY_ACQUISITION_STAGE46_R6",
            "PACKAGE_IDENTITY_NEVER_AUTHORIZES_SINGLE_DESTINATION_STAGE46_R6",
            "EVENT_DRIVEN_SINGLE_DESTINATION_NO_POLLING_STAGE46_R6",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test fun explicit_destination_cue_authorizes_single_complete_address() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block("Destino", id = "w/label", top = 440, bottom = 490),
                block(destination, id = "w/address", top = 500, bottom = 620),
            ),
        )
        assertNotNull(result)
        assertEquals(destination, result!!.destination)
        assertEquals(1, result.addresses.size)
        assertTrue(FarolSingleDestinationFastPathStage46R6.isSingleFastPathEvaluation(result))
    }

    @Test fun pickup_cue_alone_rejects_single_complete_address() {
        val decision = FarolSingleDestinationFastPathStage46R6.decide(
            listOf(
                block("Embarque", id = "w/label", top = 440, bottom = 490),
                block(destination, id = "w/address", top = 500, bottom = 620),
            ),
        )
        assertNull(decision.evaluation)
        assertEquals("single_pickup_or_origin_cue", decision.reason)
    }

    @Test fun buscar_cue_alone_rejects_single_complete_address() {
        val decision = FarolSingleDestinationFastPathStage46R6.decide(
            listOf(
                block("Buscar passageiro", id = "w/label", top = 440, bottom = 490),
                block(destination, id = "w/address", top = 500, bottom = 620),
            ),
        )
        assertNull(decision.evaluation)
        assertEquals("single_pickup_or_origin_cue", decision.reason)
    }

    @Test fun conflicting_origin_and_destination_cues_fail_closed() {
        val decision = FarolSingleDestinationFastPathStage46R6.decide(
            listOf(
                block("Origem", id = "w/origin-label", top = 410, bottom = 450),
                block("Destino", id = "w/destination-label", top = 455, bottom = 495),
                block(destination, id = "w/address", top = 500, bottom = 620),
            ),
        )
        assertNull(decision.evaluation)
        assertEquals("single_conflicting_role_cues", decision.reason)
    }

    @Test fun truncated_single_address_never_authorizes_fast_path() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block("Destino", id = "w/label", top = 440, bottom = 490),
                block("Avenida Mendonça e", id = "w/address", top = 500, bottom = 620),
            ),
        )
        assertNull(result)
    }

    @Test fun structurally_weak_single_address_without_context_is_rejected() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block("Destino", id = "w/label", top = 440, bottom = 490),
                block("Avenida Mendonça", id = "w/address", top = 500, bottom = 620),
            ),
        )
        assertNull(result)
    }

    @Test fun single_without_concrete_geometry_is_rejected_even_with_destination_cue() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block("Destino", id = "w/label", top = 440, bottom = 490),
                block(destination, id = "w/address", top = Int.MAX_VALUE, bottom = Int.MAX_VALUE, right = 0),
            ),
        )
        assertNull(result)
    }

    @Test fun strong_bottommost_complete_single_can_take_fast_path_without_label() {
        val decision = FarolSingleDestinationFastPathStage46R6.decide(
            listOf(
                block("R$ 31,50\n7 min", id = "w/header", top = 100, bottom = 260),
                block("Corrida disponível", id = "w/body", top = 300, bottom = 470),
                block(destination, id = "w/address", top = 720, bottom = 850),
            ),
        )
        assertNotNull(decision.evaluation)
        assertTrue(decision.singleFastPath)
        assertEquals("single_strong_bottom_final", decision.reason)
        assertEquals(destination, decision.evaluation!!.destination)
    }

    @Test fun upper_screen_single_without_destination_cue_is_not_assumed_final() {
        val decision = FarolSingleDestinationFastPathStage46R6.decide(
            listOf(
                block(destination, id = "w/address", top = 100, bottom = 220),
                block("R$ 31,50\n7 min", id = "w/body", top = 500, bottom = 760),
            ),
        )
        assertNull(decision.evaluation)
        assertEquals("single_not_proven_final", decision.reason)
    }

    @Test fun split_blocks_use_physical_vertical_order_not_input_order() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block(destination, id = "w/destination", top = 700, bottom = 820),
                block(pickup, id = "w/pickup", top = 180, bottom = 300),
            ),
        )
        assertNotNull(result)
        assertEquals(pickup, result!!.pickup)
        assertEquals(destination, result.destination)
        assertEquals(2, result.addresses.size)
        assertTrue(result.blockId.startsWith(FarolSingleDestinationFastPathStage46R6.AGGREGATE_BLOCK_PREFIX))
    }

    @Test fun split_blocks_reverse_input_still_same_destination_signature() {
        val a = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block(pickup, id = "w/pickup", top = 180, bottom = 300),
                block(destination, id = "w/destination", top = 700, bottom = 820),
            ),
        )!!
        val b = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block(destination, id = "w/destination", top = 700, bottom = 820),
                block(pickup, id = "w/pickup", top = 180, bottom = 300),
            ),
        )!!
        assertEquals(a.destination, b.destination)
        assertEquals(a.addressSignature, b.addressSignature)
    }

    @Test fun two_addresses_inside_same_visual_block_use_parser_order_for_last_destination() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(block("$pickup\n$destination", id = "w/card", top = 180, bottom = 820)),
        )
        assertNotNull(result)
        assertEquals(destination, result!!.destination)
    }

    @Test fun multiple_split_addresses_without_geometry_fail_closed() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block(pickup, id = "w/pickup", top = Int.MAX_VALUE, bottom = Int.MAX_VALUE, right = 0),
                block(destination, id = "w/destination", top = Int.MAX_VALUE, bottom = Int.MAX_VALUE, right = 0),
            ),
        )
        assertNull(result)
    }

    @Test fun equal_top_layer_address_windows_are_ambiguous_and_not_mixed() {
        val decision = FarolSingleDestinationFastPathStage46R6.decide(
            listOf(
                block(pickup, id = "w1/address", windowId = 7, layer = 10, top = 180, bottom = 300),
                block(destination, id = "w2/address", windowId = 8, layer = 10, top = 700, bottom = 820),
            ),
        )
        assertNull(decision.evaluation)
        assertEquals("ambiguous_top_layer_windows", decision.reason)
    }

    @Test fun highest_layer_with_address_evidence_is_visual_authority() {
        val highDestination = "Rua Benjamin Carr, 700, Sapopemba, São Paulo"
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block(pickup, id = "low/pickup", windowId = 1, layer = 2, top = 180, bottom = 300),
                block(destination, id = "low/dest", windowId = 1, layer = 2, top = 700, bottom = 820),
                block("Destino", id = "high/label", windowId = 2, layer = 9, top = 440, bottom = 490),
                block(highDestination, id = "high/address", windowId = 2, layer = 9, top = 500, bottom = 620),
            ),
        )
        assertNotNull(result)
        assertEquals(highDestination, result!!.destination)
        assertEquals(2, result.windowId)
    }

    @Test fun package_metadata_does_not_change_single_destination_authority() {
        fun result(pkg: String) = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block("Destino", id = "w/label", pkg = pkg, top = 440, bottom = 490),
                block(destination, id = "w/address", pkg = pkg, top = 500, bottom = 620),
            ),
        )!!
        val uber = result("com.ubercab.driver")
        val whatsapp = result("com.whatsapp")
        val unknown = result("example.anything")
        assertEquals(uber.destination, whatsapp.destination)
        assertEquals(uber.addressSignature, whatsapp.addressSignature)
        assertEquals(uber.addressSignature, unknown.addressSignature)
    }

    @Test fun ocr_source_is_equally_eligible_when_geometry_and_semantics_are_valid() {
        val result = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block("Destino", id = "ocr/label", top = 440, bottom = 490, source = FarolUniversalVisualPipelineStage19.Source.Ocr),
                block(destination, id = "ocr/address", top = 500, bottom = 620, source = FarolUniversalVisualPipelineStage19.Source.Ocr),
            ),
        )
        assertNotNull(result)
        assertEquals(FarolUniversalVisualPipelineStage19.Source.Ocr, result!!.source)
    }

    @Test fun arbitrary_single_evaluation_without_r6_prefix_cannot_bypass_stage21_pair_gate() {
        val evaluation = FarolUniversalVisualPipelineStage19.Evaluation(
            windowId = 7,
            blockId = "foreign-single",
            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
            analysisText = destination,
            addresses = listOf(destination),
            pickup = destination,
            destination = destination,
            addressSignature = DestinationAddressIdentityPolicy.signature("visual", destination),
            screenHash = 1,
        )
        val validation = FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluation)
        assertFalse(validation.accepted)
        assertEquals("single_not_r6_authorized", validation.reason)
    }

    @Test fun r6_single_evaluation_still_revalidates_destination_with_stage21() {
        val evaluation = FarolUniversalVisualPipelineStage19.Evaluation(
            windowId = 7,
            blockId = FarolSingleDestinationFastPathStage46R6.SINGLE_BLOCK_PREFIX + "7:bad",
            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
            analysisText = "Destino\nAvenida Mendonça e",
            addresses = listOf("Avenida Mendonça e"),
            pickup = "Avenida Mendonça e",
            destination = "Avenida Mendonça e",
            addressSignature = DestinationAddressIdentityPolicy.signature("visual", "Avenida Mendonça e"),
            screenHash = 1,
        )
        assertFalse(FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluation).accepted)
    }

    @Test fun legacy_pair_evaluation_uses_unchanged_stage21_validation() {
        val pair = FarolCausalCorrectionStage21.evaluate(
            listOf(block("$pickup\n$destination", id = "w/card", top = 180, bottom = 820)),
        )!!
        val legacy = FarolCausalCorrectionStage21.validateEvaluation(pair)
        val r6 = FarolSingleDestinationFastPathStage46R6.validateEvaluation(pair)
        assertEquals(legacy.accepted, r6.accepted)
        assertEquals(legacy.reason, r6.reason)
    }

    @Test fun different_last_visual_address_changes_destination_signature() {
        val destinationB = "Rua Benjamin Carr, 700, Sapopemba, São Paulo"
        fun eval(last: String) = FarolSingleDestinationFastPathStage46R6.evaluate(
            listOf(
                block(pickup, id = "w/pickup", top = 180, bottom = 300),
                block(last, id = "w/destination", top = 700, bottom = 820),
            ),
        )!!
        assertNotEquals(eval(destination).addressSignature, eval(destinationB).addressSignature)
    }

    @Test fun duplicate_same_address_nodes_do_not_invent_two_address_candidate() {
        val decision = FarolSingleDestinationFastPathStage46R6.decide(
            listOf(
                block(destination, id = "w/parent", depth = 2, top = 500, bottom = 650),
                block(destination, id = "w/parent/child", parentId = "w/parent", depth = 4, top = 520, bottom = 620),
            ),
        )
        assertEquals(1, decision.uniqueAddressCount)
    }

    @Test fun empty_or_non_address_screen_does_not_create_candidate() {
        assertNull(FarolSingleDestinationFastPathStage46R6.evaluate(emptyList()))
        assertNull(FarolSingleDestinationFastPathStage46R6.evaluate(listOf(block("R$ 28,40\n7 min\n4,9 estrelas"))))
    }

    @Test fun service_integrates_r6_as_fallback_in_accessibility_and_ocr_paths() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 service integration"))
        assertTrue(s.split("FarolSingleDestinationFastPathStage46R6.evaluate(").size - 1 >= 2)
        assertTrue(s.contains("FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)"))
        assertTrue(s.contains("S46_R6_SINGLE_DESTINATION_FAST_PATH"))
    }

    @Test fun legacy_evaluator_is_always_attempted_before_r6_fallback() {
        val s = source("LiveRideAccessibilityService.kt")
        val r6Calls = Regex("FarolSingleDestinationFastPathStage46R6\\.evaluate\\(").findAll(s).map { it.range.first }.toList()
        assertTrue(r6Calls.size >= 2)
        r6Calls.forEach { r6Index ->
            val prefix = s.substring((r6Index - 300).coerceAtLeast(0), r6Index)
            assertTrue(prefix.contains("FarolUniversalVisualPipelineStage19.evaluate(") || prefix.contains("FarolCausalCorrectionStage21.evaluate("))
        }
    }

    @Test fun r1_bounded_pair_recovery_remains_intact_after_r6() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("FarolCausalCorrectionStage21.evaluate(listOf(blockStage46))"))
        assertTrue(s.contains("FarolCausalCorrectionStage21::validateEvaluation"))
    }

    @Test fun stage21_source_still_has_original_two_address_gate_and_is_not_weakened() {
        val s = source("FarolCausalCorrectionStage21.kt")
        assertTrue(s.contains("less_than_two_addresses"))
        assertTrue(s.contains("validateAddress(evaluation.pickup)"))
        assertTrue(s.contains("validateAddress(evaluation.destination)"))
        assertFalse(s.contains("FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6"))
    }

    @Test fun accessibility_collector_does_not_stop_after_first_address() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("FarolVisualIdentityStage23.hasTwoAddressLeads"))
        assertFalse(s.contains("hasOneAddressLead"))
        assertFalse(s.contains("hasSingleAddressLead"))
    }

    @Test fun semantic_acceptance_still_precedes_r3_target_promotion_and_route() {
        val s = source("LiveRideAccessibilityService.kt")
        val a = s.indexOf("private suspend fun processUniversalVisualStage19(")
        val b = s.indexOf("private fun stage20BindingSnapshot(", a)
        val process = s.substring(a, b)
        val semantic = process.indexOf("FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)")
        val reject = process.indexOf("if (!semanticStage21.accepted)")
        val promotion = process.indexOf("S46_R3_TARGET_PROMOTED_AFTER_STAGE21")
        val cache = process.indexOf("cachedDrivingDistancesFromAddressKm")
        assertTrue(semantic >= 0 && reject > semantic)
        assertTrue(promotion > reject)
        assertTrue(cache < 0 || cache > promotion)
    }

    @Test fun r4_latch_and_r5_atomic_rearm_are_preserved() {
        assertTrue(source("FarolStableFinalLatchStage46R4.kt").contains("FAROL_STABLE_FINAL_DECISION_LATCH_STAGE46_R4"))
        assertTrue(source("FarolAtomicTransitionStage46R5.kt").contains("FAROL_ATOMIC_TRANSITION_STAGE46_R5"))
        val service = source("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("S46_R5_ATOMIC_CLEAR_REARM_REQUESTED"))
        assertTrue(service.contains("S46_R4_FINAL_LATCH"))
    }

    @Test fun r6_introduces_no_polling_or_artificial_delay() {
        val h = source("FarolSingleDestinationFastPathStage46R6.kt")
        listOf("Thread.sleep", "delay(", "Timer(", "scheduleAtFixedRate", "scheduleWithFixedDelay").forEach {
            assertFalse(it, h.contains(it))
        }
    }

    @Test fun version_is_stage46_r6_0_1_224_5508() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5508"))
        assertTrue(b.contains("versionName = \"0.1.224\""))
    }
}
