from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"
PASSENGER_UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
FAROL = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"


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
ui = read(UI)
passenger_ui = read(PASSENGER_UI)
farol_before = FAROL.read_bytes()

if 'versionCode = 5835' not in build or 'versionName = "0.1.543"' not in build:
    fail("Unexpected version baseline; refusing to materialize 0.1.544")

required = [
    'val queueTargetCollectorRefresh0517: () -> Unit = {',
    'AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(',
    'origin = BlaBlaCommandOrigin0407.CARD',
    'onChanged("📡 Agenda buscando somente esta viagem na BlaBlaCar em segundo plano.")',
    'Text(verificationLabel0407, style = MaterialTheme.typography.bodySmall)',
    ') { Text("🔄 Verificar") }',
    'TextButton(onClick = { actionMenuExpanded0407 = true }) { Text("⋮") }',
]
for marker in required:
    if marker not in ui:
        fail(f"Required 0.1.543 invariant missing: {marker}")

if "Carregando passageiros" in passenger_ui:
    fail("Visible passenger loading regression exists in baseline")

# Make the expanded card visibly denser without changing passenger behavior or data authority.
ui = replace_once(
    ui,
    '        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {',
    '        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {',
    "compact card padding",
)

expanded_anchor = '''            if (expanded) {
                Text("RESUMO DA VIAGEM", style = MaterialTheme.typography.labelLarge, color = profileColors.border)

'''
expanded_replacement = '''            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = tripTarget0407 != null && !reverifyPending0407,
                        onClick = queueTargetCollectorRefresh0517,
                    ) {
                        Text(if (reverifyPending0407) "📡 …" else "📡")
                    }
                    TextButton(
                        onClick = { actionMenuExpanded0407 = true },
                    ) { Text("⋮") }
                }

'''
ui = replace_once(ui, expanded_anchor, expanded_replacement, "top targeted card actions")

# Shorter operational summary labels reduce wrapping on the official Samsung width.
ui = replace_once(
    ui,
    '                    "Cota BlaBlaCar: ${allocation.blablaQuota ?: 0} • Cota Rota Certa: ${allocation.rotaCertaQuota ?: 0} • Inventário operacional: ${allocation.operationalInventory ?: entry.capacity}",',
    '                    "BlaBlaCar ${allocation.blablaQuota ?: 0} • Rota Certa ${allocation.rotaCertaQuota ?: 0} • Operacional ${allocation.operationalInventory ?: entry.capacity}",',
    "compact allocation line",
)

occupancy_old = '                    Text("👥 Passageiros confirmados: $passengers • 🪑 Vagas disponíveis: ${free ?: 0} $availabilityLabel")'
occupancy_new = '                    Text("👥 $passengers confirmado(s) • 🪑 ${free ?: 0} vaga(s) $availabilityLabel")'
if ui.count(occupancy_old) != 2:
    fail(f"compact occupancy: expected 2 baseline anchors, found {ui.count(occupancy_old)}")
ui = ui.replace(occupancy_old, occupancy_new)

ui = replace_once(
    ui,
    '                    Text("👥 Passageiros confirmados: 0 • 🪑 Vagas disponíveis: $emptyFree $availabilityLabel")',
    '                    Text("👥 0 confirmado(s) • 🪑 $emptyFree vaga(s) $availabilityLabel")',
    "compact empty occupancy",
)

# The old verification failure/status was a legacy connection diagnostic. Keep its
# underlying capability available from overflow, but remove it from the normal card surface.
verification_label = '''                val verificationLabel0407 = blaBlaVerificationLabel0407(
                    audit = commandAudit0407,
                    lastObservedAtMillis = lastObservedAt0407,
                    strongTargetAvailable = tripTarget0407 != null,
                )
                Text(verificationLabel0407, style = MaterialTheme.typography.bodySmall)
'''
ui = replace_once(ui, verification_label, "", "remove visible verification status")

old_radar = '''                    TextButton(
                        enabled = tripTarget0407 != null && !reverifyPending0407,
                        onClick = queueTargetCollectorRefresh0517,
                    ) { Text("📡") }
'''
ui = replace_once(ui, old_radar, "", "remove duplicate lower radar")

old_verify = '''                    if (BlaBlaTripAction0407.REVERIFY in actionPalette0407.primary) {
                        TextButton(
                            enabled = !reverifyPending0407,
                            onClick = queueReverify0407,
                        ) { Text("🔄 Verificar") }
                    }
'''
ui = replace_once(ui, old_verify, "", "remove primary verify button")

old_overflow_trigger = '''                        TextButton(onClick = { actionMenuExpanded0407 = true }) { Text("⋮") }
'''
ui = replace_once(ui, old_overflow_trigger, "", "remove duplicate lower overflow trigger")

# Preserve the existing collector -> Agenda -> Timeline targeted path; do not create a parallel pipeline.
for marker in [
    'val queueTargetCollectorRefresh0517: () -> Unit = {',
    'AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(',
    'origin = BlaBlaCommandOrigin0407.CARD',
    'TIMELINE_RADAR_LOCAL_IDENTITY_USED',
]:
    if marker not in ui:
        fail(f"Targeted per-card radar invariant lost: {marker}")

if 'Text(verificationLabel0407, style = MaterialTheme.typography.bodySmall)' in ui:
    fail("Visible legacy verification status still present")
if ') { Text("🔄 Verificar") }' in ui:
    fail("Primary legacy Verify button still present")
if ui.count('onClick = queueTargetCollectorRefresh0517') != 1:
    fail("Expected exactly one visible per-card targeted radar action")

build = replace_once(build, 'versionCode = 5835', 'versionCode = 5836', "versionCode")
build = replace_once(build, 'versionName = "0.1.543"', 'versionName = "0.1.544"', "versionName")

write(UI, ui)
write(BUILD, build)

if FAROL.read_bytes() != farol_before:
    fail("FAROL changed unexpectedly")

print("Materialized Timeline 0.1.544 compact targeted-card radar UI")
