package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolInstantAddressPresence0748Test {
    private val first = "Rua Carolina Machado, 511, Madureira, Rio de Janeiro"
    private val second = "Av. Sapopemba, 28533, Cidade Tiradentes, São Paulo - SP"

    @Test fun one_positive_address_is_detected_without_waiting_for_a_pair() {
        val evaluation = FarolInstantAddressPresence0748.detect(
            "Oferta recebida\n$first\nR$ 28,50",
            77,
        )
        assertNotNull(evaluation)
        assertEquals(1, evaluation!!.addresses.size)
        assertEquals(first, evaluation.destination)
    }

    @Test fun arbitrary_ui_text_does_not_arm_presence() {
        val evaluation = FarolInstantAddressPresence0748.detect(
            "08:33 99 •\ns 81\nR$ 31,50\n7 min",
            77,
        )
        assertTrue(evaluation == null)
    }

    @Test fun negative_observation_never_revokes_a_positive_latch() {
        val gate = FarolInstantAddressPresence0748.Gate()
        val evaluation = FarolInstantAddressPresence0748.detect(first, 7)!!
        val positive = gate.observe(evaluation, 7)
        assertTrue(positive.observedNow)
        assertTrue(positive.armPipeline)
        val negative = gate.observe(null, 7)
        assertFalse(negative.observedNow)
        assertFalse(negative.armPipeline)
        assertTrue(negative.retainedPositive)
        assertEquals("negative_observation_preserves_positive", negative.reason)
    }

    @Test fun same_address_is_coalesced_and_changed_address_rearms() {
        val gate = FarolInstantAddressPresence0748.Gate()
        val firstEvaluation = FarolInstantAddressPresence0748.detect(first, 7)!!
        val secondEvaluation = FarolInstantAddressPresence0748.detect(second, 7)!!
        assertTrue(gate.observe(firstEvaluation, 7).armPipeline)
        assertFalse(gate.observe(firstEvaluation, 7).armPipeline)
        assertTrue(gate.observe(secondEvaluation, 7).armPipeline)
    }

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(dir, "app/src/main/java/br/com/mapeiaia/rotacerta").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("project root not found")
    }

    @Test fun service_arms_before_the_precollect_duplicate_return_and_uses_paid_ai_only_after_local_ambiguity() {
        val service = File(projectRoot(), "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        val presence = service.indexOf("S748_ADDRESS_PRESENT_ARMED")
        val duplicateReturn = service.indexOf("if (!admissionStage26.heavyCollect && !instantPresence0748.armPipeline)")
        assertTrue(presence >= 0)
        assertTrue(duplicateReturn > presence)
        assertTrue(service.contains("ocr_no_candidate_openai_arbiter_0748"))
        assertTrue(service.contains(FarolInstantAddressPresence0748.CONTRACT_MARKER))
        assertTrue(service.contains(FarolInstantAddressPresence0748.MONOTONIC_MARKER))
    }
}
