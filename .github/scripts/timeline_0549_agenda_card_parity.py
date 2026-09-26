#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt"
PASSENGER_UI = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt"
BUILD = ROOT / "app/build.gradle.kts"
TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/trips/TimelineAgendaCardParity0549Test.kt"


def fail(message: str) -> None:
    raise SystemExit(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        fail(f"{label}: expected exactly one baseline anchor, found {count}")
    return content.replace(old, new, 1)


build = read(BUILD)
ui = read(UI)
passenger_ui = read(PASSENGER_UI)

if 'versionCode = 5840' not in build or 'versionName = "0.1.548"' not in build:
    fail("Unexpected version baseline; refusing to materialize 0.1.549")

for marker in [
    'private fun TimelineEntryCard(',
    'val queueTargetCollectorRefresh0517: () -> Unit = {',
    'EnhancedPassengerTimelineSection(',
    'TripTimelineIssue.OVERBOOKING in entry.issues -> Text("❌ URGENTE:',
    'Text(if (reverifyPending0407) "📡 …" else "📡")',
]:
    if marker not in ui:
        fail(f"Required 0.1.548 Timeline invariant missing: {marker}")

for marker in [
    'internal fun EnhancedPassengerTimelineSection(',
    'private fun TripBlaBlaTripActionRow(',
    'if (hasExternalTripActionEvidence(entry)) {\n        TripBlaBlaTripActionRow(entry, onAddManualPassenger)\n    }',
]:
    if marker not in passenger_ui:
        fail(f"Required passenger UI invariant missing: {marker}")

# Imports used by the Agenda-style surface.
ui = replace_once(
    ui,
    'import androidx.compose.foundation.background\n',
    'import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\n',
    'border import',
)
ui = replace_once(
    ui,
    'import androidx.compose.foundation.layout.Row\n',
    'import androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.layout.Spacer\n',
    'Spacer import',
)
ui = replace_once(
    ui,
    'import androidx.compose.foundation.layout.heightIn\n',
    'import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n',
    'height import',
)
ui = replace_once(
    ui,
    'import androidx.compose.foundation.layout.padding\n',
    'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.width\n',
    'size/width imports',
)
ui = replace_once(
    ui,
    'import androidx.compose.foundation.shape.RoundedCornerShape\n',
    'import androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape\n',
    'CircleShape import',
)
ui = replace_once(
    ui,
    'import androidx.compose.material3.ExperimentalMaterial3Api\n',
    'import androidx.compose.material3.ExperimentalMaterial3Api\nimport androidx.compose.material3.HorizontalDivider\n',
    'HorizontalDivider import',
)
ui = replace_once(
    ui,
    'import androidx.compose.ui.platform.LocalContext\n',
    'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.text.font.FontWeight\n',
    'FontWeight import',
)

# Allow the card-level driver shortcut strip to be rendered exactly once at the
# very top of the Timeline card, while keeping the passenger rows below it.
passenger_ui = replace_once(
    passenger_ui,
    '''    focusedBookingId: String? = null,\n    canonicalBookings0494: List<Booking>? = null,\n) {''',
    '''    focusedBookingId: String? = null,\n    canonicalBookings0494: List<Booking>? = null,\n    showTripActions0549: Boolean = true,\n) {''',
    'EnhancedPassengerTimelineSection showTripActions parameter',
)
passenger_ui = replace_once(
    passenger_ui,
    '''    if (hasExternalTripActionEvidence(entry)) {\n        TripBlaBlaTripActionRow(entry, onAddManualPassenger)\n    }''',
    '''    if (showTripActions0549) {\n        TripBlaBlaTripActionRow(entry, onAddManualPassenger)\n    }''',
    'single owner for trip actions',
)
passenger_ui = replace_once(
    passenger_ui,
    '''@Composable\nprivate fun TripBlaBlaTripActionRow(\n    entry: TripTimelineEntry,\n    onAddManualPassenger: (() -> Unit)?,\n) {''',
    '''@Composable\ninternal fun TripBlaBlaTripActionRow(\n    entry: TripTimelineEntry,\n    onAddManualPassenger: (() -> Unit)?,\n    leadingActions0549: (@Composable () -> Unit)? = null,\n    trailingActions0549: (@Composable () -> Unit)? = null,\n) {''',
    'expose driver shortcut strip with extension slots',
)
passenger_ui = replace_once(
    passenger_ui,
    '''        ) {\n        if (onAddManualPassenger != null) {\n            TextButton(''',
    '''        ) {\n        leadingActions0549?.invoke()\n        if (onAddManualPassenger != null) {\n            TextButton(''',
    'prepend Timeline-only top actions',
)
passenger_ui = replace_once(
    passenger_ui,
    '''        IconButton(\n            onClick = {\n                if (!openExternalTripBlaBla(context, entry.blablaProfileUuid, entry.blablaTripHref)) {\n                    Toast.makeText(\n                        context,\n                        "Link direto da viagem indisponível. A referência será recuperada pela atualização automática quando houver evidência suficiente.",\n                        Toast.LENGTH_LONG,\n                    ).show()\n                }\n            },\n            modifier = Modifier.size(36.dp),\n        ) {\n            Icon(\n                painter = painterResource(R.drawable.ic_blablacar_action),\n                contentDescription = "Abrir viagem no BlaBlaCar",\n                tint = Color.Unspecified,\n                modifier = Modifier.size(24.dp),\n            )\n        }\n        }\n''',
    '''        if (hasExternalTripActionEvidence(entry)) {\n            IconButton(\n                onClick = {\n                    if (!openExternalTripBlaBla(context, entry.blablaProfileUuid, entry.blablaTripHref)) {\n                        Toast.makeText(\n                            context,\n                            "Link direto da viagem indisponível. A referência será recuperada pela atualização automática quando houver evidência suficiente.",\n                            Toast.LENGTH_LONG,\n                        ).show()\n                    }\n                },\n                modifier = Modifier.size(36.dp),\n            ) {\n                Icon(\n                    painter = painterResource(R.drawable.ic_blablacar_action),\n                    contentDescription = "Abrir viagem no BlaBlaCar",\n                    tint = Color.Unspecified,\n                    modifier = Modifier.size(24.dp),\n                )\n            }\n        }\n        trailingActions0549?.invoke()\n        }\n''',
    'append Timeline top overflow and hide unavailable direct-trip icon',
)

card_start = '''    Card(\n        modifier = Modifier\n            .fillMaxWidth()\n            .padding(vertical = 6.dp)\n            .clickable('''
card_end = '''\n\n    if (showSeatDetails) {'''
start_count = ui.count(card_start)
end_count = ui.count(card_end)
if start_count != 1 or end_count != 1:
    fail(f"Timeline card boundaries not unique: start={start_count} end={end_count}")
start = ui.index(card_start)
end = ui.index(card_end, start)

new_card = r'''    val publicCapacity0549 = timelinePublicCapacityResolution(entry)
    val publicLoads0549 = if (entry.canonicalBackendAuthoritative0494) {
        canonicalSegmentLoads0494
    } else {
        seatPlan?.let { plan -> timelinePublicSegmentLoads(entry, plan.loads) }.orEmpty()
    }
    val minimumAvailable0549 = publicLoads0549.minOfOrNull(SegmentLoad::availableSeats)
        ?: publicCapacity0549.availableSeats
    val operationalInventory0549 = publicCapacity0549.operationalInventory
    val agendaBackground0549 = if (dark) Color(0xFF242628) else Color(0xFFF5F6F7)
    val agendaBorder0549 = if (dark) Color(0xFF4B4F54) else Color(0xFFD9DCE1)
    val agendaAccent0549 = if (dark) Color(0xFF72A7D4) else Color(0xFF4F789E)
    val agendaMuted0549 = if (dark) Color(0xFFB8BDC5) else Color(0xFF646A73)
    val agendaSeatAccent0549 = Color(0xFF2398D0)
    val ptBr0549 = Locale("pt", "BR")
    val departure0549 = Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault())
    val arrival0549 = entry.arrivalAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val agendaDate0549 = DateTimeFormatter
        .ofPattern("EEEE, d 'de' MMMM 'de' yyyy", ptBr0549)
        .format(departure0549)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBr0549) else it.toString() }
    val clock0549 = DateTimeFormatter.ofPattern("HH:mm", ptBr0549)
    val departureClock0549 = clock0549.format(departure0549)
    val arrivalClock0549 = arrival0549?.let(clock0549::format) ?: "—"
    val duration0549 = timelineDurationLabel0536(entry.departureAtMillis, entry.arrivalAtMillis)
    val observedClock0549 = lastObservedAt0407.takeIf { it > 0L }?.let {
        clock0549.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }
    val openManualPassenger0549: () -> Unit = {
        when {
            entry.canonicalBackendAuthoritative0494 && trip != null -> directPassengerTrip = trip
            else -> runCatching { prepareTimelineTripForPassenger(entry, store) }
                .onSuccess { preparation -> directPassengerTrip = preparation.trip }
                .onFailure { error ->
                    onChanged(error.message ?: "Não foi possível preparar este card para adicionar passageiro.")
                }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(
                onClickLabel = if (expanded) "Recolher trajeto" else "Abrir trajeto",
                onClick = onToggleExpanded,
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = agendaBackground0549),
        border = BorderStroke(1.dp, agendaBorder0549),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 0.1.549: every card-level driver shortcut owns the first/top row.
            TripBlaBlaTripActionRow(
                entry = entry,
                onAddManualPassenger = openManualPassenger0549,
                leadingActions0549 = {
                    TextButton(
                        enabled = tripTarget0407 != null && !reverifyPending0407,
                        onClick = queueTargetCollectorRefresh0517,
                    ) {
                        Text(if (reverifyPending0407) "📡 …" else "📡")
                    }
                    if (BlaBlaTripAction0407.SEAT_DETAILS in actionPalette0407.primary) {
                        TextButton(onClick = { showSeatDetails = true }) {
                            Text(if (minimumAvailable0549 != null) "💺 $minimumAvailable0549" else "💺")
                        }
                    }
                },
                trailingActions0549 = {
                    Box {
                        TextButton(onClick = { actionMenuExpanded0407 = true }) { Text("⋮") }
                        DropdownMenu(
                            expanded = actionMenuExpanded0407,
                            onDismissRequest = { actionMenuExpanded0407 = false },
                        ) {
                            if (BlaBlaTripAction0407.REVERIFY in actionPalette0407.overflow) {
                                DropdownMenuItem(
                                    text = { Text("🔄 Verificar agora") },
                                    enabled = !reverifyPending0407,
                                    onClick = {
                                        actionMenuExpanded0407 = false
                                        queueReverify0407()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("🪑 Vagas por trecho") },
                                onClick = {
                                    actionMenuExpanded0407 = false
                                    showSeatDetails = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Agenda pública: online/offline") },
                                enabled = trip?.remoteId?.isNotBlank() == true || trip?.publicToken?.isNotBlank() == true,
                                onClick = {
                                    actionMenuExpanded0407 = false
                                    val targetTrip0491 = trip
                                    val settings0491 = store.onlineSettings()
                                    val remoteId0491 = targetTrip0491?.remoteId?.takeIf(String::isNotBlank)
                                        ?: targetTrip0491?.publicToken?.takeIf(String::isNotBlank)
                                    when {
                                        targetTrip0491 == null || remoteId0491 == null ->
                                            onChanged("Esta viagem ainda não possui identidade remota para alterar a publicação.")
                                        !settings0491.configured ->
                                            onChanged("Integração online necessária para alterar a publicação.")
                                        else -> scope.launch {
                                            runCatching {
                                                val api0491 = TripRemoteApi(settings0491)
                                                val state0491 = api0491
                                                    .listDriverTripSyncStates0402(includePastForVerification0429 = true)
                                                    .trips
                                                    .firstOrNull { it.remoteTripId == remoteId0491 }
                                                    ?: error("Estado remoto da viagem não encontrado.")
                                                api0491.updateDriverTripPublicVisibility0491(
                                                    remoteTripId = remoteId0491,
                                                    online = !state0491.publicAgendaOnline0471,
                                                    expectedVisibilityRevision0471 = state0491.publicAgendaVisibilityRevision0471,
                                                )
                                            }.onSuccess { response0491 ->
                                                onChanged(
                                                    if (response0491.online) {
                                                        "🟢 Viagem online na Agenda Pública."
                                                    } else {
                                                        "⚪ Viagem offline na Agenda Pública; o estado canônico foi preservado."
                                                    },
                                                )
                                            }.onFailure { error0491 ->
                                                onChanged(
                                                    "Não foi possível alterar online/offline: " +
                                                        (error0491.message ?: error0491.javaClass.simpleName),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                            if (BlaBlaTripAction0407.OPEN_PUBLICATION in actionPalette0407.overflow) {
                                DropdownMenuItem(
                                    text = { Text("Ver publicação BlaBlaCar") },
                                    onClick = {
                                        actionMenuExpanded0407 = false
                                        val href = canonicalPublicationHref0490
                                        if (href == null || !openBlaBlaHref(context, entry, href)) {
                                            onChanged("A URL pública canônica desta viagem ainda não está disponível.")
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )

            Text(
                text = agendaDate0549,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Viagens com $profileDisplayLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = agendaMuted0549,
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(76.dp)) {
                    Text(departureClock0549, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(duration0549, style = MaterialTheme.typography.bodySmall, color = agendaMuted0549)
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(arrivalClock0549, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier.width(34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("○", color = agendaAccent0549, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("│", color = agendaAccent0549, style = MaterialTheme.typography.headlineSmall)
                    Text("○", color = agendaAccent0549, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.origin, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(34.dp))
                    Text(entry.destination, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }

            if (expanded) {
                HorizontalDivider(color = agendaBorder0549)
                Text("Vagas por trecho", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                if (publicLoads0549.isEmpty()) {
                    Text(
                        "Disponibilidade por trecho aguardando o estado canônico da Agenda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = agendaMuted0549,
                    )
                } else {
                    publicLoads0549.forEach { load0549 ->
                        val seatCount0549 = (
                            load0549.passengerSeats + load0549.blockedSeats + load0549.availableSeats
                        ).coerceAtLeast(1).coerceAtMost(8)
                        val occupied0549 = (seatCount0549 - load0549.availableSeats).coerceIn(0, seatCount0549)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${load0549.from.name} → ${load0549.to.name}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(seatCount0549) { seatIndex0549 ->
                                    Box(
                                        modifier = Modifier
                                            .size(25.dp)
                                            .border(2.dp, agendaSeatAccent0549, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (seatIndex0549 < occupied0549) {
                                            Text("●", color = Color(0xFF9AA5B1), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            Text(
                                "${load0549.availableSeats} vagas",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                HorizontalDivider(color = agendaBorder0549)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🚙", style = MaterialTheme.typography.headlineSmall)
                    Column(horizontalAlignment = Alignment.End) {
                        minimumAvailable0549?.let { free0549 ->
                            Text(
                                if (free0549 == 1) "1 vaga disponível" else "$free0549 vagas disponíveis",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        observedClock0549?.let { observed0549 ->
                            Text("Atualizado às $observed0549", style = MaterialTheme.typography.bodySmall, color = agendaMuted0549)
                        }
                    }
                }
            }

            Text(
                text = if (expanded) "Recolher trajeto" else "Ver trajeto",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = agendaSeatAccent0549,
                fontWeight = FontWeight.Bold,
            )

            if (expanded && minimumAvailable0549 != null) {
                Text(
                    text = if (minimumAvailable0549 == 0) "LOTADO" else "$minimumAvailable0549 VAGA(S)",
                    style = MaterialTheme.typography.titleLarge,
                    color = agendaMuted0549,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (expanded) {
                HorizontalDivider(color = agendaBorder0549)
                EnhancedPassengerTimelineSection(
                    entry = entry,
                    trip = trip,
                    store = store,
                    currentCoordinate = currentCoordinate,
                    onChanged = onChanged,
                    focusedBookingId = focusedBookingId,
                    canonicalBookings0494 = bookingsSnapshot0432,
                    showTripActions0549 = false,
                    onAddManualPassenger = openManualPassenger0549,
                )

                val canCompleteRemoteTrip0549 =
                    trip?.remoteId != null &&
                        entry.departureAtMillis <= System.currentTimeMillis() &&
                        trip.status !in setOf(TripStatus.COMPLETED, TripStatus.CANCELLED)
                ResponsiveTripActions(
                    buildList {
                        add(ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() })
                        if (canCompleteRemoteTrip0549) {
                            add(
                                ResponsiveTripAction("Concluir viagem") {
                                    val target0549 = trip
                                    val settings0549 = store.onlineSettings()
                                    when {
                                        target0549 == null -> onChanged("Viagem não identificada para conclusão.")
                                        !settings0549.configured -> onChanged("Integração online necessária para concluir a viagem.")
                                        else -> scope.launch {
                                            runCatching {
                                                val remoteId0549 = target0549.remoteId?.takeIf(String::isNotBlank)
                                                    ?: error("Viagem sem identidade remota canônica.")
                                                TripRemoteApi(settings0549).update(
                                                    target0549.copy(
                                                        remoteId = remoteId0549,
                                                        status = TripStatus.COMPLETED,
                                                    ),
                                                )
                                            }.onSuccess { response0549 ->
                                                UnifiedDebugEventStore.record(
                                                    "TIMELINE_CANONICAL_TRIP_COMPLETED_0494",
                                                    context.packageName,
                                                    "canonicalTripId=${seatSyncDiagnosticKey(target0549.id)} entityRevision=${response0549.entityRevision} authority=CANONICAL_BACKEND localBusinessWrite=false",
                                                )
                                                onChanged("Viagem concluída no backend canônico. Timeline e Agenda receberão a nova revisão.")
                                            }.onFailure { error0549 ->
                                                onChanged("Nada foi alterado: ${error0549.message ?: "falha no backend canônico"}")
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
    }'''

ui = ui[:start] + new_card + ui[end:]

# Normal Timeline surface must not expose the obsolete diagnostic/derived blocks anymore.
card_slice_start = ui.index('private fun TimelineEntryCard(')
card_slice_end = ui.index('\n    if (showSeatDetails) {', card_slice_start)
card_slice = ui[card_slice_start:card_slice_end]
for forbidden in [
    '❌ URGENTE:',
    'BlaBlaCar ${allocation.blablaQuota',
    'sourceLine = entry.sourcePassengerSeats',
    'Passageiros confirmados:',
    'Identidade externa incompleta;',
    'Toque para fechar',
    'timelineProfileCardColors(profileColorSlot, dark)',
]:
    if forbidden in card_slice:
        fail(f"Divergent legacy Timeline surface survived: {forbidden}")

for required in [
    'TripBlaBlaTripActionRow(',
    'Vagas por trecho',
    'Recolher trajeto',
    'showTripActions0549 = false',
    'RoundedCornerShape(26.dp)',
    'agendaBackground0549',
    'minimumAvailable0549',
]:
    if required not in card_slice:
        fail(f"Agenda parity marker missing after rewrite: {required}")

build = replace_once(build, 'versionCode = 5840', 'versionCode = 5841', 'versionCode')
build = replace_once(build, 'versionName = "0.1.548"', 'versionName = "0.1.549"', 'versionName')

write(UI, ui)
write(PASSENGER_UI, passenger_ui)
write(BUILD, build)

write(
    TEST,
    r'''package br.com.mapeiaia.rotacerta.trips

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineAgendaCardParity0549Test {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun timelineCardUsesAgendaSurfaceAndSuppressesLegacyDerivedDiagnostics() {
        val ui = source("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt")
        val start = ui.indexOf("private fun TimelineEntryCard(")
        val end = ui.indexOf("if (showSeatDetails)", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val card = ui.substring(start, end)

        assertTrue(card.contains("Vagas por trecho"))
        assertTrue(card.contains("Recolher trajeto"))
        assertTrue(card.contains("TripBlaBlaTripActionRow("))
        assertTrue(card.contains("showTripActions0549 = false"))
        assertTrue(card.contains("RoundedCornerShape(26.dp)"))
        assertTrue(card.contains("minimumAvailable0549"))

        assertFalse(card.contains("❌ URGENTE:"))
        assertFalse(card.contains("BlaBlaCar ${'$'}{allocation.blablaQuota"))
        assertFalse(card.contains("sourceLine = entry.sourcePassengerSeats"))
        assertFalse(card.contains("Passageiros confirmados:"))
        assertFalse(card.contains("Identidade externa incompleta;"))
        assertFalse(card.contains("Toque para fechar"))
    }

    @Test
    fun driverShortcutsHaveSingleTopOwner() {
        val passenger = source("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt")
        assertTrue(passenger.contains("showTripActions0549: Boolean = true"))
        assertTrue(passenger.contains("leadingActions0549?.invoke()"))
        assertTrue(passenger.contains("trailingActions0549?.invoke()"))
        assertTrue(passenger.contains("internal fun TripBlaBlaTripActionRow("))
    }
}
''',
)

print("Materialized Timeline 0.1.549 Agenda-card visual parity with top driver shortcuts")
