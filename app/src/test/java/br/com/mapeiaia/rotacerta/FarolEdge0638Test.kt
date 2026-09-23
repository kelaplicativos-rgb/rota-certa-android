package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolEdge0638Test {
    private fun root(): File {
        val cwd = File(System.getProperty("user.dir"))
        return if (File(cwd, "app/src/main/java").isDirectory) cwd
        else if (cwd.name == "app" && File(cwd, "src/main/java").isDirectory) cwd.parentFile
        else cwd
    }

    private fun src(name: String): String =
        File(root(), "app/src/main/java/br/com/mapeiaia/rotacerta/" + name).readText()

    private fun node(
        text: String,
        top: Int,
        left: Int,
        className: String = "android.widget.TextView",
        viewId: String = "",
    ) = FailedCardNodeLine0161(
        text = text,
        top = top,
        left = left,
        bottom = top + 52,
        right = left + 360,
        className = className,
        viewId = viewId,
    )

    @Test
    fun foreign_package_is_rejected_before_heavy_pipeline() {
        val decision = FarolPackageEntryGate638.decide(
            eventPackageName = "com.whatsapp",
            rootPackageName = "com.whatsapp",
            selectedPackages = setOf("com.app99.driver"),
            ownPackageName = "br.com.mapeiaia.rotacerta",
            transientOverlay = { false },
        )
        assertFalse(decision.allowHeavyPipeline)

        val live = src("LiveRideAccessibilityService.kt")
        val gate = live.indexOf("val entryGate638 = FarolPackageEntryGate638.decide")
        val stage38 = live.indexOf("S38_ACCESSIBILITY_EVENT_RECEIVED", gate)
        val stage19 = live.indexOf("handleUniversalVisualEventStage19", gate)
        assertTrue(gate >= 0)
        assertTrue(stage38 > gate)
        assertTrue(stage19 > gate)
        val reject = live.substring(gate, stage38)
        assertTrue(reject.contains("stage638ForeignEventsAvoided"))
        assertTrue(reject.contains("return"))
    }

    @Test
    fun transient_overlay_over_selected_driver_never_enters_heavy_pipeline() {
        val decision = FarolPackageEntryGate638.decide(
            eventPackageName = "com.android.systemui",
            rootPackageName = "com.app99.driver",
            selectedPackages = setOf("com.app99.driver"),
            ownPackageName = "br.com.mapeiaia.rotacerta",
            transientOverlay = { it == "com.android.systemui" },
        )
        assertFalse(decision.allowHeavyPipeline)
        assertTrue(decision.authorityPackage == "com.app99.driver")
    }

    @Test
    fun trained_signature_matches_same_layout_with_different_dynamic_values() {
        val packageName = "com.app99.driver"
        val trainingNodes = listOf(
            node("Perfil Essencial", 120, 30, viewId = "profile"),
            node("R$ 42,80", 190, 30, viewId = "fare"),
            node("8 min (2,4 km)", 250, 30, viewId = "eta_pickup"),
            node("Rua das Flores, 123", 315, 30, viewId = "pickup"),
            node("12 min (5,9 km)", 390, 30, viewId = "eta_destination"),
            node("Avenida Brasil, 999", 455, 30, viewId = "destination"),
            node("Aceitar", 540, 30, className = "android.widget.Button", viewId = "accept"),
        )
        val model = FarolCardSignatureCompiler638.newModel(
            packageName = packageName,
            text = trainingNodes.joinToString("\n") { it.text },
            nodes = trainingNodes,
            visualHash = "0123456789abcdef",
            nowMillis = 1_000L,
        )
        val runtimeNodes = listOf(
            node("Perfil Essencial", 120, 30, viewId = "profile"),
            node("R$ 87,10", 190, 30, viewId = "fare"),
            node("6 min (1,1 km)", 250, 30, viewId = "eta_pickup"),
            node("Rua João Ribeiro, 851", 315, 30, viewId = "pickup"),
            node("19 min (13,7 km)", 390, 30, viewId = "eta_destination"),
            node("Avenida Mateo Bei, 2651", 455, 30, viewId = "destination"),
            node("Aceitar", 540, 30, className = "android.widget.Button", viewId = "accept"),
        )
        val match = FarolCardSignatureMatcher638.match(
            packageName = packageName,
            text = runtimeNodes.joinToString("\n") { it.text },
            nodes = runtimeNodes,
            models = listOf(model),
        )
        assertTrue("score=" + match.score + "; reason=" + match.reason, match.matched)
    }

    @Test
    fun unrelated_layout_does_not_match_trained_card() {
        val packageName = "com.app99.driver"
        val modelNodes = listOf(
            node("R$ 42,80", 190, 30, viewId = "fare"),
            node("8 min (2,4 km)", 250, 30, viewId = "eta"),
            node("Rua das Flores, 123", 315, 30, viewId = "destination"),
            node("Aceitar", 540, 30, className = "android.widget.Button", viewId = "accept"),
        )
        val model = FarolCardSignatureCompiler638.newModel(
            packageName, modelNodes.joinToString("\n") { it.text }, modelNodes, null, 1_000L,
        )
        val unrelated = listOf(
            node("Configurações", 50, 500, viewId = "title"),
            node("Conta", 800, 500, viewId = "account"),
            node("Privacidade", 1200, 500, viewId = "privacy"),
            node("Ajuda", 1600, 500, viewId = "help"),
        )
        assertFalse(
            FarolCardSignatureMatcher638.match(
                packageName, unrelated.joinToString("\n") { it.text }, unrelated, listOf(model),
            ).matched,
        )
    }

    @Test
    fun manual_training_is_the_only_signature_screenshot_path() {
        val live = src("LiveRideAccessibilityService.kt")
        val trainStart = live.indexOf("private fun memorizeFarolCard638")
        val printStart = live.indexOf("private fun saveScreenPrintStage32", trainStart)
        assertTrue(trainStart >= 0 && printStart > trainStart)
        val train = live.substring(trainStart, printStart)
        assertTrue(train.contains("takeScreenshot("))
        assertTrue(train.contains("S638_CARD_SIGNATURE_TRAINED"))

        val matcher = src("FarolCardSignatureCompiler638.kt")
        assertFalse(matcher.contains("takeScreenshot("))
        assertFalse(matcher.contains("OcrService"))
    }

    @Test
    fun bubble_catalog_and_upgrade_migration_expose_memorize_card() {
        val spec = BubbleShortcutCatalog.findSpec("farol_card_training")
        assertTrue(spec?.action == BubbleShortcutAction.MemorizeFarolCard)
        assertTrue(spec?.emoji == "🧠")
        val grid = src("ShortcutGridCustomization0179.kt")
        assertTrue(grid.contains("applyStage638SignatureShortcutMigration"))
        assertTrue(grid.contains("farol_card_training"))
        assertTrue(grid.contains("shortcutId == \"print\""))
    }

    @Test
    fun scheduled_analysis_is_gated_before_heavy_collection() {
        val live = src("LiveRideAccessibilityService.kt")
        val scheduled = live.indexOf("private fun scheduleVisibleTextAnalysis")
        val gate = live.indexOf("stage638ScheduledForeignAvoided", scheduled)
        val heavy = live.indexOf("heavyCollectionsStarted", scheduled)
        assertTrue(scheduled >= 0)
        assertTrue(gate > scheduled)
        assertTrue(heavy > gate)
    }

    @Test
    fun release_metadata_0638_5929_remains_in_history() {
        val history = File(root(), "app/src/main/assets/release_history.json").readText()
        assertTrue(history.contains("\"version\": \"0.1.638\""))
        assertTrue(history.contains("\"build\": 5929"))
    }
}
