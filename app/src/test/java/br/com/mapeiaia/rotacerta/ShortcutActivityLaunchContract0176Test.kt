package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutActivityLaunchContract0176Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    private fun method(name: String, nextName: String): String = service.substringAfter("private fun $name")
        .substringBefore("private fun $nextName")

    @Test
    fun visibleOverlayIsPreservedUntilTheUserInitiatedDispatchIsSent() {
        val helper = method("launchShortcutActivity0176", "executeShortcutModule")
        assertTrue(helper.contains("setPendingIntentCreatorBackgroundActivityStartMode"))
        assertTrue(helper.contains("setPendingIntentBackgroundActivityStartMode"))
        assertTrue(helper.contains("pendingIntent0176.send"))
        assertTrue(helper.indexOf("pendingIntent0176.send") < helper.indexOf("shortcutOverlayController.hideAll()"))
        assertTrue(helper.contains("SHORTCUT_ACTIVITY_DISPATCHED_0176") || helper.contains("DISPATCHED_STAGE"))
        assertTrue(helper.contains("SHORTCUT_ACTIVITY_DISPATCH_FAILED_0176") || helper.contains("FAILED_STAGE"))
    }

    @Test
    fun everyInternalSingleTapDestinationUsesTheSafeLauncher() {
        val methods = listOf(
            "openAuthorizedAppsAndCards146" to "captureCurrentAppAndScreen138",
            "openQuickLinks0172" to "openMessageTemplates0172",
            "openMessageTemplates0172" to "openPrimaryQuickLink0172",
            "openFinance159" to "copyTripConfirmationFromBubbleChecklist8",
            "openQuickRepliesFromBubble" to "exportDiagnosticFromBubble",
            "exportDiagnosticFromBubble" to "toggleLiveReadingFromBubble",
            "openResourceGroup" to "showImportedRadarPopup",
        )
        methods.forEach { (name, next) ->
            val body = method(name, next)
            assertTrue("$name must use the safe launcher", body.contains("launchShortcutActivity0176"))
            assertFalse("$name must not remove the visible overlay before dispatch", body.substringBefore("launchShortcutActivity0176").contains("hideAll()"))
            assertFalse("$name must not call startActivity directly", body.contains("startActivity("))
        }
    }

    @Test
    fun menuStillDispatchesTheResolvedPrimaryActionExactlyOnce() {
        val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
        assertTrue(overlay.contains("bubble.shortcut.clicked entry="))
        assertTrue(overlay.contains("onShortcut(entry0179)"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
    }
}
