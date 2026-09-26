package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0635Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun physical_report_address_variants_are_one_destination_identity() {
        val complete = DestinationAddressIdentityPolicy.signature(
            "visual",
            "Rua Leopoldo Delisle, 555 (Jardim Sao Vicente, São Paulo - SP)",
        )
        val short = DestinationAddressIdentityPolicy.signature(
            "visual",
            "Rua Leopoldo Delisle, 555",
        )
        val contaminated = DestinationAddressIdentityPolicy.signature(
            "visual",
            "Rua Leopoldo Delisle, 555 (Jardim Sao Vicente, São Pedidos de viagem",
        )
        assertTrue(DestinationAddressIdentityPolicy.sameDestinationSignatures(complete, short))
        assertTrue(DestinationAddressIdentityPolicy.sameDestinationSignatures(complete, contaminated))
    }

    @Test
    fun different_house_number_is_still_a_real_destination_change() {
        val a = DestinationAddressIdentityPolicy.signature("visual", "Rua Silvio Barbini, 167A")
        val b = DestinationAddressIdentityPolicy.signature("visual", "Rua Silvio Barbini, 168")
        assertFalse(DestinationAddressIdentityPolicy.sameDestinationSignatures(a, b))
    }

    @Test
    fun service_coalesces_equivalent_variant_before_runtime_lease_binding() {
        val live = src("LiveRideAccessibilityService.kt")
        val stable = live.indexOf("val stableAddressSignatureStage635")
        val bind = live.indexOf("stage36RuntimeAuthority.bindDestination(stableAddressSignatureStage635)", stable)
        val changed = live.indexOf("val visualChangedStage19 = universalActiveAddressSignature != stableAddressSignatureStage635", bind)
        assertTrue(stable >= 0)
        assertTrue(bind > stable)
        assertTrue(changed > bind)
        assertTrue(live.contains("S635_EQUIVALENT_ADDRESS_VARIANT_COALESCED"))
    }

    @Test
    fun incomplete_ocr_frame_preserves_active_semantic_lease() {
        val live = src("LiveRideAccessibilityService.kt")
        val marker = live.indexOf("S635_TRANSIENT_NO_CANDIDATE_INFLIGHT_PRESERVED")
        val clear = live.indexOf("Snapshot visual atual sem dois endereços semanticamente completos Stage23 e sem lease Stage44 ativa nem lease semântica em andamento.", marker)
        assertTrue(marker >= 0)
        assertTrue(clear > marker)
        val block = live.substring((marker - 1400).coerceAtLeast(0), (clear + 500).coerceAtMost(live.length))
        assertTrue(block.contains("transientSemanticLeaseStage635"))
        assertTrue(block.contains("hardClear=false"))
    }

    @Test
    fun pending_same_card_lease_cannot_fall_through_to_generation_invalidation() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("val pendingSemanticLeaseStage635")
        val dispatch = live.indexOf("if (evaluationStage19 != null) {", start)
        assertTrue(start >= 0 && dispatch > start)
        val block = live.substring(start, dispatch)
        assertTrue(block.contains("S635_INFLIGHT_SEMANTIC_LEASE_PRESERVED"))
        assertTrue(block.contains("if (pendingSemanticLeaseStage635) {"))
        assertTrue(block.contains("} else if (verifyWithoutBlinkStage46R4) {"))
    }

    @Test
    fun release_history_retains_0635_5926_baseline() {
        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.635\""))
        assertTrue(history.contains("\"build\": 5926"))
        assertTrue(history.contains("\"branch\": \"agent/farol-edge-stability-0.1.635\""))
    }

    @Test
    fun final_lease_uses_semantic_destination_compatibility_not_raw_string_equality() {
        val stage44 = src("FarolSemanticFinalLeaseStage44.kt")
        assertTrue(stage44.contains("DestinationAddressIdentityPolicy.sameDestinationSignatures(current, candidate)"))
        assertFalse(stage44.contains("return current == candidate"))
    }
}
