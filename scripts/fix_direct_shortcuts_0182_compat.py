from pathlib import Path

transform_path = Path(__file__).with_name("fix_direct_shortcuts_0182.py")
source = transform_path.read_text(encoding="utf-8")

old = '''    fun quickHoldAndFiveSecondEditorAreSeparateDeterministicActions() {
        assertTrue(overlay.contains("SHORTCUT_LONG_PRESS_MILLIS"))
        assertTrue(overlay.contains("SHORTCUT_CUSTOMIZATION_HOLD_MILLIS"))
'''
new = '''    fun quickHoldAndTripleTapEditorAreSeparateDeterministicActions() {
        assertTrue(overlay.contains("SHORTCUT_LONG_PRESS_MILLIS"))
        assertTrue(overlay.contains("SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS"))
'''

if source.count(old) != 1:
    raise RuntimeError("Unexpected 0.1.182 compatibility anchor")
source = source.replace(old, new, 1)
exec(compile(source, str(transform_path), "exec"), globals(), globals())
