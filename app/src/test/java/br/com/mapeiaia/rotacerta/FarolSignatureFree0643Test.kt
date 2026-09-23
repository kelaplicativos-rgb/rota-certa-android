package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolSignatureFree0643Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    @Test
    fun selected_app_is_the_only_entry_authority_and_signature_gate_is_absent() {
        val live = src("LiveRideAccessibilityService.kt")
        assertTrue(live.contains("FarolPackageEntryGate638.decide("))
        assertTrue(live.contains("S643_SIGNATURE_FREE_PACKAGE_ADMISSION"))
        assertTrue(live.contains("selectedPackage=true; cardSignatureRequired=false"))
        assertFalse(live.contains("admitTrainedCardStage640("))
        assertFalse(live.contains("matchesTrainedCardSignature638("))
        assertFalse(live.contains("masterResetCardAdmissionStage639("))
        assertFalse(live.contains("farolCardSignatureStore638"))
        assertFalse(live.contains("farolCardTrainingModule638"))
        assertFalse(live.contains("stage640EventSignatureGateRejected"))
        assertFalse(live.contains("stage640ScheduledSignatureGateRejected"))
    }

    @Test
    fun memorizar_card_is_removed_from_active_shortcut_catalog_and_migration() {
        val catalog = src("BubbleShortcutModule.kt")
        val grid = src("ShortcutGridCustomization0179.kt")
        val live = src("LiveRideAccessibilityService.kt")

        assertFalse(catalog.contains("FarolCardTrainingBubbleShortcutModule638"))
        assertFalse(catalog.contains("id = \"farol_card_training\""))
        assertFalse(catalog.contains("label = \"Memorizar card\""))
        assertFalse(grid.contains("applyStage638SignatureShortcutMigration"))
        assertFalse(grid.contains("KEY_STAGE638_SIGNATURE_SHORTCUT_MIGRATED"))
        assertFalse(live.contains("memorizeFarolCard638"))
        assertFalse(live.contains("BubbleShortcutAction.MemorizeFarolCard ->"))
    }

    @Test
    fun offline_ocr_recovery_has_no_saved_card_model_dependency() {
        val ai = src("FarolOfflineAiStage642.kt")
        val live = src("LiveRideAccessibilityService.kt")

        assertFalse(ai.contains("FarolCardSignatureModel638"))
        assertFalse(ai.contains("FarolCardVisualHash638"))
        assertFalse(ai.contains("bestVisualSimilarity"))
        assertTrue(live.contains("FarolOfflineAiStage642.recognize("))
        assertTrue(live.contains("scheduleOfflineAiAdmission642(resolvedPackage, \"semantic_card_evidence_miss_643\")"))
        assertTrue(live.contains("lastOfflineAiRecoveryAtElapsed642 < 600L"))
    }

    @Test
    fun semantic_guard_still_prevents_random_selected_app_screens_from_painting_decisions() {
        val structured = OcrStructuredText0188(
            text = "Configurações\nRua das Acácias, 500",
            blocks = listOf(
                OcrTextBlock0188("a", "Rua das Acácias, 500", 10, 100, 900, 220),
            ),
        )
        val result = FarolOfflineAiStage642.recognizeFromEvidence(structured, 2340)
        assertFalse(result.recognizedRideCard)
        assertTrue(result.destination != null)
    }

    @Test
    fun release_metadata_is_0643_5934() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("releaseVersionName = \"0.1.643\""))
        assertTrue(gradle.contains("releaseVersionCode = 5_934"))

        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.643\""))
        assertTrue(history.contains("\"build\": 5934"))
        assertTrue(history.contains("remove a assinatura de cards"))
    }
}
