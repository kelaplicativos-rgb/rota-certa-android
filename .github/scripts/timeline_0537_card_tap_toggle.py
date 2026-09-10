from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
PASSENGER_UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"


def fail(message: str) -> None:
    raise SystemExit(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        fail(f"{label}: expected exactly one baseline anchor, found {count}")
    return content.replace(old, new, 1)


build = read(BUILD)
if 'versionCode = 5828' not in build or 'versionName = "0.1.536"' not in build:
    fail("Unexpected version baseline; refusing to materialize 0.1.537")

ui = read(UI)
passenger_ui = read(PASSENGER_UI)

required_ui = [
    'var expandedTripIds0536 by remember { mutableStateOf<Set<String>>(emptySet()) }',
    'timelineDriverProfileLabel0536(',
    'Text("RESUMO DA VIAGEM"',
]
for marker in required_ui:
    if marker not in ui:
        fail(f"Required 0.1.536 Timeline invariant missing: {marker}")

required_passenger = [
    'val pickupTarget = passengerPickupMapTarget(passenger)',
    'openPassengerPickupMap(context, pickupTarget)',
    'val dropoffTarget = passengerDropoffMapTarget(passenger)',
    'openPassengerDropoffMap(context, dropoffTarget)',
]
for marker in required_passenger:
    if marker not in passenger_ui:
        fail(f"Passenger operational control missing before change: {marker}")
if "Carregando passageiros" in passenger_ui:
    fail("Visible passenger loading regression exists in baseline")

old_card = '''    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = profileColors.background),'''
new_card = '''    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(
                onClickLabel = if (expanded) "Fechar viagem" else "Abrir viagem",
                onClick = onToggleExpanded,
            ),
        colors = CardDefaults.cardColors(containerColor = profileColors.background),'''
ui = replace_once(ui, old_card, new_card, "Timeline whole-card click target")

old_footer = '''                TextButton(onClick = onToggleExpanded) {
                    Text(if (expanded) "▲ Fechar" else "▼ Abrir")
                }'''
new_footer = '''                Text(
                    text = if (expanded) "▲ Toque para fechar" else "▼ Toque para abrir",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )'''
ui = replace_once(ui, old_footer, new_footer, "Timeline passive expand/collapse hint")

write(UI, ui)

build = replace_once(build, 'versionCode = 5828', 'versionCode = 5829', "versionCode")
build = replace_once(build, 'versionName = "0.1.536"', 'versionName = "0.1.537"', "versionName")
write(BUILD, build)

final_ui = read(UI)
final_passenger_ui = read(PASSENGER_UI)
if 'onClickLabel = if (expanded) "Fechar viagem" else "Abrir viagem"' not in final_ui:
    fail("Whole-card accessibility click label was not materialized")
if 'onClick = onToggleExpanded' not in final_ui:
    fail("Whole-card expansion callback was not materialized")
if 'TextButton(onClick = onToggleExpanded)' in final_ui:
    fail("Tiny dedicated footer target is still present")
if '"▲ Toque para fechar" else "▼ Toque para abrir"' not in final_ui:
    fail("Passive whole-card tap hint missing")
for marker in required_ui:
    if marker not in final_ui:
        fail(f"Timeline regression after change: {marker}")
for marker in required_passenger:
    if marker not in final_passenger_ui:
        fail(f"Passenger control regression after change: {marker}")
if "Carregando passageiros" in final_passenger_ui:
    fail("Passenger loading text regression detected")

print("Timeline 0.1.537 whole-card tap toggle materialized successfully")
