from pathlib import Path

passenger_ui = Path('app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt')
seat_test = Path('app/src/test/java/br/com/mapeiaia/rotacerta/trips/BlaBlaReliableSeatSync0271Test.kt')
gradle = Path('app/build.gradle.kts')

ptext = passenger_ui.read_text()
test_text = seat_test.read_text()
gradle_text = gradle.read_text()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}: {old[:120]!r}')
    return text.replace(old, new, 1)


ptext = replace_once(
    ptext,
    '    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n'
    '    var createProfileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n',
    '    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n'
    '    var cancelManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n'
    '    var createProfileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }\n',
    'manual cancellation state',
)

ptext = replace_once(
    ptext,
    '    profileRow?.let { row ->\n'
    '        val profile = passengerStore.profile(row.passengerId)\n'
    '            ?: passengerStore.profileByExternalPassengerId(row.externalPassengerId)\n'
    '        val history = profile?.let { passengerStore.rideHistory(it.id) }\n'
    '        AlertDialog(\n',
    '    profileRow?.let { row ->\n'
    '        val profile = passengerStore.profile(row.passengerId)\n'
    '            ?: passengerStore.profileByExternalPassengerId(row.externalPassengerId)\n'
    '        val history = profile?.let { passengerStore.rideHistory(it.id) }\n'
    '        val manualBooking = trip?.let { currentTrip ->\n'
    '            row.localBookingId?.let { bookingId ->\n'
    '                store.bookingsFor(currentTrip.id).firstOrNull { booking ->\n'
    '                    booking.id == bookingId &&\n'
    '                        booking.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&\n'
    '                        booking.capacityClaimType == CapacityClaimType.PASSENGER &&\n'
    '                        booking.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)\n'
    '                }\n'
    '            }\n'
    '        }\n'
    '        AlertDialog(\n',
    'resolve cancellable manual booking in passenger profile',
)

ptext = replace_once(
    ptext,
    '                    if (profile?.blocked == true) Text("🚫 PASSAGEIRO BLOQUEADO", color = MaterialTheme.colorScheme.error)\n'
    '                }\n'
    '            },\n',
    '                    if (profile?.blocked == true) Text("🚫 PASSAGEIRO BLOQUEADO", color = MaterialTheme.colorScheme.error)\n'
    '                    if (manualBooking != null) {\n'
    '                        TextButton(onClick = {\n'
    '                            cancelManualRow = row\n'
    '                            profileRow = null\n'
    '                        }) { Text("Cancelar / excluir desta viagem") }\n'
    '                    }\n'
    '                }\n'
    '            },\n',
    'manual cancellation action in passenger profile',
)

ptext = replace_once(
    ptext,
    '    createProfileRow?.let { row ->\n',
    '    cancelManualRow?.let { row ->\n'
    '        val currentTrip = trip\n'
    '        val booking = currentTrip?.let { selectedTrip ->\n'
    '            row.localBookingId?.let { bookingId ->\n'
    '                store.bookingsFor(selectedTrip.id).firstOrNull { candidate ->\n'
    '                    candidate.id == bookingId &&\n'
    '                        candidate.source in setOf(BookingSource.PRIVATE, BookingSource.OTHER) &&\n'
    '                        candidate.capacityClaimType == CapacityClaimType.PASSENGER &&\n'
    '                        candidate.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)\n'
    '                }\n'
    '            }\n'
    '        }\n'
    '        AlertDialog(\n'
    '            onDismissRequest = { cancelManualRow = null },\n'
    '            title = { Text("Cancelar passageiro desta viagem?") },\n'
    '            text = {\n'
    '                Text(\n'
    '                    "A reserva particular será cancelada na Agenda. Se a redução externa já estiver comprovada, o Rota Certa devolverá ${booking?.seats ?: row.seats} vaga(s) à mesma publicação BlaBlaCar e confirmará o número final antes de concluir.",\n'
    '                )\n'
    '            },\n'
    '            confirmButton = {\n'
    '                TextButton(\n'
    '                    enabled = currentTrip != null && booking != null,\n'
    '                    onClick = {\n'
    '                        val selectedTrip = currentTrip ?: return@TextButton\n'
    '                        val selectedBooking = booking ?: return@TextButton\n'
    '                        store.saveBooking(selectedBooking.copy(status = BookingStatus.CANCELLED))\n'
    '                        val cancellation = BlaBlaReliableSeatSyncBridge.onManualBookingCancelled(\n'
    '                            context = context,\n'
    '                            trip = selectedTrip,\n'
    '                            booking = selectedBooking,\n'
    '                            explicitTarget = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry),\n'
    '                        )\n'
    '                        UnifiedDebugEventStore.record(\n'
    '                            "AGENDA_MANUAL_PASSENGER_CANCELLED",\n'
    '                            context.packageName,\n'
    '                            "timeline=true seats=${selectedBooking.seats} shouldSync=${cancellation.shouldSync}",\n'
    '                        )\n'
    '                        cancelManualRow = null\n'
    '                        onChanged(cancellation.message)\n'
    '                        if (cancellation.shouldSync) onSyncExactCard?.invoke()\n'
    '                    },\n'
    '                ) { Text("Cancelar e devolver vaga(s)") }\n'
    '            },\n'
    '            dismissButton = { TextButton(onClick = { cancelManualRow = null }) { Text("Manter passageiro") } },\n'
    '        )\n'
    '    }\n\n'
    '    createProfileRow?.let { row ->\n',
    'manual cancellation confirmation and reliable reverse sync',
)

passenger_ui.write_text(ptext)


test_text = replace_once(
    test_text,
    '    @Test\n'
    '    fun verifiedCancellationAddsSeatBack() {\n'
    '        val decision = BlaBlaReliableSeatSyncPolicy.decide(\n'
    '            currentSeats = 3,\n'
    '            canAdd = true,\n'
    '            canRemove = true,\n'
    '            seatDelta = 1,\n'
    '            attempt = null,\n'
    '        )\n'
    '        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)\n'
    '        assertEquals(4, decision.targetSeats)\n'
    '    }\n',
    '    @Test\n'
    '    fun verifiedCancellationAddsSeatBack() {\n'
    '        val decision = BlaBlaReliableSeatSyncPolicy.decide(\n'
    '            currentSeats = 3,\n'
    '            canAdd = true,\n'
    '            canRemove = true,\n'
    '            seatDelta = 1,\n'
    '            attempt = null,\n'
    '        )\n'
    '        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)\n'
    '        assertEquals(4, decision.targetSeats)\n'
    '    }\n\n'
    '    @Test\n'
    '    fun threeSeatPrivateBookingReducesFourToOne() {\n'
    '        val decision = BlaBlaReliableSeatSyncPolicy.decide(\n'
    '            currentSeats = 4,\n'
    '            canAdd = true,\n'
    '            canRemove = true,\n'
    '            seatDelta = -3,\n'
    '            attempt = null,\n'
    '        )\n'
    '        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)\n'
    '        assertEquals(1, decision.targetSeats)\n'
    '    }\n\n'
    '    @Test\n'
    '    fun threeSeatCancellationRestoresOneToFour() {\n'
    '        val decision = BlaBlaReliableSeatSyncPolicy.decide(\n'
    '            currentSeats = 1,\n'
    '            canAdd = true,\n'
    '            canRemove = true,\n'
    '            seatDelta = 3,\n'
    '            attempt = null,\n'
    '        )\n'
    '        assertEquals(BlaBlaReliableSeatSyncAction.APPLY_TARGET, decision.action)\n'
    '        assertEquals(4, decision.targetSeats)\n'
    '    }\n',
    'explicit three-seat decrease and reverse tests',
)

seat_test.write_text(test_text)


old_version = '        versionCode = 5582\n        versionName = "0.1.289"'
new_version = '        versionCode = 5583\n        versionName = "0.1.290"'
if gradle_text.count(old_version) != 1:
    raise SystemExit('version baseline mismatch')
gradle.write_text(gradle_text.replace(old_version, new_version, 1))
