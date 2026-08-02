#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(f"0177: expected exactly one literal for {label}, found {text.count(old)}")
    return text.replace(old, new, 1)


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    app = root / "app"
    main_dir = app / "src/main/java/br/com/mapeiaia/rotacerta"
    test_dir = app / "src/test/java/br/com/mapeiaia/rotacerta"
    service_path = main_dir / "LiveRideAccessibilityService.kt"
    activity_path = main_dir / "MainActivity.kt"
    build_path = app / "build.gradle.kts"

    service = service_path.read_text(encoding="utf-8")
    activity = activity_path.read_text(encoding="utf-8")
    build = build_path.read_text(encoding="utf-8")

    if "SHORTCUT_MODULE_IDENTITY_FOCUS_0177" in activity:
        print("0177 already applied")
        return

    old_dispatch = '''            BubbleShortcutAction.OpenSettings,
            -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))
'''
    new_dispatch = '''            BubbleShortcutAction.OpenSettings,
            -> openShortcutModule0171(spec) // shortcut_module_identity_focus_0177
'''
    service = replace_once(service, old_dispatch, new_dispatch, "resource module dispatch")

    activity = replace_once(
        activity,
        "import androidx.compose.foundation.verticalScroll\n",
        "import androidx.compose.foundation.verticalScroll\n"
        "import androidx.compose.foundation.relocation.BringIntoViewRequester\n"
        "import androidx.compose.foundation.relocation.bringIntoViewRequester\n",
        "bring into view imports",
    )
    activity = replace_once(
        activity,
        "import androidx.compose.runtime.setValue\n",
        "import androidx.compose.runtime.setValue\n"
        "import androidx.compose.runtime.withFrameNanos\n",
        "frame import",
    )

    old_call = '''                    ShortcutModulesHome0171(
                        expandedModuleId = highlightedShortcutModule0171,
                        onToggleModule = { spec ->
'''
    new_call = '''                    ShortcutModulesHome0171(
                        expandedModuleId = highlightedShortcutModule0171,
                        navigationRequestKey0177 = System.identityHashCode(launchIntent),
                        onToggleModule = { spec ->
'''
    activity = replace_once(activity, old_call, new_call, "home module call")

    old_signature_and_rows = '''private fun ShortcutModulesHome0171(
    expandedModuleId: String?,
    onToggleModule: (BubbleShortcutSpec) -> Unit,
    moduleContent: @Composable (BubbleShortcutSpec) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        check(HomeModuleBubbleGridPolicy0175.CONTRACT_MARKER.isNotBlank())
        ShortcutGridPolicy0173.clearLegacyPreferences(context)
    }
    val moduleRows = remember {
        HomeModuleBubbleGridPolicy0175.rows(BubbleShortcutCatalog.modules)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
'''
    new_signature_and_rows = '''private fun ShortcutModulesHome0171(
    expandedModuleId: String?,
    navigationRequestKey0177: Int,
    onToggleModule: (BubbleShortcutSpec) -> Unit,
    moduleContent: @Composable (BubbleShortcutSpec) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        check(HomeModuleBubbleGridPolicy0175.CONTRACT_MARKER.isNotBlank())
        check(ShortcutModuleFocusPolicy0177.CONTRACT_MARKER.isNotBlank())
        ShortcutGridPolicy0173.clearLegacyPreferences(context)
    }
    val moduleRows = remember {
        HomeModuleBubbleGridPolicy0175.rows(BubbleShortcutCatalog.modules)
    }
    val moduleFocusRequesters0177 = remember {
        BubbleShortcutCatalog.modules.associate { module ->
            module.spec.id to BringIntoViewRequester()
        }
    }
    LaunchedEffect(expandedModuleId, navigationRequestKey0177) {
        val requestedModuleId0177 = expandedModuleId ?: return@LaunchedEffect
        withFrameNanos { }
        moduleFocusRequesters0177[requestedModuleId0177]?.bringIntoView()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
'''
    activity = replace_once(activity, old_signature_and_rows, new_signature_and_rows, "home module focus setup")

    old_loop = '''        moduleRows.forEach { rowModules ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowModules.forEach { module ->
                    HomeModuleBubble0175(
                        spec = module.spec,
                        selected = HomeModuleExpansionPolicy0174.isExpanded(expandedModuleId, module.spec.id),
                        onClick = { onToggleModule(module.spec) },
                    )
                }
            }
            val expandedIdForRow = HomeModuleBubbleGridPolicy0175.expandedIdInRow(
                rowIds = rowModules.map { it.spec.id },
                expandedId = expandedModuleId,
            )
            val expandedModule = rowModules.firstOrNull { it.spec.id == expandedIdForRow }
            if (expandedModule != null) {
                HomeModuleInlinePanel0175(
                    spec = expandedModule.spec,
                    content = { moduleContent(expandedModule.spec) },
                )
            }
        }
'''
    new_loop = '''        moduleRows.forEach { rowModules ->
            val expandedIdForRow = HomeModuleBubbleGridPolicy0175.expandedIdInRow(
                rowIds = rowModules.map { it.spec.id },
                expandedId = expandedModuleId,
            )
            val expandedModule = rowModules.firstOrNull { it.spec.id == expandedIdForRow }
            val rowFocusRequester0177 = expandedIdForRow?.let(moduleFocusRequesters0177::get)
            Column(
                modifier = if (rowFocusRequester0177 != null) {
                    Modifier.bringIntoViewRequester(rowFocusRequester0177)
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowModules.forEach { module ->
                        HomeModuleBubble0175(
                            spec = module.spec,
                            selected = HomeModuleExpansionPolicy0174.isExpanded(expandedModuleId, module.spec.id),
                            onClick = { onToggleModule(module.spec) },
                        )
                    }
                }
                if (expandedModule != null) {
                    HomeModuleInlinePanel0175(
                        spec = expandedModule.spec,
                        content = { moduleContent(expandedModule.spec) },
                    )
                }
            }
        }
'''
    activity = replace_once(activity, old_loop, new_loop, "home module row focus")
    activity += "\n// SHORTCUT_MODULE_IDENTITY_FOCUS_0177\n"

    if 'versionName = "0.1.176"' not in build or "versionCode = 5370" not in build:
        raise SystemExit("0177: expected 0.1.176 (5370) before version bump")
    build = build.replace("versionCode = 5370", "versionCode = 5380", 1)
    build = build.replace('versionName = "0.1.176"', 'versionName = "0.1.177"', 1)

    (main_dir / "ShortcutModuleFocusPolicy0177.kt").write_text(
        '''package br.com.mapeiaia.rotacerta

object ShortcutModuleFocusPolicy0177 {
    const val CONTRACT_MARKER = "SHORTCUT_MODULE_IDENTITY_FOCUS_0177"

    fun routesByModuleIdentity(action: BubbleShortcutAction): Boolean = when (action) {
        BubbleShortcutAction.OpenRoute,
        BubbleShortcutAction.OpenDestination,
        BubbleShortcutAction.OpenAlerts,
        BubbleShortcutAction.OpenSavedPlaces,
        BubbleShortcutAction.OpenRadars,
        BubbleShortcutAction.OpenAppearance,
        BubbleShortcutAction.OpenPermissions,
        BubbleShortcutAction.OpenBackup,
        BubbleShortcutAction.OpenReports,
        BubbleShortcutAction.OpenSettings,
        -> true
        else -> false
    }
}
''',
        encoding="utf-8",
    )

    test_dir.mkdir(parents=True, exist_ok=True)
    (test_dir / "ShortcutModuleFocusPolicy0177Test.kt").write_text(
        '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutModuleFocusPolicy0177Test {
    @Test
    fun inlineHomeModulesRouteByTheirOwnIdentity() {
        val routed = listOf(
            BubbleShortcutAction.OpenRoute,
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenAlerts,
            BubbleShortcutAction.OpenSavedPlaces,
            BubbleShortcutAction.OpenRadars,
            BubbleShortcutAction.OpenAppearance,
            BubbleShortcutAction.OpenPermissions,
            BubbleShortcutAction.OpenBackup,
            BubbleShortcutAction.OpenReports,
            BubbleShortcutAction.OpenSettings,
        )
        routed.forEach { action ->
            assertTrue(action.name, ShortcutModuleFocusPolicy0177.routesByModuleIdentity(action))
        }
        assertFalse(ShortcutModuleFocusPolicy0177.routesByModuleIdentity(BubbleShortcutAction.OpenFinance))
        assertFalse(ShortcutModuleFocusPolicy0177.routesByModuleIdentity(BubbleShortcutAction.ClearClipboard))
    }
}
''',
        encoding="utf-8",
    )
    (test_dir / "ShortcutModuleFocusContract0177Test.kt").write_text(
        '''package br.com.mapeiaia.rotacerta

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutModuleFocusContract0177Test {
    private fun source(path: String): String = Files.readString(Paths.get(path))

    @Test
    fun floatingGridSendsModuleIdentityAndHomeBringsPanelIntoView() {
        val service = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        val activity = source("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
        assertTrue(service.contains("openShortcutModule0171(spec) // shortcut_module_identity_focus_0177"))
        assertFalse(service.contains("openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))"))
        assertTrue(activity.contains("navigationRequestKey0177 = System.identityHashCode(launchIntent)"))
        assertTrue(activity.contains("BringIntoViewRequester()"))
        assertTrue(activity.contains("Modifier.bringIntoViewRequester(rowFocusRequester0177)"))
        assertTrue(activity.contains("moduleFocusRequesters0177[requestedModuleId0177]?.bringIntoView()"))
        assertTrue(activity.contains("SHORTCUT_MODULE_IDENTITY_FOCUS_0177"))
    }
}
''',
        encoding="utf-8",
    )

    service_path.write_text(service, encoding="utf-8")
    activity_path.write_text(activity, encoding="utf-8")
    build_path.write_text(build, encoding="utf-8")
    print("0177 shortcut module identity and focus fix applied")


if __name__ == "__main__":
    main()
