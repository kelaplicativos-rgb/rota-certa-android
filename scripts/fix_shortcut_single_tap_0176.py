#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


def replace_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"0176: expected one match for {label}, found {count}")
    return updated


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    app = root / "app"
    service_path = app / "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
    build_path = app / "build.gradle.kts"
    main_dir = app / "src/main/java/br/com/mapeiaia/rotacerta"
    test_dir = app / "src/test/java/br/com/mapeiaia/rotacerta"

    service = service_path.read_text(encoding="utf-8")
    if "private fun launchShortcutActivity0176(" in service:
        print("0176 already applied")
        return

    service = service.replace(
        "import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback\n",
        "import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback\n"
        "import android.app.ActivityOptions\n"
        "import android.app.PendingIntent\n",
        1,
    )
    service = service.replace(
        "import java.util.concurrent.atomic.AtomicBoolean\n",
        "import java.util.concurrent.atomic.AtomicBoolean\n"
        "import java.util.concurrent.atomic.AtomicInteger\n",
        1,
    )
    service = service.replace(
        "    private val manualCaptureInProgress138 = AtomicBoolean(false)\n",
        "    private val manualCaptureInProgress138 = AtomicBoolean(false)\n"
        "    private val shortcutActivityLaunchRequestCode0176 = AtomicInteger(17_600)\n",
        1,
    )

    helper = r'''    private fun launchShortcutActivity0176(
        shortcutId: String,
        intent: Intent,
        failureMessage: String,
        failureAction: (() -> Unit)? = null,
    ): Boolean {
        val launchIntent0176 = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target0176 = launchIntent0176.component?.className
            ?: launchIntent0176.action
            ?: "unknown"
        val requestCode0176 = ShortcutActivityLaunchPolicy0176.requestCode(
            shortcutActivityLaunchRequestCode0176.incrementAndGet(),
        )
        val result0176 = runCatching {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ShortcutActivityLaunchPolicy0176.usePendingIntent(Build.VERSION.SDK_INT)
            ) {
                val creatorOptions0176 = ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    .toBundle()
                val pendingIntent0176 = PendingIntent.getActivity(
                    this,
                    requestCode0176,
                    launchIntent0176,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    creatorOptions0176,
                )
                val senderOptions0176 = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    .toBundle()
                pendingIntent0176.send(
                    this,
                    0,
                    null,
                    null,
                    null,
                    null,
                    senderOptions0176,
                )
            } else {
                startActivity(launchIntent0176)
            }
        }
        if (result0176.isSuccess) {
            UnifiedDebugEventStore.record(
                ShortcutActivityLaunchPolicy0176.DISPATCHED_STAGE,
                universalResolvedForegroundPackage(),
                "id=$shortcutId; target=$target0176",
            )
            shortcutOverlayController.hideAll()
            persistResourceShortcutState()
            return true
        }
        val error0176 = result0176.exceptionOrNull()
        UnifiedDebugEventStore.record(
            ShortcutActivityLaunchPolicy0176.FAILED_STAGE,
            universalResolvedForegroundPackage(),
            "id=$shortcutId; type=${error0176?.javaClass?.simpleName.orEmpty()}",
        )
        if (failureAction != null) failureAction() else toast(failureMessage)
        return false
    }

'''
    service = service.replace(
        "    private fun executeShortcutModule(spec: BubbleShortcutSpec) {\n",
        helper + "    private fun executeShortcutModule(spec: BubbleShortcutSpec) {\n",
        1,
    )

    service = replace_once(
        service,
        r"    private fun openAuthorizedAppsAndCards146\(\) \{.*?\n    \}\n\n    private fun captureCurrentAppAndScreen138",
        '''    private fun openAuthorizedAppsAndCards146() {
        launchShortcutActivity0176(
            shortcutId = "manual_capture",
            intent = Intent(this, InstalledRideAppPickerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            failureMessage = "Não consegui abrir os aplicativos autorizados.",
        )
    }

    private fun captureCurrentAppAndScreen138''',
        "openAuthorizedAppsAndCards146",
    )

    service = replace_once(
        service,
        r"    private fun openNamedPlaceShortcut138\(type: SavedPlaceType\) \{.*?\n    \}\n\n    private fun openDestinationConfirmationFromBubble138",
        '''    private fun openNamedPlaceShortcut138(type: SavedPlaceType) {
        val group = if (type == SavedPlaceType.ProximityAlert) "alerts" else "saved_places"
        launchShortcutActivity0176(
            shortcutId = "$group.create",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group)
                .putExtra(EXTRA_CREATE_SAVED_PLACE_TYPE_138, type.name),
            failureMessage = "Não consegui abrir o cadastro agora.",
        )
    }

    private fun openDestinationConfirmationFromBubble138''',
        "openNamedPlaceShortcut138",
    )

    service = replace_once(
        service,
        r"    private fun openDestinationConfirmationFromBubble138\(\) \{.*?\n    \}\n\n    private fun copyAllVisibleTextFromBubble138",
        '''    private fun openDestinationConfirmationFromBubble138() {
        launchShortcutActivity0176(
            shortcutId = "destination.confirm",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "destination")
                .putExtra(EXTRA_CONFIRM_DESTINATION_GPS_138, true),
            failureMessage = "Não consegui abrir a confirmação do destino.",
        )
    }

    private fun copyAllVisibleTextFromBubble138''',
        "openDestinationConfirmationFromBubble138",
    )

    service = replace_once(
        service,
        r"    private fun openQuickLinks0172\(\) \{.*?\n    \}\n\n    private fun openMessageTemplates0172",
        '''    private fun openQuickLinks0172() {
        launchShortcutActivity0176(
            shortcutId = "quick_links",
            intent = Intent(this, QuickLinksActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            failureMessage = "Não foi possível abrir os Links rápidos.",
        )
    }

    private fun openMessageTemplates0172''',
        "openQuickLinks0172",
    )

    service = replace_once(
        service,
        r"    private fun openMessageTemplates0172\(\) \{.*?\n    \}\n\n    private fun openPrimaryQuickLink0172",
        '''    private fun openMessageTemplates0172() {
        launchShortcutActivity0176(
            shortcutId = "message_templates",
            intent = Intent(this, MessageTemplatesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            failureMessage = "Não foi possível abrir as frases predefinidas.",
        )
    }

    private fun openPrimaryQuickLink0172''',
        "openMessageTemplates0172",
    )

    service = replace_once(
        service,
        r"    private fun openFinance159\(\) \{.*?\n    \}\n\n    private fun copyTripConfirmationFromBubbleChecklist8",
        '''    private fun openFinance159() {
        launchShortcutActivity0176(
            shortcutId = "finance",
            intent = Intent(this, FinancialActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            failureMessage = "Não foi possível abrir o Financeiro",
            failureAction = {
                shortcutOverlayController.showSilentStatus159("Não foi possível abrir o Financeiro", false)
            },
        )
    }

    private fun copyTripConfirmationFromBubbleChecklist8''',
        "openFinance159",
    )

    service = replace_once(
        service,
        r"    private fun openQuickRepliesFromBubble\(createNew: Boolean = false\) \{.*?\n    \} // open_quick_replies_checklist_3",
        '''    private fun openQuickRepliesFromBubble(createNew: Boolean = false) {
        val targetPackage = listOf(currentRootPackageName(), currentWindowPackageName())
            .firstNotNullOfOrNull { candidate ->
                QuickReplyTargetPolicy.normalize(candidate)
                    ?.takeUnless { normalized -> normalized == packageName }
            }
        if (targetPackage == null) {
            toast("Abra primeiro a conversa onde deseja inserir a resposta.")
            return
        }
        quickReplyTargetPackageNameChecklist3 = targetPackage
        launchShortcutActivity0176(
            shortcutId = "quick_replies",
            intent = Intent(this, QuickRepliesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE, targetPackage)
                .putExtra(EXTRA_QUICK_REPLY_CREATE, createNew)
                .putExtra(EXTRA_QUICK_REPLY_OVERLAY_MODE_0172, true),
            failureMessage = "Não foi possível abrir as respostas rápidas.",
        )
    } // open_quick_replies_checklist_3''',
        "openQuickRepliesFromBubble",
    )

    service = replace_once(
        service,
        r"    private fun exportDiagnosticFromBubble\(\) \{.*?\n    \} // unified_manual_report_from_grid_0_1_142",
        '''    private fun exportDiagnosticFromBubble() {
        UnifiedDebugEventStore.record(
            "BUBBLE_REPORT_SHORTCUT_OPENED",
            universalResolvedForegroundPackage(),
            "grade abriu a area de relatorios; exportacao automatica desativada",
        )
        launchShortcutActivity0176(
            shortcutId = "diagnostic",
            intent = Intent(this@LiveRideAccessibilityService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_HISTORY)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "reports"),
            failureMessage = "Nao foi possivel abrir a area de relatorios.",
        )
    } // unified_manual_report_from_grid_0_1_142''',
        "exportDiagnosticFromBubble",
    )

    service = replace_once(
        service,
        r"    private fun openResourceGroup\(group: String, tab: String\) \{.*?\n    \}\n\n    private fun showImportedRadarPopup",
        '''    private fun openResourceGroup(group: String, tab: String) {
        launchShortcutActivity0176(
            shortcutId = group,
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, tab)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group),
            failureMessage = "Não foi possível abrir o módulo agora.",
        )
    }

    private fun showImportedRadarPopup''',
        "openResourceGroup",
    )

    service = replace_once(
        service,
        r"    private fun openSavedPlaceEditor\(place: SavedPlace\) \{.*?\n    \}\n\n    private fun onMainBubbleClick",
        '''    private fun openSavedPlaceEditor(place: SavedPlace) {
        launchShortcutActivity0176(
            shortcutId = if (place.type == SavedPlaceType.ProximityAlert) "alerts.edit" else "saved_places.edit",
            intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                .putExtra(EXTRA_SAVED_PLACE_ID, place.id)
                .putExtra(EXTRA_OPEN_BUBBLE_GROUP, if (place.type == SavedPlaceType.ProximityAlert) "alerts" else "saved_places"),
            failureMessage = "Não foi possível abrir este local.",
        )
    }

    private fun onMainBubbleClick''',
        "openSavedPlaceEditor",
    )

    service = replace_once(
        service,
        r"    private fun openShortcutModule0171\(spec: BubbleShortcutSpec\) \{.*?\n    \}\n\n    private fun executeShortcutDoubleTap",
        '''    private fun openShortcutModule0171(spec: BubbleShortcutSpec) {
        val intent0171 = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_TAB, spec.targetTab ?: TAB_CONFIG)
            .putExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171, spec.id)
        spec.targetGroup?.let { intent0171.putExtra(EXTRA_OPEN_BUBBLE_GROUP, it) }
        launchShortcutActivity0176(
            shortcutId = spec.id,
            intent = intent0171,
            failureMessage = "Não foi possível abrir o módulo ${spec.displayLabel}.",
        )
    }

    private fun executeShortcutDoubleTap''',
        "openShortcutModule0171",
    )

    service_path.write_text(service, encoding="utf-8")

    build = build_path.read_text(encoding="utf-8")
    if 'versionName = "0.1.175"' not in build or "versionCode = 5360" not in build:
        raise SystemExit("0176: expected 0.1.175 (5360) before version bump")
    build = build.replace("versionCode = 5360", "versionCode = 5370", 1)
    build = build.replace('versionName = "0.1.175"', 'versionName = "0.1.176"', 1)
    build_path.write_text(build, encoding="utf-8")

    policy_path = main_dir / "ShortcutActivityLaunchPolicy0176.kt"
    policy_path.write_text(
        '''package br.com.mapeiaia.rotacerta

object ShortcutActivityLaunchPolicy0176 {
    const val CONTRACT_MARKER = "SHORTCUT_ACTIVITY_LAUNCH_0176"
    const val DISPATCHED_STAGE = "SHORTCUT_ACTIVITY_DISPATCHED_0176"
    const val FAILED_STAGE = "SHORTCUT_ACTIVITY_DISPATCH_FAILED_0176"
    private const val FIRST_REQUEST_CODE = 17_600

    fun usePendingIntent(sdkInt: Int): Boolean = sdkInt >= 34

    fun requestCode(serial: Int): Int {
        val positive = serial and Int.MAX_VALUE
        return if (positive == 0) FIRST_REQUEST_CODE else positive
    }
}
''',
        encoding="utf-8",
    )

    test_dir.mkdir(parents=True, exist_ok=True)
    (test_dir / "ShortcutActivityLaunchPolicy0176Test.kt").write_text(
        '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutActivityLaunchPolicy0176Test {
    @Test
    fun pendingIntentIsRequiredFromAndroid14() {
        assertFalse(ShortcutActivityLaunchPolicy0176.usePendingIntent(33))
        assertTrue(ShortcutActivityLaunchPolicy0176.usePendingIntent(34))
        assertTrue(ShortcutActivityLaunchPolicy0176.usePendingIntent(36))
    }

    @Test
    fun requestCodeStaysPositiveAndNeverUsesZero() {
        assertEquals(17_601, ShortcutActivityLaunchPolicy0176.requestCode(17_601))
        assertEquals(17_600, ShortcutActivityLaunchPolicy0176.requestCode(Int.MIN_VALUE))
        assertEquals(Int.MAX_VALUE, ShortcutActivityLaunchPolicy0176.requestCode(-1))
    }
}
''',
        encoding="utf-8",
    )

    (test_dir / "ShortcutActivityLaunchContract0176Test.kt").write_text(
        '''package br.com.mapeiaia.rotacerta

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
    fun menuStillDispatchesThePrimaryActionExactlyOnce() {
        val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
        assertTrue(overlay.contains("trace(\"bubble.shortcut.clicked id=\${module.spec.id}\")"))
        assertTrue(overlay.contains("onShortcut(module.spec)"))
        assertFalse(overlay.contains("(doubleAction ?: singleAction).invoke()"))
    }
}
''',
        encoding="utf-8",
    )

    print("0176 shortcut activity launch fix applied")


if __name__ == "__main__":
    main()
