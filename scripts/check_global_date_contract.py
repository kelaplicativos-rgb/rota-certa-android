#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

android_picker = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/ui/RotaCertaDatePicker.kt"
android_contract = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/date/RotaCertaDateContract.kt"
public_search = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchUi.kt"
trip_editor = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
web_contract = ROOT / "trip-platform/public/date-selection.js"
web_app = ROOT / "trip-platform/public/app.js"
web_html = ROOT / "trip-platform/public/index.html"

required = {
    android_contract: [
        "enum class RotaCertaDateSelectionMode",
        "SINGLE",
        "MULTIPLE",
        "RANGE",
        "MONTH",
        "val isoDateKeys: List<String>",
    ],
    android_picker: [
        "import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelection",
        "RotaCertaDatePickerDialog",
    ],
    public_search: [
        "import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelection",
        "dateSelection.isoDateKeys",
        'label = "Datas da consulta"',
    ],
    trip_editor: [
        "import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelection",
        'label = "Data da saída"',
        "allowedModes = setOf(RotaCertaDateSelectionMode.SINGLE)",
    ],
    web_contract: [
        "RotaCertaDateContract",
        'SINGLE: "SINGLE"',
        'MULTIPLE: "MULTIPLE"',
        'RANGE: "RANGE"',
        'MONTH: "MONTH"',
    ],
    web_app: [
        "const DateContract = window.RotaCertaDateContract",
        "DateContract.todayKey()",
        "DateContract.isBefore(",
    ],
    web_html: [
        '<script src="/date-selection.js" defer></script>',
        '<script src="/app.js" defer></script>',
    ],
}

for path, markers in required.items():
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"global date contract missing: {path.relative_to(ROOT)} :: {marker}")

for path, forbidden in {
    public_search: [
        'label = { Text("Data ou mês") }',
        "AAAA-MM-DD para um dia",
    ],
    trip_editor: [
        "Saída — dd/MM/aaaa HH:mm",
    ],
    web_html: [
        'type="date"',
    ],
}.items():
    text = path.read_text(encoding="utf-8")
    for marker in forbidden:
        if marker in text:
            raise SystemExit(f"parallel/manual date input found: {path.relative_to(ROOT)} :: {marker}")

print("Global date contract OK: Android + Agenda Pública Web")
