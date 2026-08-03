from pathlib import Path

parts = [
    Path(__file__).with_name(f"fix_per_shortcut_menu_0180.py.part{index:02d}")
    for index in range(6)
]
missing = [str(path) for path in parts if not path.is_file()]
if missing:
    raise FileNotFoundError(f"Missing 0.1.180 transformation parts: {missing}")
source = "".join(path.read_text(encoding="utf-8") for path in parts)

# The transformer is stored in the patches checkout, while the materialized
# Android source is the current working directory. Never edit the patches tree.
root_from_script = "ROOT = Path(__file__).resolve().parents[1]"
if source.count(root_from_script) != 1:
    raise RuntimeError("Unexpected 0.1.180 transformer root structure")
source = source.replace(root_from_script, "ROOT = Path.cwd().resolve()", 1)

# The cumulative 0.1.179 source may expose the two overlay callbacks either
# with BubbleShortcutSpec or with the already-resolved entry type. Keep the
# transformation strict about names/count while accepting both safe forms.
source = source.replace("from pathlib import Path\n", "from pathlib import Path\nimport re\n", 1)
old_helper = '''def replace_all_exact(relative: str, old: str, new: str, expected: int) -> None:
    text = read(relative)
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{relative}: expected {expected} occurrences, found {count}: {old[:140]!r}")
    write(relative, text.replace(old, new))
'''
new_helper = '''def replace_all_exact(relative: str, old: str, new: str, expected: int) -> None:
    text = read(relative)
    count = text.count(old)
    if count == 0 and "onShortcut:" in old and "onShortcutLongPress:" in old:
        callback_pair = re.compile(
            r"(?m)^        onShortcut: \\([^\\n]+\\) -> Unit,\\n"
            r"        onShortcutLongPress: \\([^\\n]+\\) -> Unit,"
        )
        updated, regex_count = callback_pair.subn(new, text)
        if regex_count == expected:
            write(relative, updated)
            return
        count = regex_count
    if count != expected:
        raise RuntimeError(f"{relative}: expected {expected} occurrences, found {count}: {old[:140]!r}")
    write(relative, text.replace(old, new))
'''
if source.count(old_helper) != 1:
    raise RuntimeError("Unexpected 0.1.180 transformer helper structure")
source = source.replace(old_helper, new_helper, 1)

# Gradle formatting may insert comments or blank lines between the two fields.
# Require both exact old values independently instead of relying on adjacency.
old_version_block = '''replace_once(
    "app/build.gradle.kts",
    '        versionCode = 5400\\n        versionName = "0.1.179"',
    '        versionCode = 5410\\n        versionName = "0.1.180"',
)
'''
new_version_block = '''replace_once(
    "app/build.gradle.kts",
    "versionCode = 5400",
    "versionCode = 5410",
)
replace_once(
    "app/build.gradle.kts",
    'versionName = "0.1.179"',
    'versionName = "0.1.180"',
)
'''
if source.count(old_version_block) != 1:
    raise RuntimeError("Unexpected 0.1.180 version transform structure")
source = source.replace(old_version_block, new_version_block, 1)

exec(compile(source, str(parts[0]), "exec"), globals(), globals())

# The individual editor now opens with three medium-speed taps instead of a
# five-second hold. Quick tap remains configurable, while a 1.5-second hold is
# still decided on release. The delayed finalizer prevents the first two taps
# of the triple gesture from executing the configured quick action.
customization = "app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt"
replace_once(
    customization,
    "const val SHORTCUT_CUSTOMIZATION_HOLD_MILLIS: Long = 5_000L",
    "const val SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS: Long = 900L",
)

overlay = "app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt"
replace_once(
    overlay,
    "${spec.label}. Toque rápido e toque de um segundo e meio são configuráveis; mantenha por cinco segundos para configurar esta bolinha.",
    "${spec.label}. Toque rápido e toque de um segundo e meio são configuráveis; toque três vezes para configurar esta bolinha.",
)
replace_once(
    overlay,
    'trace("bubble.shortcut.customize.five_seconds")',
    'trace("bubble.shortcut.customize.triple_tap")',
)

overlay_text = read(overlay)
touch_start = """        var downX = 0f
        var downY = 0f
        var downEventTime = 0L
"""
touch_end = """                else -> true
            }
        }"""
if overlay_text.count(touch_start) != 1:
    raise RuntimeError("BubbleShortcutOverlayController.kt: unexpected 0.1.180 touch start")
start_index = overlay_text.index(touch_start)
end_index = overlay_text.find(touch_end, start_index)
if end_index < 0:
    raise RuntimeError("BubbleShortcutOverlayController.kt: unexpected 0.1.180 touch end")
end_index += len(touch_end)
new_touch = """        var downX = 0f
        var downY = 0f
        var downEventTime = 0L
        var movedOutsideGesture = false
        var tapCount0180 = 0
        var firstTapUpTime0180 = 0L
        val finalizeQuickTap0180 = Runnable {
            if (tapCount0180 in 1..2) {
                tapCount0180 = 0
                firstTapUpTime0180 = 0L
                singleAction()
            }
        }
        fun resetTapSequence0180() {
            handler.removeCallbacks(finalizeQuickTap0180)
            tapCount0180 = 0
            firstTapUpTime0180 = 0L
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downEventTime = event.eventTime
                    movedOutsideGesture = false
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moved = kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop
                    if (moved) {
                        movedOutsideGesture = true
                        view.isPressed = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (!movedOutsideGesture) {
                        val heldMillis = (event.eventTime - downEventTime).coerceAtLeast(0L)
                        if (heldMillis >= ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS) {
                            resetTapSequence0180()
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            longAction()
                        } else {
                            val withinTripleWindow = tapCount0180 > 0 &&
                                event.eventTime - firstTapUpTime0180 <=
                                ShortcutGesturePolicy0179.SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS
                            if (!withinTripleWindow) {
                                resetTapSequence0180()
                                tapCount0180 = 1
                                firstTapUpTime0180 = event.eventTime
                                handler.postDelayed(
                                    finalizeQuickTap0180,
                                    ShortcutGesturePolicy0179.SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS,
                                )
                            } else {
                                tapCount0180 += 1
                                if (tapCount0180 >= 3) {
                                    resetTapSequence0180()
                                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    customizeAction()
                                }
                            }
                        }
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
        }"""
write(overlay, overlay_text[:start_index] + new_touch + overlay_text[end_index:])

service = "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(
    service,
    '"SHORTCUT_HOLD_OPEN_EDITOR_0180"',
    '"SHORTCUT_TRIPLE_TAP_OPEN_EDITOR_0180"',
)

contract0179 = "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomizationContract0179Test.kt"
contract0179_text = read(contract0179)
contract0179_text = contract0179_text.replace(
    "quickHoldAndFiveSecondEditorAreSeparateDeterministicActions",
    "quickHoldAndTripleTapEditorAreSeparateDeterministicActions",
)
contract0179_text = contract0179_text.replace(
    "SHORTCUT_CUSTOMIZATION_HOLD_MILLIS",
    "SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS",
)
write(contract0179, contract0179_text)

unit0180 = "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenu0180Test.kt"
replace_once(
    unit0180,
    """    fun thresholdsMatchUserContract() {
        assertEquals(1_500L, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)
        assertEquals(5_000L, ShortcutGesturePolicy0179.SHORTCUT_CUSTOMIZATION_HOLD_MILLIS)
        assertTrue(
            ShortcutGesturePolicy0179.SHORTCUT_CUSTOMIZATION_HOLD_MILLIS >
                ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS,
        )
    }""",
    """    fun thresholdsMatchUserContract() {
        assertEquals(1_500L, ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS)
        assertEquals(900L, ShortcutGesturePolicy0179.SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS)
        assertTrue(
            ShortcutGesturePolicy0179.SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS <
                ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS,
        )
    }""",
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenuContract0180Test.kt",
    """package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPerEntryMenuContract0180Test {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val main = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
    private val policy = File("src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt").readText()

    @Test
    fun tripleTapDoesNotExecuteQuickOrHoldBeforeOpeningEditor() {
        assertTrue(overlay.contains("tapCount0180"))
        assertTrue(overlay.contains("finalizeQuickTap0180"))
        assertTrue(overlay.contains("SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS"))
        assertTrue(overlay.contains("if (tapCount0180 >= 3)"))
        assertTrue(overlay.contains("customizeAction()"))
        assertFalse(overlay.contains("SHORTCUT_CUSTOMIZATION_HOLD_MILLIS"))
    }

    @Test
    fun holdIsStillDecidedOnlyOnRelease() {
        assertTrue(overlay.contains("val heldMillis"))
        assertTrue(overlay.contains("heldMillis >= ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS"))
        assertFalse(overlay.contains("postDelayed(longPressAction"))
    }

    @Test
    fun overlaySendsTheExactEntryForAllThreeGestures() {
        assertTrue(overlay.contains("onShortcut(entry0179)"))
        assertTrue(overlay.contains("onShortcutLongPress(entry0179)"))
        assertTrue(overlay.contains("onShortcutCustomize(entry0179)"))
        assertFalse(overlay.contains("onShortcutLongPress(entry0179.spec)"))
    }

    @Test
    fun individualMenuContainsRequestedControlsAndChoices() {
        assertTrue(main.contains("Toque rápido:"))
        assertTrue(main.contains("Segurar 1,5 s:"))
        assertTrue(main.contains("Não fazer nada"))
        assertTrue(main.contains("Excluir da grade"))
        assertTrue(main.contains("ShortcutGestureAction0180.values()"))
        assertTrue(policy.contains("Executar ação imediatamente"))
        assertTrue(policy.contains("Abrir módulo"))
    }

    @Test
    fun tripleTapOpensOnlyTheSelectedEntryEditor() {
        assertTrue(service.contains("SHORTCUT_TRIPLE_TAP_OPEN_EDITOR_0180"))
        assertTrue(service.contains("openShortcutEntryCustomization0180"))
        assertTrue(service.contains("openShortcutCustomization0179(entry0180.entryId)"))
        assertTrue(service.contains("EXTRA_EDIT_SHORTCUT_ENTRY_ID_0180"))
        assertTrue(main.contains("selectedEntryId0180"))
        assertTrue(main.contains("Configurar bolinha"))
    }
}
""",
)

print("per_shortcut_triple_tap_menu_0_1_180")
