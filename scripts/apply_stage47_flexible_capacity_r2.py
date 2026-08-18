#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()
PATCHES = Path(sys.argv[2]).resolve()


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


activity = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
replace_once(
    activity,
    '    OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit).take(2) }, label = { Text("Vagas") }, modifier = Modifier.fillMaxWidth())\n',
    '    OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit).take(3) }, label = { Text("Capacidade do veículo") }, modifier = Modifier.fillMaxWidth())\n',
    "Stage47 R2 driver capacity input",
)
replace_once(
    activity,
    '                val seats = capacity.toIntOrNull()?.coerceIn(1, 8) ?: throw IllegalArgumentException("Informe uma quantidade de vagas válida.")\n',
    '                val seats = capacity.toIntOrNull() ?: throw IllegalArgumentException("Informe uma quantidade de vagas válida.")\n                require(seats in 1..999) { "Informe uma capacidade entre 1 e 999 lugares." }\n',
    "Stage47 R2 driver capacity validation",
)
replace_once(
    activity,
    '    OutlinedTextField(seatsText, { seatsText = it.filter(Char::isDigit).take(1) }, label = { Text("Vagas") })\n',
    '    OutlinedTextField(seatsText, { seatsText = it.filter(Char::isDigit).take(3) }, label = { Text("Lugares reservados") })\n',
    "Stage47 R2 manual booking seat digits",
)

backend = PATCHES / "trip-platform/functions/index.js"
replace_once(
    backend,
    '  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 8) throw new Error("Capacidade inválida.");\n',
    '  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 999) throw new Error("Capacidade inválida.");\n',
    "Stage47 R2 backend trip capacity",
)
replace_once(
    backend,
    '  if (!Number.isInteger(seats) || seats < 1 || seats > 4) return fail(res, 400, "invalid_seats", "Quantidade de vagas inválida.");\n',
    '  if (!Number.isInteger(seats) || seats < 1 || seats > 999) return fail(res, 400, "invalid_seats", "Quantidade de lugares inválida.");\n',
    "Stage47 R2 backend booking capacity",
)

page = PATCHES / "trip-platform/public/index.html"
replace_once(
    page,
    '    <label style="margin-top:10px">Quantidade de lugares<select id="seats"><option>1</option><option>2</option><option>3</option><option>4</option></select></label>\n',
    '    <label style="margin-top:10px">Quantidade de lugares<input id="seats" type="number" min="1" step="1" value="1" inputmode="numeric"></label>\n',
    "Stage47 R2 public booking seat input",
)

browser = PATCHES / "trip-platform/public/app.js"
replace_once(
    browser,
    '''  const available = availableFor(fromIndex, toIndex);\n  $("availability").textContent = `${available} lugar(es) disponível(is) neste trecho`;\n  $("reserve").disabled = available < Number($("seats").value || 1);\n''',
    '''  const available = availableFor(fromIndex, toIndex);\n  const seatsInput = $("seats");\n  seatsInput.max = String(Math.max(1, available));\n  let requested = Number(seatsInput.value || 1);\n  if (!Number.isInteger(requested) || requested < 1) {\n    requested = 1;\n    seatsInput.value = "1";\n  }\n  if (available > 0 && requested > available) {\n    requested = available;\n    seatsInput.value = String(available);\n  }\n  $("availability").textContent = `${available} lugar(es) disponível(is) neste trecho`;\n  $("reserve").disabled = available < 1 || requested > available;\n''',
    "Stage47 R2 dynamic public availability",
)
replace_once(
    browser,
    '$("seats").addEventListener("change", refreshAvailability);\n',
    '$("seats").addEventListener("input", refreshAvailability);\n$("seats").addEventListener("change", refreshAvailability);\n',
    "Stage47 R2 public booking live seat validation",
)

print("stage47_flexible_capacity_r2=PASS driver_capacity=1..999 public_booking_dynamic=true backend_transaction_guard_preserved=true")
