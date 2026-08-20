package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46UniversalVisibleLocationR7Test {
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
        id: String,
        top: Int,
        bottom: Int,
        pkg: String? = "anything.visible",
        source: FarolUniversalVisualPipelineStage19.Source =
            FarolUniversalVisualPipelineStage19.Source.Accessibility,
    ) = FarolUniversalVisualPipelineStage19.VisualBlock(
        id = id,
        parentId = "w",
        metadataPackageName = pkg,
        windowId = 77,
        windowLayer = 10,
        depth = 4,
        text = text,
        source = source,
        left = 20,
        top = top,
        right = 1040,
        bottom = bottom,
    )

    @Test fun single_hospital_named_place_routes_without_house_number_or_destination_cue() {
        val result = FarolRouteLocationEvidenceStage46R8.evaluate(
            listOf(block("Hospital das Clínicas", "w/hospital", 400, 520)),
        )
        assertNotNull(result)
        assertEquals("Hospital das Clínicas", result!!.destination)
        assertTrue(FarolRouteLocationEvidenceStage46R8.validateEvaluation(result).accepted)
    }

    @Test fun single_station_named_place_routes_without_street_city_or_state_requirement() {
        val result = FarolRouteLocationEvidenceStage46R8.evaluate(
            listOf(block("Estação da Luz (Metrô)", "w/luz", 400, 520)),
        )
        assertNotNull(result)
        assertEquals("Estação da Luz (Metrô)", result!!.destination)
        assertTrue(FarolRouteLocationEvidenceStage46R8.validateEvaluation(result).accepted)
    }

    @Test fun street_without_house_number_routes_immediately() {
        val value = "Avenida Doutor Enéas Carvalho de Aguiar - Cerqueira César, São Paulo - SP"
        val result = FarolRouteLocationEvidenceStage46R8.evaluate(
            listOf(block(value, "w/street", 400, 560)),
        )
        assertNotNull(result)
        assertEquals(value, result!!.destination)
    }

    @Test fun physical_case_001170_estacao_luz_to_hospital_clinicas_routes_last_visual_location() {
        val station = """
            Estação da Luz (Metrô)
            (Centro Histórico de São
            Paulo, São Paulo - SP)
        """.trimIndent()
        val hospital = """
            Hospital das Clínicas
            (Avenida Doutor Enéas
            Carvalho de Aguiar -
            Cerqueira César, São Paulo
            - SP)
        """.trimIndent()

        val result = FarolRouteLocationEvidenceStage46R8.evaluate(
            listOf(
                block(station, "w/station", 260, 480, source = FarolUniversalVisualPipelineStage19.Source.Ocr),
                block(hospital, "w/hospital", 560, 900, source = FarolUniversalVisualPipelineStage19.Source.Ocr),
            ),
        )
        assertNotNull(result)
        assertTrue(result!!.destination.contains("Hospital das Clínicas"))
        assertTrue(FarolRouteLocationEvidenceStage46R8.validateEvaluation(result).accepted)
    }

    @Test fun visually_last_named_place_wins_even_when_input_order_is_reversed() {
        val result = FarolRouteLocationEvidenceStage46R8.evaluate(
            listOf(
                block("Hospital das Clínicas", "w/hospital", 700, 820),
                block("Estação da Luz (Metrô)", "w/luz", 200, 320),
            ),
        )
        assertNotNull(result)
        assertEquals("Hospital das Clínicas", result!!.destination)
    }

    @Test fun package_identity_never_changes_named_place_authority() {
        fun destination(pkg: String) = FarolRouteLocationEvidenceStage46R8.evaluate(
            listOf(block("Hospital das Clínicas", "w/hospital", 400, 520, pkg = pkg)),
        )!!.destination
        assertEquals(destination("com.ubercab.driver"), destination("com.whatsapp"))
        assertEquals(destination("com.whatsapp"), destination("com.google.android.apps.photos"))
        assertEquals(destination("com.google.android.apps.photos"), destination("arbitrary.files.viewer"))
    }

    @Test fun only_negative_technical_hygiene_rejects_obvious_non_location_values() {
        assertNull(FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block("R$ 31,50", "w/fare", 400, 500))))
        assertNull(FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block("7 min", "w/time", 400, 500))))
        assertNull(FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block("4,2 km", "w/distance", 400, 500))))
        assertNull(FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block("85%", "w/percent", 400, 500))))
        assertNull(FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block("Aceitar", "w/action", 400, 500))))
    }

    @Test fun truncated_broken_fragment_is_hygiene_rejected_without_requiring_stage21() {
        assertNull(
            FarolRouteLocationEvidenceStage46R8.evaluate(
                listOf(block("Avenida Mendonça e", "w/truncated", 400, 500)),
            ),
        )
        val helper = source("FarolImmediateAddressRouteStage46R7.kt")
        assertFalse(helper.contains("FarolCausalCorrectionStage21.validateAddress("))
    }

    @Test fun r7_helper_declares_universal_location_and_no_semantic_veto_contract() {
        val helper = source("FarolImmediateAddressRouteStage46R7.kt")
        listOf(
            "ANY_VISIBLE_LOCATION_CAN_START_REAL_ROUTE_STAGE46_R7",
            "NO_STAGE21_SEMANTIC_VETO_FOR_R7_LOCATION_STAGE46_R7",
            "NAMED_PLACE_WITHOUT_HOUSE_NUMBER_IS_ROUTEABLE_STAGE46_R7",
            "ONLY_NEGATIVE_TECHNICAL_HYGIENE_BEFORE_ROUTE_STAGE46_R7",
            "CASE_001170_ESTACAO_LUZ_HOSPITAL_CLINICAS_REGRESSION_STAGE46_R7",
        ).forEach { assertTrue(it, helper.contains(it)) }
    }

    @Test fun r7_keeps_freshness_downstream_and_introduces_no_timer_polling() {
        val helper = source("FarolImmediateAddressRouteStage46R7.kt")
        assertTrue(helper.contains("EXISTING_VISUAL_EPOCH_ROUTE_AND_PAINT_FRESHNESS_RETAINED_STAGE46_R7"))
        listOf("Thread.sleep", "delay(", "Timer(", "scheduleAtFixedRate", "scheduleWithFixedDelay").forEach {
            assertFalse(it, helper.contains(it))
        }
    }
}
