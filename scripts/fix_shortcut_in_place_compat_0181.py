from pathlib import Path

ROOT = Path.cwd().resolve()
service = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
text = service.read_text(encoding="utf-8")

replacements = [
    (
        "    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {\n",
        "    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType, defaultName: String = \"\") {\n",
    ),
    (
        "            showSavePlacePopup(coordinate, resolved.addressLine, type)\n",
        "            showSavePlacePopup(coordinate, resolved.addressLine, type, defaultName)\n",
    ),
    (
        "        type: SavedPlaceType,\n    ) {\n",
        "        type: SavedPlaceType,\n        initialName: String = \"\",\n    ) {\n",
    ),
    (
        "        val nameInput = EditText(this).apply {\n            hint = \"Nome\"\n",
        "        val nameInput = EditText(this).apply {\n            hint = \"Nome\"\n            setText(initialName)\n",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"LiveRideAccessibilityService.kt: expected one compatibility anchor, found {count}: {old!r}")
    text = text.replace(old, new, 1)
service.write_text(text, encoding="utf-8")

backslash = chr(92)
test_repairs = {
    "app/src/test/java/br/com/mapeiaia/rotacerta/SavedPlacePopupContract0181Test.kt": [
        (
            'assertTrue(service.contains("text = "Cancelar""))',
            'assertTrue(service.contains("text = ' + backslash + '"Cancelar' + backslash + '""))',
        ),
        (
            'assertTrue(service.contains("text = "Salvar""))',
            'assertTrue(service.contains("text = ' + backslash + '"Salvar' + backslash + '""))',
        ),
    ],
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutInPlaceContract0181Test.kt": [
        (
            'assertTrue(service.contains("text = if (isAlert) "Nome do alerta" else "Nome do local""))',
            'assertTrue(service.contains("text = if (isAlert) '
            + backslash
            + '"Nome do alerta'
            + backslash
            + '" else '
            + backslash
            + '"Nome do local'
            + backslash
            + '""))',
        ),
    ],
}

for relative, repairs in test_repairs.items():
    path = ROOT / relative
    test = path.read_text(encoding="utf-8")
    test = test.replace(
        "showSavePlacePopup(coordinate, resolved.addressLine, type)",
        "showSavePlacePopup(coordinate, resolved.addressLine, type, defaultName)",
    )
    for old, new in repairs:
        count = test.count(old)
        if count != 1:
            raise RuntimeError(f"{relative}: expected one generated Kotlin literal to repair, found {count}: {old!r}")
        test = test.replace(old, new, 1)
    path.write_text(test, encoding="utf-8")
