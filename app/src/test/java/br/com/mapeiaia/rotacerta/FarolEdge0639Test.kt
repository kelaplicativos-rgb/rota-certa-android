package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0639Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    @Test
    fun admission_is_fail_closed_without_training() {
        val decision = FarolCardAdmissionStage639.decide(
            hasModels = false,
            signatureMatched = false,
        )
        assertFalse(decision.allowHeavyPipeline)
        assertTrue(decision.outcome == FarolCardAdmissionStage639.Outcome.BLOCK_UNTRAINED)
    }

    @Test
    fun admission_is_fail_closed_on_signature_miss() {
        val decision = FarolCardAdmissionStage639.decide(
            hasModels = true,
            signatureMatched = false,
        )
        assertFalse(decision.allowHeavyPipeline)
        assertTrue(decision.outcome == FarolCardAdmissionStage639.Outcome.BLOCK_SIGNATURE_MISS)
    }

    @Test
    fun only_trained_matching_card_can_enter_heavy_pipeline() {
        val decision = FarolCardAdmissionStage639.decide(
            hasModels = true,
            signatureMatched = true,
        )
        assertTrue(decision.allowHeavyPipeline)
        assertTrue(decision.outcome == FarolCardAdmissionStage639.Outcome.ALLOW_MATCHED)
    }

    @Test
    fun notification_is_gated_before_notification_ocr_wakeup() {
        val live = src("LiveRideAccessibilityService.kt")
        val event = live.indexOf("TYPE_NOTIFICATION_STATE_CHANGED")
        val gate = live.indexOf("admitTrainedCardStage639(notificationPackage639, \"notification\")", event)
        val wake = live.indexOf("handleNotificationWakeup0169(", gate)
        assertTrue(event >= 0)
        assertTrue(gate > event)
        assertTrue(wake > gate)
    }

    @Test
    fun normal_event_is_gated_before_stage38_and_stage19() {
        val live = src("LiveRideAccessibilityService.kt")
        val gate = live.indexOf("admitTrainedCardStage639(authorityPackage638, \"accessibility_event\")")
        val stage38 = live.indexOf("S38_ACCESSIBILITY_EVENT_RECEIVED", gate)
        val stage19 = live.indexOf("handleUniversalVisualEventStage19", gate)
        assertTrue(gate >= 0)
        assertTrue(stage38 > gate)
        assertTrue(stage19 > gate)
    }

    @Test
    fun scheduled_analysis_is_fail_closed_before_heavy_collection() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun scheduleVisibleTextAnalysis")
        val gate = live.indexOf("admitTrainedCardStage639(scheduledPackage638, \"scheduled_analysis\")", start)
        val heavy = live.indexOf("heavyCollectionsStarted", gate)
        assertTrue(start >= 0)
        assertTrue(gate > start)
        assertTrue(heavy > gate)
    }

    @Test
    fun master_reset_cancels_notification_wakeup_before_public_clear() {
        val live = src("LiveRideAccessibilityService.kt")
        val start = live.indexOf("private fun masterResetCardAdmissionStage639")
        val cancel = live.indexOf("notificationWakeJob0169?.cancel()", start)
        val invalidate = live.indexOf("notificationWakeGate0169.invalidate()", start)
        val clear = live.indexOf("hardClearUniversalTwoAddress(", start)
        assertTrue(start >= 0)
        assertTrue(invalidate > start)
        assertTrue(cancel > start)
        assertTrue(clear > cancel)
        assertTrue(live.substring(start, clear).contains("notificationWakeJob0169 = null"))
    }

    @Test
    fun manual_training_immediately_rechecks_current_card() {
        val live = src("LiveRideAccessibilityService.kt")
        val trained = live.indexOf("S638_CARD_SIGNATURE_TRAINED")
        val immediate = live.indexOf("S639_POST_TRAINING_IMMEDIATE_ANALYSIS", trained)
        val schedule = live.indexOf("scheduleVisibleTextAnalysis(0L, allowPopupCandidate = true)", immediate)
        assertTrue(trained >= 0)
        assertTrue(immediate > trained)
        assertTrue(schedule > immediate)
    }

    @Test
    fun release_metadata_is_0639_5930() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("releaseVersionName = \"0.1.639\""))
        assertTrue(gradle.contains("releaseVersionCode = 5_930"))

        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.639\""))
        assertTrue(history.contains("\"build\": 5930"))
        assertTrue(history.contains("apps selecionados sem modelo continuavam no pipeline legado"))
    }
}
