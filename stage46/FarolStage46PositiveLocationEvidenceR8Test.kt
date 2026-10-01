package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolStage46PositiveLocationEvidenceR8Test {
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
        id: String = "w/block",
        top: Int = 300,
        bottom: Int = 700,
        source: FarolUniversalVisualPipelineStage19.Source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
    ) = FarolUniversalVisualPipelineStage19.VisualBlock(
        id = id,
        parentId = "w",
        metadataPackageName = "anything.visible",
        windowId = 77,
        windowLayer = 10,
        depth = 3,
        text = text,
        source = source,
        left = 20,
        top = top,
        right = 1040,
        bottom = bottom,
    )

    private val pickup = "Avenida Arquiteto Vilanova Artigas, 1396 (Vila Sapopemba, São Paulo - SP)"
    private val destination = "Rua Nara Leão, 96 (Vila Mazzei, Santo André - SP)"
    private val other = "Rua Nelson, 431 (Vila Isolina Mazzei, São Paulo - SP)"

    @Test fun physical_false_ui_destinations_never_become_route_candidates() {
        listOf(
            "Parar",
            "SALVAR",
            "Stoqui",
            "Planos de fundo",
            "Ignorar contagem regressiva",
            "Saída de mídia",
            "Iniciar gravação",
            "Fechar tudo",
            "~4,1 km",
        ).forEach { value ->
            assertNull(value, FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(value))))
            assertNull(value, FarolRouteLocationEvidenceStage46R8.evaluateImmediateText(value, 77, FarolUniversalVisualPipelineStage19.Source.Accessibility))
        }
    }

    @Test fun real_single_street_remains_immediate() {
        val result = FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(destination)))
        assertNotNull(result)
        assertEquals(destination, result!!.destination)
        assertTrue(FarolRouteLocationEvidenceStage46R8.validateEvaluation(result).accepted)
    }

    @Test fun real_two_address_card_keeps_last_visual_destination() {
        val result = FarolRouteLocationEvidenceStage46R8.evaluate(
            listOf(
                block(destination, "w/destination", 650, 780),
                block(pickup, "w/pickup", 250, 380),
            ),
        )!!
        assertEquals(pickup, result.pickup)
        assertEquals(destination, result.destination)
        assertEquals(2, result.addresses.size)
    }

    @Test fun strong_named_places_from_r7_remain_routeable() {
        listOf(
            "Hospital das Clínicas",
            "Estação da Luz (Metrô)",
            "Shopping Interlar Aricanduva",
            "Parque do Carmo",
        ).forEach { value ->
            val result = FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(value)))
            assertNotNull(value, result)
            assertEquals(value, result!!.destination)
        }
    }

    @Test fun named_place_category_without_identity_fails_closed() {
        listOf("Hospital", "Estação", "Shopping", "Parque").forEach { value ->
            assertNull(value, FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(value))))
        }
    }

    @Test fun trailing_operational_prices_are_removed_from_route_identity() {
        val contaminated = "$destination R$ 11 R$ 12 R$ 13 R$ 14"
        val result = FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(contaminated)))!!
        assertEquals(destination, result.destination)
        assertFalse(result.addressSignature.contains("r 11"))
        assertFalse(result.addressSignature.contains("r 14"))
    }

    @Test fun legitimate_house_number_is_preserved() {
        val value = "Rua Carolina Machado, 970, Madureira, Rio de Janeiro"
        assertEquals(value, FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(value)))!!.destination)
    }

    @Test fun same_current_destination_in_cheap_text_never_clears_final() {
        val current = DestinationAddressIdentityPolicy.signature("visual", destination)
        val proof = FarolRouteLocationEvidenceStage46R8.proveDestinationReplacement("$pickup\n$destination", current)
        assertFalse(proof.proven)
        assertEquals("current_destination_still_visible", proof.reason)
    }

    @Test fun different_two_address_card_proves_replacement_before_heavy_collect() {
        val current = DestinationAddressIdentityPolicy.signature("visual", destination)
        val proof = FarolRouteLocationEvidenceStage46R8.proveDestinationReplacement("$pickup\n$other", current)
        assertTrue(proof.proven)
        assertEquals(2, proof.positiveLocationCount)
        assertEquals(DestinationAddressIdentityPolicy.signature("visual", other), proof.candidateSignature)
    }

    @Test fun explicit_new_destination_single_address_proves_replacement() {
        val current = DestinationAddressIdentityPolicy.signature("visual", destination)
        val proof = FarolRouteLocationEvidenceStage46R8.proveDestinationReplacement("Destino\n$other", current)
        assertTrue(proof.proven)
        assertEquals(1, proof.positiveLocationCount)
        assertEquals(DestinationAddressIdentityPolicy.signature("visual", other), proof.candidateSignature)
    }

    @Test fun lone_partial_address_event_does_not_reintroduce_r4_flicker() {
        val current = DestinationAddressIdentityPolicy.signature("visual", destination)
        val proof = FarolRouteLocationEvidenceStage46R8.proveDestinationReplacement(other, current)
        assertFalse(proof.proven)
        assertEquals("single_unlabeled_location_not_destructive", proof.reason)
    }

    @Test fun arbitrary_ui_change_cannot_destructively_clear_final() {
        val current = DestinationAddressIdentityPolicy.signature("visual", destination)
        val proof = FarolRouteLocationEvidenceStage46R8.proveDestinationReplacement("Parar\nSALVAR\nPlanos de fundo", current)
        assertFalse(proof.proven)
        assertEquals("no_positive_location", proof.reason)
    }

    @Test fun changed_destination_changes_route_identity() {
        val a = FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(destination)))!!
        val b = FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block(other)))!!
        assertNotEquals(a.addressSignature, b.addressSignature)
        assertNotEquals(a.screenHash, b.screenHash)
    }

    @Test fun service_runtime_authority_moves_from_r7_to_r8() {
        val s = source("LiveRideAccessibilityService.kt")
        assertTrue(s.contains("FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 service integration"))
        assertTrue(s.split("FarolRouteLocationEvidenceStage46R8.evaluate(").size - 1 >= 2)
        assertTrue(s.contains("FarolRouteLocationEvidenceStage46R8.evaluateImmediateText("))
        assertTrue(s.contains("FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)"))
        assertTrue(s.contains("S46_R8_POSITIVE_SINGLE_LOCATION"))
        assertTrue(s.contains("S46_R8_LAST_VALID_LOCATION"))
        assertTrue(s.contains("S46_R8_PROVEN_DESTINATION_CHANGE_CLEARED_PRECOLLECT"))
        assertFalse(s.contains("FarolImmediateAddressRouteStage46R7.evaluate(collectionStage26.blocks)"))
    }

    @Test fun google_cache_radius_and_r4_r5_are_not_reimplemented_by_r8() {
        val helper = source("FarolRouteLocationEvidenceStage46R8.kt")
        listOf("GoogleMapsService", "LiveRideRouteCache", "DecisionEngine", "WorkRegionTargetPolicy", "showOverlay(").forEach {
            assertFalse(it, helper.contains(it))
        }
        val service = source("LiveRideAccessibilityService.kt")
        assertTrue(service.contains("S46_R4_FINAL_LATCH"))
        assertTrue(service.contains("S46_R5_ATOMIC_CLEAR_REARM_REQUESTED"))
    }

    @Test fun r8_has_no_polling_timer_or_delay() {
        val helper = source("FarolRouteLocationEvidenceStage46R8.kt")
        listOf("Thread.sleep", "delay(", "Timer(", "scheduleAtFixedRate", "scheduleWithFixedDelay").forEach {
            assertFalse(it, helper.contains(it))
        }
    }

    @Test fun version_is_stage46_r8_0_1_226_5510() {
        val b = File(projectRoot(), "app/build.gradle.kts").readText()
        assertTrue(b.contains("versionCode = 5510"))
        assertTrue(b.contains("versionName = \"0.1.226\""))
    }
}
