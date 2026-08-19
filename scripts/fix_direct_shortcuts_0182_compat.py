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

# The original generator used escaped quotes inside a Python triple-quoted
# Kotlin source block. Python consumed those escapes and produced invalid
# nested Kotlin string literals. Keep the same assertions without nesting a
# quoted Compose call inside the searched text.
contract_path = Path.cwd() / (
    "app/src/test/java/br/com/mapeiaia/rotacerta/"
    "ShortcutPerEntryMenuContract0180Test.kt"
)
contract = contract_path.read_text(encoding="utf-8")
replacements = {
    '        assertFalse(main.contains("Text("Toque rápido:"))\n':
        '        assertFalse(main.contains("Toque rápido:"))\n',
    '        assertFalse(main.contains("Text("Segurar 1,5 s:"))\n':
        '        assertFalse(main.contains("Segurar 1,5 s:"))\n',
}
for invalid, valid in replacements.items():
    if contract.count(invalid) != 1:
        raise RuntimeError(f"Unexpected generated Kotlin quote anchor: {invalid!r}")
    contract = contract.replace(invalid, valid, 1)
contract_path.write_text(contract, encoding="utf-8")
