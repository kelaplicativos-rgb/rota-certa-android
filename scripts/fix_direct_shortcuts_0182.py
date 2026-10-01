from pathlib import Path

ROOT = Path.cwd().resolve()
MARKER = "direct_shortcuts_two_taps_0_1_182"


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise FileNotFoundError(f"Missing required source file: {relative}")
    return path.read_text(encoding="utf-8")


def write(relative: str, content: str) -> None:
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def replace_once(relative: str, old: str, new: str) -> None:
    text = read(relative)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            f"{relative}: expected exactly one occurrence, found {count}: {old[:180]!r}"
        )
    write(relative, text.replace(old, new, 1))


# Version.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 5420\n        versionName = "0.1.181"',
    '        versionCode = 5430\n        versionName = "0.1.182"',
)

# The grid item itself must be a direct shortcut. Remove the inherited
# 900-millisecond triple-tap decision window and all hold classification from
# the shortcut bubble. The permanent + entry remains the configuration route.
overlay = "app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt"
overlay_text = read(overlay)
function_marker = "    private fun shortcutBubble("
function_start = overlay_text.find(function_marker)
if function_start < 0:
    raise RuntimeError("BubbleShortcutOverlayController.kt: shortcutBubble not found")

start_token = "        var downX = 0f\n        var downY = 0f\n"
touch_start = overlay_text.find(start_token, function_start)
if touch_start < 0:
    raise RuntimeError("BubbleShortcutOverlayController.kt: shortcut touch start not found")
end_token = "                else -> true\n            }\n        }"
touch_end = overlay_text.find(end_token, touch_start)
if touch_end < 0:
    raise RuntimeError("BubbleShortcutOverlayController.kt: shortcut touch end not found")
touch_end += len(end_token)

new_touch = '''        // SHORTCUT_DIRECT_TAP_0182: one release dispatches one action immediately.
        var downX = 0f
        var downY = 0f
        var movedOutsideGesture = false
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    movedOutsideGesture = false
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moved = kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop
                    if (moved) {
                        movedOutsideGesture = true
                        view.isPressed = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (!movedOutsideGesture) {
                        performClick()
                        singleAction()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    movedOutsideGesture = true
                    view.isPressed = false
                    true
                }
                else -> true
            }
        }'''
overlay_text = overlay_text[:touch_start] + new_touch + overlay_text[touch_end:]

function_end = overlay_text.find("\n    }\n", function_start)
if function_end < 0:
    raise RuntimeError("BubbleShortcutOverlayController.kt: shortcutBubble end not found")
segment = overlay_text[function_start:function_end]
lines = segment.splitlines(keepends=True)
content_matches = [i for i, line in enumerate(lines) if "contentDescription =" in line]
if len(content_matches) != 1:
    raise RuntimeError(
        f"BubbleShortcutOverlayController.kt: expected one contentDescription, found {len(content_matches)}"
    )
lines[content_matches[0]] = (
    '        contentDescription = "${spec.label}. Um toque executa imediatamente. " +\n'
    '            "Use a bolinha mais para editar a grade."\n'
)
overlay_text = overlay_text[:function_start] + "".join(lines) + overlay_text[function_end:]
if "tapCount0180" in overlay_text[function_start:function_end]:
    raise RuntimeError("BubbleShortcutOverlayController.kt: triple-tap state survived direct listener")
write(overlay, overlay_text)

# Normalize any persisted legacy quick/hold choice to the one authoritative
# action of a shortcut tap. Existing name, icon, order, visibility and selected
# resource remain untouched.
write(
    "app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutDirectTapPolicy0182.kt",
    '''package br.com.mapeiaia.rotacerta

object ShortcutDirectTapPolicy0182 {
    const val CONTRACT_MARKER = "SHORTCUT_DIRECT_TAP_0182"
    const val MAX_TAPS_FROM_MAIN_BUBBLE_TO_ACTION = 2

    fun actionForTap(
        @Suppress("UNUSED_PARAMETER") persistedAction: ShortcutGestureAction0180,
    ): ShortcutGestureAction0180 = ShortcutGestureAction0180.PRIMARY_ACTION
}
''',
)

service = "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(
    service,
    '''    private fun executeShortcutQuickTap0180(entry0180: ResolvedShortcutGridEntry0179) {
        dispatchShortcutGesture0180(entry0180, entry0180.quickAction0180, "quick")
    }
''',
    '''    private fun executeShortcutQuickTap0180(entry0180: ResolvedShortcutGridEntry0179) {
        dispatchShortcutGesture0180(
            entry0180,
            ShortcutDirectTapPolicy0182.actionForTap(entry0180.quickAction0180),
            "single_tap_direct_0182",
        )
    }
''',
)
replace_once(
    service,
    '''        when (action0180) {
            ShortcutGestureAction0180.PRIMARY_ACTION -> dispatchShortcutPrimaryInPlace0181(entry0180.spec)
            ShortcutGestureAction0180.OPEN_MODULE -> showShortcutModulePopup0181(entry0180.spec)
            ShortcutGestureAction0180.NONE -> Unit
        }
''',
    '''        when (action0180) {
            ShortcutGestureAction0180.PRIMARY_ACTION -> dispatchShortcutPrimaryDirect0182(entry0180.spec)
            ShortcutGestureAction0180.OPEN_MODULE -> openShortcutModule0171(entry0180.spec)
            ShortcutGestureAction0180.NONE -> Unit
        }
''',
)
replace_once(
    service,
    '''    private fun dispatchShortcutPrimaryInPlace0181(spec: BubbleShortcutSpec) {
        when (spec.id) {
            "saved_places" -> saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            "alerts" -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            in ShortcutInPlacePolicy0181.overlayFirstIds -> showShortcutModulePopup0181(spec)
            else -> executeShortcutModule(spec)
        }
    }
''',
    '''    private fun dispatchShortcutPrimaryDirect0182(spec: BubbleShortcutSpec) {
        when (spec.id) {
            "saved_places" -> saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            "alerts" -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            else -> executeShortcutModule(spec)
        }
    }
''',
)

# The central remains the only customization surface. Remove gesture selectors
# from the visible cards so the UI matches the direct-shortcut contract.
main = "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
replace_once(
    main,
    '''            if (selectedEntryId0180 == null) {
                "Personalize nome, ícone e os dois gestos de cada bolinha."
            } else {
                "Esta configuração pertence somente à bolinha pressionada por cinco segundos."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Toque rápido e segurar por 1,5 segundo podem executar a ação, abrir o módulo ou não fazer nada. Segurar por 5 segundos sempre abre esta configuração.",
            style = MaterialTheme.typography.bodySmall,
        )''',
    '''            if (selectedEntryId0180 == null) {
                "Personalize nome, recurso, ícone, ordem e visibilidade de cada atalho."
            } else {
                "Edite esta bolinha sem alterar o funcionamento das demais."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Um toque na bolinha principal abre a grade; o toque seguinte executa o atalho imediatamente. Use a bolinha + para voltar a esta Central.",
            style = MaterialTheme.typography.bodySmall,
        )''',
)
replace_once(
    main,
    '''            OutlinedButton(
                onClick = { editingGesture0180 = ShortcutGestureSlot0180.QUICK_TAP },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Toque rápido: ${entry.quickAction0180.displayLabel}")
            }
            OutlinedButton(
                onClick = { editingGesture0180 = ShortcutGestureSlot0180.HOLD_1500 },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Segurar 1,5 s: ${entry.holdAction0180.displayLabel}")
            }
            TextButton(
                onClick = {
                    onUpdate(
                        entry.copy(
                            quickAction0180 = ShortcutGestureAction0180.NONE,
                            holdAction0180 = ShortcutGestureAction0180.NONE,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Não fazer nada")
            }
''',
    '''            Text(
                "Ação: um toque executa imediatamente",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
''',
)

# Update inherited contracts that intentionally described the superseded
# multi-gesture behavior.
replace_once(
    "app/src/test/java/br/com/mapeiaia/rotacerta/AuthorizedAppsCards146ContractTest.kt",
    'assertTrue(overlay.contains("heldMillis >= ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS"))',
    'assertTrue(overlay.contains("SHORTCUT_DIRECT_TAP_0182"))',
)
replace_once(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomizationContract0179Test.kt",
    '''    fun quickHoldAndFiveSecondEditorAreSeparateDeterministicActions() {
        assertTrue(overlay.contains("SHORTCUT_LONG_PRESS_MILLIS"))
        assertTrue(overlay.contains("SHORTCUT_CUSTOMIZATION_HOLD_MILLIS"))
        assertTrue(overlay.contains("onShortcut(entry0179)"))
        assertTrue(overlay.contains("onShortcutLongPress(entry0179)"))
        assertTrue(service.contains("onShortcut = ::executeShortcutQuickTap0180"))
        assertTrue(service.contains("onShortcutLongPress = ::executeShortcutHold0180"))
        assertTrue(service.contains("onShortcutCustomize = ::openShortcutEntryCustomization0180"))
        assertFalse(service.contains("private fun executeShortcutLongPress0179"))
    }''',
    '''    fun singleTapDispatchesTheSelectedShortcutWithoutGestureDelay() {
        assertTrue(overlay.contains("SHORTCUT_DIRECT_TAP_0182"))
        assertTrue(overlay.contains("singleAction()"))
        assertFalse(overlay.contains("tapCount0180"))
        assertFalse(overlay.contains("SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS"))
        assertTrue(service.contains("onShortcut = ::executeShortcutQuickTap0180"))
        assertTrue(service.contains("ShortcutDirectTapPolicy0182.actionForTap"))
    }''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenuContract0180Test.kt",
    '''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPerEntryMenuContract0180Test {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()

    @Test
    fun shortcutTapHasNoTripleTapWindowOrHoldClassification() {
        assertTrue(overlay.contains("SHORTCUT_DIRECT_TAP_0182"))
        assertTrue(overlay.contains("singleAction()"))
        assertFalse(overlay.contains("tapCount0180"))
        assertFalse(overlay.contains("SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS"))
        assertFalse(overlay.contains("heldMillis >= ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS"))
    }

    @Test
    fun centralPlusReplacesGestureBasedEditing() {
        assertTrue(overlay.contains("shortcut_add_0179"))
        assertTrue(main.contains("Use a bolinha + para voltar a esta Central."))
        assertTrue(main.contains("Ação: um toque executa imediatamente"))
        assertFalse(main.contains("Text(\"Toque rápido:"))
        assertFalse(main.contains("Text(\"Segurar 1,5 s:"))
    }

    @Test
    fun persistedLegacyGestureCannotDisableTheDirectTap() {
        assertTrue(service.contains("ShortcutDirectTapPolicy0182.actionForTap(entry0180.quickAction0180)"))
        assertTrue(service.contains("single_tap_direct_0182"))
    }
}
''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutInPlaceContract0181Test.kt",
    '''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInPlaceContract0181Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun savedPlaceAndAlertOpenTheirRealActionPopupDirectly() {
        assertTrue(service.contains("dispatchShortcutPrimaryDirect0182"))
        assertTrue(service.contains("saveCurrentPlaceFromBubble(SavedPlaceType.Place)"))
        assertTrue(service.contains("saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)"))
    }

    @Test
    fun otherPrimaryShortcutsDoNotStopAtTheGenericInformationPopup() {
        assertTrue(service.contains("else -> executeShortcutModule(spec)"))
        assertFalse(
            service.contains(
                "in ShortcutInPlacePolicy0181.overlayFirstIds -> showShortcutModulePopup0181(spec)",
            ),
        )
        assertFalse(service.contains("OPEN_MODULE -> showShortcutModulePopup0181(entry0180.spec)"))
    }
}
''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutDirectTapPolicy0182Test.kt",
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutDirectTapPolicy0182Test {
    @Test
    fun allLegacyGestureValuesResolveToThePrimaryShortcutAction() {
        ShortcutGestureAction0180.values().forEach { persisted ->
            assertEquals(
                ShortcutGestureAction0180.PRIMARY_ACTION,
                ShortcutDirectTapPolicy0182.actionForTap(persisted),
            )
        }
    }

    @Test
    fun actionIsReachedWithAtMostTwoTapsFromTheMainBubble() {
        assertEquals(2, ShortcutDirectTapPolicy0182.MAX_TAPS_FROM_MAIN_BUBBLE_TO_ACTION)
    }
}
''',
)

# Marker in a deterministic source file for artifact verification.
policy_path = "app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutDirectTapPolicy0182.kt"
policy_text = read(policy_path)
if MARKER not in policy_text:
    write(policy_path, policy_text + f"\n// {MARKER}\n")

print(MARKER)
