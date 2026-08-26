from pathlib import Path

passenger_ui = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt')
timeline_ui = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt')
flow_ui = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripGlobalPassengerFlow0256.kt')
gradle = Path('app/build.gradle.kts')
test = Path('app/src/test/java/br/com/mapeiaia/rotacerta/trips/TripTimelineCardActions0289Test.kt')

ptext = passenger_ui.read_text()
ttext = timeline_ui.read_text()
ftext = flow_ui.read_text()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}: {old[:100]!r}')
    return text.replace(old, new, 1)


ptext = replace_once(
    ptext,
    '    onChanged: (String) -> Unit,\n'
    '    onSyncExactCard: (() -> Unit)? = null,\n'
    ') {\n',
    '    onChanged: (String) -> Unit,\n'
    '    onSyncExactCard: (() -> Unit)? = null,\n'
    '    onAddManualPassenger: (() -> Unit)? = null,\n'
    ') {\n',
    'passenger section signature',
)

ptext = replace_once(
    ptext,
    '    val passengerStore = remember(context) { PassengerIdentityStore(context) }\n'
    '    val rawRows = enhancedPassengerRows(entry, trip, store, passengerStore)\n'
    '    if (rawRows.isEmpty()) return\n\n'
    '    val progress = trip?.let { TripPassengerRouteOrder.progress(it, currentCoordinate) }\n',
    '    val passengerStore = remember(context) { PassengerIdentityStore(context) }\n'
    '    val rawRows = enhancedPassengerRows(entry, trip, store, passengerStore)\n'
    '    if (hasExternalTripActionEvidence(entry)) {\n'
    '        TripBlaBlaTripActionRow(entry, onSyncExactCard, onAddManualPassenger)\n'
    '    }\n'
    '    if (rawRows.isEmpty()) return\n\n'
    '    val progress = trip?.let { TripPassengerRouteOrder.progress(it, currentCoordinate) }\n',
    'card actions before empty passenger return',
)

ptext = replace_once(
    ptext,
    '    if (hasExternalTripActionEvidence(entry)) {\n'
    '        TripBlaBlaTripActionRow(entry, onSyncExactCard)\n'
    '    }\n\n'
    '    rows.forEachIndexed { index, passenger ->\n',
    '    rows.forEachIndexed { index, passenger ->\n',
    'remove old passenger-dependent card action placement',
)

ptext = replace_once(
    ptext,
    '@Composable\n'
    'private fun TripBlaBlaTripActionRow(entry: TripTimelineEntry, onSyncExactCard: (() -> Unit)?) {\n'
    '    val context = LocalContext.current\n'
    '    Row(\n'
    '        modifier = Modifier.fillMaxWidth(),\n'
    '        horizontalArrangement = Arrangement.End,\n'
    '        verticalAlignment = Alignment.CenterVertically,\n'
    '    ) {\n',
    '@Composable\n'
    'private fun TripBlaBlaTripActionRow(\n'
    '    entry: TripTimelineEntry,\n'
    '    onSyncExactCard: (() -> Unit)?,\n'
    '    onAddManualPassenger: (() -> Unit)?,\n'
    ') {\n'
    '    val context = LocalContext.current\n'
    '    Row(\n'
    '        modifier = Modifier.fillMaxWidth(),\n'
    '        horizontalArrangement = Arrangement.End,\n'
    '        verticalAlignment = Alignment.CenterVertically,\n'
    '    ) {\n'
    '        if (onAddManualPassenger != null) {\n'
    '            TextButton(\n'
    '                onClick = {\n'
    '                    UnifiedDebugEventStore.record(\n'
    '                        "AGENDA_CARD_MANUAL_PASSENGER_OPEN",\n'
    '                        context.packageName,\n'
    '                        "timeline=true externalPublication=true",\n'
    '                    )\n'
    '                    onAddManualPassenger()\n'
    '                },\n'
    '                contentPadding = COMPACT_ACTION_PADDING,\n'
    '            ) { Text("👤➕") }\n'
    '        }\n',
    'card action row manual passenger button',
)

passenger_ui.write_text(ptext)


ttext = replace_once(
    ttext,
    '            currentCoordinate = currentCoordinate,\n'
    '            onSyncExactCard = {\n',
    '            currentCoordinate = currentCoordinate,\n'
    '            onManualSeatSyncRequested = {\n'
    '                autoSyncProfileUuid = canonicalTimelineProfileUuid(entry)\n'
    '                autoSyncTripId = null\n'
    '                onRequestBlaBlaSync()\n'
    '            },\n'
    '            onSyncExactCard = {\n',
    'timeline card manual seat callback',
)


ttext = replace_once(
    ttext,
    '    directionGeo: Map<String, TimelineGeoPoint>,\n'
    '    currentCoordinate: Coordinate?,\n'
    '    onSyncExactCard: () -> Unit,\n'
    '    onArchive: () -> Unit,\n'
    ') {\n',
    '    directionGeo: Map<String, TimelineGeoPoint>,\n'
    '    currentCoordinate: Coordinate?,\n'
    '    onManualSeatSyncRequested: () -> Unit,\n'
    '    onSyncExactCard: () -> Unit,\n'
    '    onArchive: () -> Unit,\n'
    ') {\n',
    'timeline card signature',
)


ttext = replace_once(
    ttext,
    '    val dark = isSystemInDarkTheme()\n'
    '    val profileColors = timelineProfileCardColors(profileColorSlot, dark)\n\n'
    '    Card(\n',
    '    val dark = isSystemInDarkTheme()\n'
    '    val profileColors = timelineProfileCardColors(profileColorSlot, dark)\n'
    '    var directPassengerTrip by remember(entry.tripId) { mutableStateOf<Trip?>(null) }\n\n'
    '    Card(\n',
    'timeline card direct passenger state',
)


ttext = replace_once(
    ttext,
    '                currentCoordinate = currentCoordinate,\n'
    '                onChanged = onChanged,\n'
    '                onSyncExactCard = onSyncExactCard,\n'
    '            )\n',
    '                currentCoordinate = currentCoordinate,\n'
    '                onChanged = onChanged,\n'
    '                onSyncExactCard = onSyncExactCard,\n'
    '                onAddManualPassenger = {\n'
    '                    runCatching { prepareTimelineTripForPassenger(entry, store) }\n'
    '                        .onSuccess { preparation -> directPassengerTrip = preparation.trip }\n'
    '                        .onFailure { error ->\n'
    '                            onChanged(error.message ?: "Não foi possível preparar este card para adicionar passageiro.")\n'
    '                        }\n'
    '                },\n'
    '            )\n',
    'timeline card direct passenger action',
)


ttext = replace_once(
    ttext,
    '            ResponsiveTripActions(\n'
    '                listOf(\n'
    '                    ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() },\n'
    '                ),\n'
    '            )\n'
    '        }\n'
    '    }\n'
    '}\n\n'
    'internal data class TimelineQuickPassengerOption(\n',
    '            ResponsiveTripActions(\n'
    '                listOf(\n'
    '                    ResponsiveTripAction(if (archived) "Restaurar" else "Arquivar") { onArchive() },\n'
    '                ),\n'
    '            )\n'
    '        }\n'
    '    }\n\n'
    '    directPassengerTrip?.let { selectedTrip ->\n'
    '        TimelineCardQuickPassengerDialog(\n'
    '            entry = entry,\n'
    '            trip = selectedTrip,\n'
    '            store = store,\n'
    '            onChanged = onChanged,\n'
    '            onTargetSync = onManualSeatSyncRequested,\n'
    '            onDismiss = { directPassengerTrip = null },\n'
    '        )\n'
    '    }\n'
    '}\n\n'
    'internal data class TimelineQuickPassengerOption(\n',
    'timeline card quick passenger dialog',
)

timeline_ui.write_text(ttext)


ftext = replace_once(
    ftext,
    '@Composable\n'
    'internal fun GlobalPassengerFlowPanel(\n',
    '@Composable\n'
    'internal fun TimelineCardQuickPassengerDialog(\n'
    '    entry: TripTimelineEntry,\n'
    '    trip: Trip,\n'
    '    store: TripStore,\n'
    '    onChanged: (String) -> Unit,\n'
    '    onTargetSync: () -> Unit,\n'
    '    onDismiss: () -> Unit,\n'
    ') {\n'
    '    val formatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.getDefault()) }\n'
    '    val date = formatter.format(Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()))\n'
    '    AlertDialog(\n'
    '        onDismissRequest = onDismiss,\n'
    '        title = { Text("Adicionar passageiro") },\n'
    '        text = {\n'
    '            Column(\n'
    '                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),\n'
    '                verticalArrangement = Arrangement.spacedBy(8.dp),\n'
    '            ) {\n'
    '                Text("${entry.profileLabel} • $date", style = MaterialTheme.typography.labelLarge)\n'
    '                Text("${entry.origin} → ${entry.destination}")\n'
    '                Text(\n'
    '                    "Passageiros adicionados aqui ocupam a Agenda imediatamente. Quando esta publicação BlaBlaCar tem identidade forte, salvar reduz as vagas externas e remover devolve somente uma redução já comprovada.",\n'
    '                    style = MaterialTheme.typography.bodySmall,\n'
    '                )\n'
    '                QuickPassengerPanel(\n'
    '                    trip = trip,\n'
    '                    store = store,\n'
    '                    onChanged = onChanged,\n'
    '                    onBlaBlaSyncRequested = if (timelineStrongExternalTripKey(entry) != null) onTargetSync else null,\n'
    '                    externalSeatTarget = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry),\n'
    '                    onSaved = onDismiss,\n'
    '                    showExistingPassengers = true,\n'
    '                )\n'
    '            }\n'
    '        },\n'
    '        confirmButton = {},\n'
    '        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },\n'
    '    )\n'
    '}\n\n'
    '@Composable\n'
    'internal fun GlobalPassengerFlowPanel(\n',
    'card quick passenger dialog helper',
)

flow_ui.write_text(ftext)


gradle_text = gradle.read_text()
old_version = '        versionCode = 5581\n        versionName = "0.1.288"'
new_version = '        versionCode = 5582\n        versionName = "0.1.289"'
if gradle_text.count(old_version) != 1:
    raise SystemExit('version baseline mismatch')
gradle.write_text(gradle_text.replace(old_version, new_version, 1))


test.write_text("""package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineCardActions0289Test {
    @Test
    fun directionLabelsKeepGpsOriginContract() {
        assertEquals("↑ IDA", timelineDirectionDisplayLabel(TimelineDirectionState.OUTBOUND))
        assertEquals("↓ VOLTA", timelineDirectionDisplayLabel(TimelineDirectionState.INBOUND))
    }

    @Test
    fun externalCardWithoutPassengersStillHasDirectActionEvidence() {
        val entry = TripTimelineEntry(
            tripId = "timeline-trip",
            profileId = "7371f028-9c55-4903-8444-308015823efd",
            profileLabel = "Perfil",
            departureAtMillis = 1_800_000_000_000L,
            arrivalAtMillis = null,
            origin = "Santo André",
            destination = "Três Corações",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            blablaTripId = "trip-123",
            blablaTripHref = "https://www.blablacar.com.br/rides/offer/trip-123",
            blablaProfileUuid = "7371f028-9c55-4903-8444-308015823efd",
            blablaPassengers = emptyList(),
        )

        assertTrue(entry.blablaPassengers.isEmpty())
        assertTrue(hasExternalTripActionEvidence(entry))
        assertNotNull(externalTripTarget(entry.blablaProfileUuid, entry.blablaTripHref))
        assertNotNull(BlaBlaReliableSeatSyncBridge.targetForTimeline(entry))
    }
}
""")
