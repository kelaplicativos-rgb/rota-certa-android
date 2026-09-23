package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0641Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun active_semantic_lease_is_same_app_authority_without_window_id() {
        assertTrue(
            FarolSemanticLeaseContinuityStage641.activeLeaseForSameApp(
                hasModels = true,
                admittedPackage = "sinet.startup.indriver",
                authorityPackage = "sinet.startup.indriver",
                activeAddressSignature = "visual|travessa revoadas 58",
            ),
        )
        assertFalse(
            FarolSemanticLeaseContinuityStage641.activeLeaseForSameApp(
                hasModels = true,
                admittedPackage = "sinet.startup.indriver",
                authorityPackage = "com.other.driver",
                activeAddressSignature = "visual|travessa revoadas 58",
            ),
        )
    }

    @Test
    fun semantic_lease_requires_previous_exact_admission_and_active_destination() {
        assertFalse(
            FarolSemanticLeaseContinuityStage641.activeLeaseForSameApp(
                hasModels = true,
                admittedPackage = null,
                authorityPackage = "sinet.startup.indriver",
                activeAddressSignature = "visual|travessa revoadas 58",
            ),
        )
        assertFalse(
            FarolSemanticLeaseContinuityStage641.activeLeaseForSameApp(
                hasModels = true,
                admittedPackage = "sinet.startup.indriver",
                authorityPackage = "sinet.startup.indriver",
                activeAddressSignature = null,
            ),
        )
    }

    @Test
    fun idle_signature_miss_is_idempotent_and_never_requests_hard_reset() {
        assertEquals(
            FarolSemanticLeaseContinuityStage641.BlockAction.NOOP,
            FarolSemanticLeaseContinuityStage641.blockAction(
                hasDestructiveState = false,
                alreadyWaitingYellow = true,
            ),
        )
        assertEquals(
            FarolSemanticLeaseContinuityStage641.BlockAction.PAINT_WAITING_ONLY,
            FarolSemanticLeaseContinuityStage641.blockAction(
                hasDestructiveState = false,
                alreadyWaitingYellow = false,
            ),
        )
        assertEquals(
            FarolSemanticLeaseContinuityStage641.BlockAction.HARD_RESET,
            FarolSemanticLeaseContinuityStage641.blockAction(
                hasDestructiveState = true,
                alreadyWaitingYellow = true,
            ),
        )
    }

    @Test
    fun service_no_longer_uses_entry_window_as_semantic_lease_identity() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun admitTrainedCardStage640")
        val end = live.indexOf("private fun masterResetCardAdmissionStage639", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("FarolSemanticLeaseContinuityStage641.activeLeaseForSameApp"))
        assertFalse(block.contains("root640?.windowId == stage640AdmittedWindowId"))
        assertFalse(live.contains("private var stage640AdmittedWindowId"))
    }

    @Test
    fun semantic_lease_bypass_occurs_before_structural_matcher() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun admitTrainedCardStage640")
        val lease = live.indexOf("if (sameSurfaceLease640)", start)
        val matcher = live.indexOf("matchesTrainedCardSignature638(packageName639, root640)", start)
        assertTrue(start >= 0)
        assertTrue(lease > start)
        assertTrue(matcher > lease)
        assertTrue(live.substring(lease, matcher).contains("return true"))
    }

    @Test
    fun notification_during_active_lease_is_ignored_without_reset_or_ocr_wakeup() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("if (sameSurfaceLease640)")
        val notification = live.indexOf("if (trigger639 == \"notification\")", start)
        val matcher = live.indexOf("matchesTrainedCardSignature638(packageName639, root640)", start)
        assertTrue(notification > start)
        assertTrue(notification < matcher)
        val notificationBlock = live.substring(notification, matcher)
        assertTrue(notificationBlock.contains("stage641NotificationIgnoredDuringLease"))
        assertTrue(notificationBlock.contains("return false"))
        assertFalse(notificationBlock.contains("masterResetCardAdmissionStage639("))
    }

    @Test
    fun repeated_idle_signature_miss_does_not_enter_hard_clear() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("FarolSemanticLeaseContinuityStage641.BlockAction.NOOP")
        val end = live.indexOf("return false", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertFalse(block.contains("masterResetCardAdmissionStage639("))
        assertFalse(block.contains("hardClearUniversalTwoAddress("))
        assertTrue(block.contains("stage641IdleSignatureMissNoHardClear"))
    }

    @Test
    fun destructive_stale_state_is_still_fail_closed() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("FarolSemanticLeaseContinuityStage641.BlockAction.HARD_RESET")
        val end = live.indexOf("FarolSemanticLeaseContinuityStage641.BlockAction.PAINT_WAITING_ONLY", start)
        assertTrue(start >= 0 && end > start)
        val block = live.substring(start, end)
        assertTrue(block.contains("masterResetCardAdmissionStage639("))
    }

    @Test
    fun release_history_preserves_0641_5932_contract() {
        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.641\""))
        assertTrue(history.contains("\"build\": 5932"))
        assertTrue(history.contains("windowId"))
        assertTrue(history.contains("tempestade"))
    }
}
