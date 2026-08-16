package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46ImmediateAddressRouteR7Test {
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

    private val first = "Rua Carolina Machado, 511, Madureira, Rio de Janeiro"
    private val second = "Rua Clarimundo de Melo, 581, Encantado, Rio de Janeiro"
    private val third = "Rua Benjamin Carr, 596, Sapopemba, São Paulo"

    @Test fun r7_contract_markers_are_present() {
        val h = source("FarolImmediateAddressRouteStage46R7.kt")
        listOf(
            "FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7",
            "FIRST_VALID_ADDRESS_STARTS_ROUTE_IMMEDIATELY_STAGE46_R7",
            "LAST_VISIBLE_ADDRESS_REPLACES_DESTINATION_STAGE46_R7",
            "SINGLE_ADDRESS_EVENT_TEXT_AVOIDS_OCR_WAIT_STAGE46_R7",
            "STAGE21_ADDRESS_STRUCTURE_RETAINED_STAGE46_R7",
            "PACKAGE_IDENTITY_NEVER_AUTHORIZES_ROUTE_STAGE46_R7",
            "EXISTING_VISUAL_EPOCH_ROUTE_AND_PAINT_FRESHNESS_RETAINED_STAGE46_R7",
            "EVENT_DRIVEN_IMMEDIATE_ADDRESS_NO_POLLING_STAGE46_R7",
        ).forEach { assertTrue(it, h.contains(it)) }
    }

    @Test fun one_valid_address_immediately_becomes_destination_without_destination_cue() {
        val decision = FarolImmediateAddressRouteStage46R7.decide(
            listOf(block(first, top = 120, bottom = 230)),
        )
        assertNotNull(decision.evaluation)
        assertTrue(decision.immediateSingle)
        assertEquals("single_valid_address_immediate", decision.reason)
        assertEquals(first, decision.evaluation!!.destination)
        assertEquals(1, decision.evaluation!!.addresses.size)
    }

    @Test fun origin_label_does_not_delay_a_single_valid_address_anymore() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block("Origem", id = "w/label", top = 100, bottom = 150),
                block(first, id = "w/address", top = 170, bottom = 280),
            ),
        )
        assertNotNull(result)
        assertEquals(first, result!!.destination)
    }

    @Test fun pickup_label_does_not_delay_a_single_valid_address_anymore() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block("Embarque / Buscar passageiro", id = "w/label", top = 100, bottom = 150),
                block(first, id = "w/address", top = 170, bottom = 280),
            ),
        )
        assertNotNull(result)
        assertEquals(first, result!!.destination)
    }

    @Test fun conflicting_role_labels_do_not_override_address_authority() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block("Origem", id = "w/a", top = 80, bottom = 120),
                block("Destino", id = "w/b", top = 130, bottom = 170),
                block(first, id = "w/address", top = 180, bottom = 300),
            ),
        )
        assertNotNull(result)
        assertEquals(first, result!!.destination)
    }

    @Test fun one_valid_address_does_not_wait_for_geometry() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(block(first, top = Int.MAX_VALUE, bottom = Int.MAX_VALUE, right = 0)),
        )
        assertNotNull(result)
        assertEquals(first, result!!.destination)
    }

    @Test fun text_that_is_not_a_structurally_valid_address_is_still_rejected() {
        assertNull(FarolImmediateAddressRouteStage46R7.evaluate(listOf(block("Avenida Mendonça e"))))
        assertNull(FarolImmediateAddressRouteStage46R7.evaluate(listOf(block("R$ 31,50\n7 min\n4,9 estrelas"))))
    }

    @Test fun event_text_exactly_one_valid_address_skips_ocr_wait() {
        val result = FarolImmediateAddressRouteStage46R7.evaluateImmediateText(
            "Oferta recebida\n$first\nR$ 28,50",
            77,
            FarolUniversalVisualPipelineStage19.Source.Accessibility,
        )
        assertNotNull(result)
        assertEquals(first, result!!.destination)
        assertTrue(result.blockId.startsWith(FarolImmediateAddressRouteStage46R7.EVENT_TEXT_BLOCK_PREFIX))
    }

    @Test fun event_text_with_multiple_addresses_never_guesses_their_visual_order() {
        assertNull(
            FarolImmediateAddressRouteStage46R7.evaluateImmediateText(
                "$first\n$second",
                77,
                FarolUniversalVisualPipelineStage19.Source.Accessibility,
            ),
        )
    }

    @Test fun two_split_addresses_choose_geometrically_last_destination() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block(second, id = "w/last", top = 700, bottom = 820),
                block(first, id = "w/first", top = 180, bottom = 300),
            ),
        )
        assertNotNull(result)
        assertEquals(first, result!!.pickup)
        assertEquals(second, result.destination)
        assertEquals(2, result.addresses.size)
    }

    @Test fun input_order_cannot_override_physical_vertical_order() {
        val a = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block(first, id = "w/first", top = 180, bottom = 300),
                block(second, id = "w/last", top = 700, bottom = 820),
            ),
        )!!
        val b = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block(second, id = "w/last", top = 700, bottom = 820),
                block(first, id = "w/first", top = 180, bottom = 300),
            ),
        )!!
        assertEquals(second, a.destination)
        assertEquals(second, b.destination)
        assertEquals(a.addressSignature, b.addressSignature)
    }

    @Test fun three_addresses_choose_the_visually_last_one() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block(second, id = "w/middle", top = 420, bottom = 540),
                block(third, id = "w/last", top = 760, bottom = 880),
                block(first, id = "w/first", top = 120, bottom = 240),
            ),
        )!!
        assertEquals(first, result.pickup)
        assertEquals(third, result.destination)
        assertEquals(listOf(first, second, third), result.addresses)
    }

    @Test fun addresses_inside_one_block_use_parser_order_and_last_wins() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(block("$first\n$second\n$third", top = 120, bottom = 900)),
        )!!
        assertEquals(third, result.destination)
    }

    @Test fun multiple_addresses_without_geometry_use_deterministic_detected_order() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block(first, id = "w/1", top = Int.MAX_VALUE, bottom = Int.MAX_VALUE, right = 0),
                block(second, id = "w/2", top = Int.MAX_VALUE, bottom = Int.MAX_VALUE, right = 0),
            ),
        )!!
        assertEquals(second, result.destination)
    }

    @Test fun duplicate_nodes_for_same_address_do_not_invent_multiple_destinations() {
        val decision = FarolImmediateAddressRouteStage46R7.decide(
            listOf(
                block(first, id = "w/parent", depth = 2, top = 500, bottom = 650),
                block(first, id = "w/parent/child", parentId = "w/parent", depth = 4, top = 520, bottom = 620),
            ),
        )
        assertEquals(1, decision.uniqueAddressCount)
        assertNotNull(decision.evaluation)
    }

    @Test fun package_metadata_never_changes_address_decision() {
        fun eval(pkg: String) = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(block(first, pkg = pkg)),
        )!!
        val uber = eval("com.ubercab.driver")
        val whatsapp = eval("com.whatsapp")
        val unknown = eval("anything.visible")
        assertEquals(uber.destination, whatsapp.destination)
        assertEquals(uber.addressSignature, unknown.addressSignature)
    }

    @Test fun ocr_is_equally_eligible_for_immediate_single_address() {
        val result = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(block(first, source = FarolUniversalVisualPipelineStage19.Source.Ocr)),
        )!!
        assertEquals(FarolUniversalVisualPipelineStage19.Source.Ocr, result.source)
        assertEquals(first, result.destination)
    }

    @Test fun arbitrary_foreign_single_evaluation_cannot_bypass_r7_authorization() {
        val evaluation = FarolUniversalVisualPipelineStage19.Evaluation(
            windowId = 7,
            blockId = "foreign-single",
            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
            analysisText = first,
            addresses = listOf(first),
            pickup = first,
            destination = first,
            addressSignature = DestinationAddressIdentityPolicy.signature("visual", first),
            screenHash = 1,
        )
        val validation = FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluation)
        assertFalse(validation.accepted)
        assertEquals("single_not_r7_authorized", validation.reason)
    }

    @Test fun r7_single_still_requires_stage21_address_structure() {
        val bad = "Avenida Mendonça e"
        val evaluation = FarolUniversalVisualPipelineStage19.Evaluation(
            windowId = 7,
            blockId = FarolImmediateAddressRouteStage46R7.SINGLE_BLOCK_PREFIX + "7:bad",
            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
            analysisText = bad,
            addresses = listOf(bad),
            pickup = bad,
            destination = bad,
            addressSignature = DestinationAddressIdentityPolicy.signature("visual", bad),
            screenHash = 1,
        )
        assertFalse(FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluation).accepted)
    }

    @Test fun legacy_pair_validation_remains_exactly_stage21_authority() {
        val pair = FarolCausalCorrectionStage21.evaluate(
            listOf(block("$first\n$second", top = 120, bottom = 820)),
        )!!
        val legacy = FarolCausalCorrectionStage21.validateEvaluation(pair)
        val r7 = FarolImmediateAddressRouteStage46R7.validateEvaluation(pair)
        assertEquals(legacy.accepted, r7.accepted)
        assertEquals(legacy.reason, r7.reason)
    }

    @Test fun a_later_address_replaces_the_previous_route_identity() {
        val one = FarolImmediateAddressRouteStage46R7.evaluate(listOf(block(first)))!!
        val two = FarolImmediateAddressRouteStage46R7.evaluate(
            listOf(
                block(first, id = "w/first", top = 180, bottom = 300),
                block(second, id = "w/last", top = 700, bottom = 820),
            ),
        )!!
        assertEquals(first, one.destination)
        assertEquals(second, two.destination)
        assertNotEquals(one.addressSignature, two.addressSignature)
        assertNotEquals(one.screenHash, two.screenHash)
    }

    @Test fun service_uses_r7_in_accessibility_and_ocr_and_event_single_fallback() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration"))
        assertTrue(s.split("FarolImmediateAddressRouteStage46R7.evaluate(").size - 1 >= 2)
        assertTrue(s.contains("FarolImmediateAddressRouteStage46R7.evaluateImmediateText("))
        assertTrue(s.contains("cheapSignalStage26.sourceText"))
        assertTrue(s.contains("FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)"))
        assertTrue(s.contains("S46_R7_IMMEDIATE_SINGLE_ADDRESS"))
        assertTrue(s.contains("S46_R7_LAST_VISUAL_DESTINATION"))
        assertFalse(s.contains("FarolSingleDestinationFastPathStage46R6.evaluate("))
    }

    @Test fun legacy_pair_evaluator_still_precedes_r7_fallback() {
        val s = source("LiveRideAccessibilityService.kt")
        val r7Calls = Regex("FarolImmediateAddressRouteStage46R7\\.evaluate\\(").findAll(s).map { it.range.first }.toList()
        assertTrue(r7Calls.size >= 2)
        r7Calls.forEach { r7Index ->
            val prefix = s.substring((r7Index - 420).coerceAtLeast(0), r7Index)
            assertTrue(prefix.contains("FarolUniversalVisualPipelineStage19.evaluate(") || prefix.contains("FarolCausalCorrectionStage21.evaluate("))
        }
    }

    @Test fun r4_r5_freshness_and_no_polling_contracts_remain_intact() {
        val service = source("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("S46_R5_ATOMIC_CLEAR_REARM_REQUESTED"))
        assertTrue(service.contains("S46_R4_FINAL_LATCH"))
        val helper = source("FarolImmediateAddressRouteStage46R7.kt")
        listOf("Thread.sleep", "delay(", "Timer(", "scheduleAtFixedRate", "scheduleWithFixedDelay").forEach {
            assertFalse(it, helper.contains(it))
        }
    }

    @Test fun version_is_stage46_r7_0_1_225_5509() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5509"))
        assertTrue(b.contains("versionName = \"0.1.225\""))
    }
}