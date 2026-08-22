package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun QuickPassengerPanel(
    trip: Trip,
    store: TripStore,
    onChanged: (String) -> Unit,
    onBlaBlaSyncRequested: (() -> Unit)? = null,
    onSaved: (() -> Unit)? = null,
    showExistingPassengers: Boolean = true,
) {
    val context = LocalContext.current
    val externalSeatLedger = remember(context) { BlaBlaManualSeatSyncLedger(context) }
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    val moneySpec = remember(context) { PassengerMoney.spec(context) }
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return
    val scope = rememberCoroutineScope()
    var name by remember(trip.id) { mutableStateOf("") }
    var contact by remember(trip.id) { mutableStateOf("") }
    var selectedPassengerId by remember(trip.id) { mutableStateOf("") }
    var fareText by remember(trip.id) { mutableStateOf("") }
    var seats by remember(trip.id) { mutableIntStateOf(1) }
    var fromStopId by remember(trip.id) { mutableStateOf<String?>(null) }
    var toStopId by remember(trip.id) { mutableStateOf<String?>(null) }
    var fromMenuOpen by remember(trip.id) { mutableStateOf(false) }
    var toMenuOpen by remember(trip.id) { mutableStateOf(false) }
    var busy by remember(trip.id) { mutableStateOf(false) }
    var error by remember(trip.id) { mutableStateOf<String?>(null) }
    val bookings = store.bookingsFor(trip.id)
    val fromIndex = stops.indexOfFirst { it.id == fromStopId }
    val toIndex = stops.indexOfFirst { it.id == toStopId }
    val validSegment = fromIndex >= 0 && toIndex > fromIndex
    val availability = if (validSegment) {
        runCatching {
            SeatAvailabilityEngine.availability(
                trip,
                bookings,
                stops[fromIndex].id,
                stops[toIndex].id,
                seats,
            )
        }.getOrNull()
    } else null
    val parsedFare = PassengerMoney.parseMinorUnits(fareText, moneySpec)
    val exactContactMatches = remember(contact, selectedPassengerId) {
        if (selectedPassengerId.isBlank()) passengerStore.exactContactMatches(contact) else emptyList()
    }

    HorizontalDivider()
    Text("Adicionar passageiro")
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            selectedPassengerId = ""
        },
        label = { Text("Nome") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = contact,
        onValueChange = {
            contact = it
            selectedPassengerId = ""
        },
        label = { Text("WhatsApp / telefone") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (exactContactMatches.size == 1) {
        val existing = exactContactMatches.single()
        Text(
            "Cadastro existente encontrado. Só será reutilizado se você confirmar.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = {
                selectedPassengerId = existing.id
                name = existing.displayName
                contact = existing.whatsapp
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Usar cadastro: ${existing.displayName}") }
    } else if (exactContactMatches.size > 1) {
        Text(
            "Há mais de um cadastro com esse contato. Nenhum será unido automaticamente.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (selectedPassengerId.isNotBlank()) {
        Text("✓ Cadastro de passageiro selecionado", style = MaterialTheme.typography.bodySmall)
    }

    Column {
        OutlinedButton(onClick = { fromMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text(fromStopId?.let { id -> "Embarque: ${stops.first { it.id == id }.name}" } ?: "Selecionar embarque…")
        }
        DropdownMenu(expanded = fromMenuOpen, onDismissRequest = { fromMenuOpen = false }) {
            stops.dropLast(1).forEach { stop ->
                DropdownMenuItem(
                    text = { Text(stop.name) },
                    onClick = {
                        fromStopId = stop.id
                        val selectedIndex = stops.indexOf(stop)
                        if (toIndex <= selectedIndex) toStopId = null
                        fromMenuOpen = false
                    },
                )
            }
        }
    }
    Column {
        OutlinedButton(
            enabled = fromIndex >= 0,
            onClick = { toMenuOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(toStopId?.let { id -> "Destino: ${stops.first { it.id == id }.name}" } ?: "Selecionar destino…")
        }
        DropdownMenu(expanded = toMenuOpen, onDismissRequest = { toMenuOpen = false }) {
            stops.filterIndexed { index, _ -> fromIndex >= 0 && index > fromIndex }.forEach { stop ->
                DropdownMenuItem(
                    text = { Text(stop.name) },
                    onClick = {
                        toStopId = stop.id
                        toMenuOpen = false
                    },
                )
            }
        }
    }

    OutlinedTextField(
        value = fareText,
        onValueChange = { fareText = it.take(32) },
        label = {
            Text(
                if (moneySpec.currencyCode.isBlank()) "Valor do trecho" else "Valor do trecho (${moneySpec.currencyCode})",
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (fareText.isNotBlank() && parsedFare == null) {
        Text("Informe um valor positivo válido.", style = MaterialTheme.typography.bodySmall)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { if (seats > 1) seats-- }) { Text("−") }
        Text("$seats lugar(es)")
        OutlinedButton(onClick = { if (seats < trip.capacity) seats++ }) { Text("+") }
    }
    when {
        !validSegment -> Text("Selecione embarque e destino para calcular as vagas deste trecho.")
        availability?.canBook == true -> Text("${availability.availableSeats} vaga(s) livre(s) neste trecho")
        availability != null -> Text("Sem vaga física neste trecho")
        else -> Text("Não foi possível calcular as vagas deste trecho.")
    }
    error?.let { Text(it) }

    val phoneValid = passengerContactKey(contact).isNotBlank()
    Button(
        enabled = !busy &&
            name.isNotBlank() &&
            phoneValid &&
            validSegment &&
            parsedFare != null &&
            availability?.canBook == true,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            error = null
            val boarding = fromStopId ?: return@Button
            val dropoff = toStopId ?: return@Button
            val fare = parsedFare ?: return@Button
            if (!phoneValid) {
                error = "Informe um WhatsApp/telefone válido."
                return@Button
            }
            val request = QuickPassengerRequest(
                passengerName = name,
                passengerContact = contact,
                passengerId = selectedPassengerId,
                boardingStopId = boarding,
                dropoffStopId = dropoff,
                seats = seats,
                fareMinorUnits = fare,
                fareCurrencyCode = moneySpec.currencyCode,
                source = BookingSource.PRIVATE,
            )
            val plan = runCatching { QuickPassengerEngine.build(trip, bookings, request) }
                .onFailure { error = it.message ?: "Sem vaga para incluir este passageiro." }
                .getOrNull() ?: return@Button
            busy = true
            scope.launch {
                val settings = store.onlineSettings()
                val remoteTripId = trip.remoteId
                val syncOnline = settings.configured && remoteTripId != null
                runCatching {
                    if (syncOnline) TripRemoteApi(settings).upsertDriverBooking(remoteTripId!!, plan.passenger)
                    store.saveBooking(plan.passenger)
                    val existingProfile = passengerStore.profile(plan.passenger.passengerId)
                    passengerStore.saveProfile(
                        (existingProfile ?: PassengerProfile(
                            id = plan.passenger.passengerId,
                            displayName = plan.passenger.passengerName,
                            whatsapp = plan.passenger.passengerContact,
                            createdAtMillis = plan.passenger.createdAtMillis,
                        )).copy(
                            displayName = plan.passenger.passengerName,
                            whatsapp = plan.passenger.passengerContact,
                        ),
                    )
                }.onSuccess {
                    val external = if (onBlaBlaSyncRequested != null) {
                        BlaBlaManualSeatSyncCoordinator.enqueueForManualBooking(
                            context = context,
                            trip = trip,
                            booking = plan.passenger,
                            seatDelta = -plan.passenger.seats,
                        )
                    } else null
                    name = ""
                    contact = ""
                    selectedPassengerId = ""
                    fareText = ""
                    seats = 1
                    fromStopId = null
                    toStopId = null
                    onChanged(
                        if (external != null) {
                            "Passageiro particular adicionado. Ocupação interna recalculada • sincronizando ${plan.passenger.seats} vaga(s) no BlaBlaCar…"
                        } else {
                            "Passageiro particular adicionado. Ocupação interna recalculada."
                        },
                    )
                    onBlaBlaSyncRequested?.invoke()
                    onSaved?.invoke()
                }.onFailure { error = "Não foi possível salvar: ${it.message}" }
                busy = false
            }
        },
    ) { Text(if (busy) "Salvando…" else "Adicionar passageiro") }

    if (!phoneValid && contact.isNotBlank()) {
        Text("Telefone ainda inválido; nenhum cadastro será criado.", style = MaterialTheme.typography.bodySmall)
    }

    val active = bookings.filter {
        it.capacityClaimType == CapacityClaimType.PASSENGER &&
            (it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD)
    }
    if (showExistingPassengers && active.isNotEmpty()) {
        HorizontalDivider()
        Text("Particulares (${active.sumOf(Booking::seats)})")
        active.forEach { booking ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${booking.passengerName} • ${booking.seats}")
                if (booking.source == BookingSource.PRIVATE || booking.source == BookingSource.OTHER) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            runCatching { store.saveBooking(booking.copy(status = BookingStatus.CANCELLED)) }
                                .onSuccess {
                                    val priorExternalDecreaseVerified = externalSeatLedger.canReverse(booking.id, booking.seats)
                                    val external = if (onBlaBlaSyncRequested != null && priorExternalDecreaseVerified) {
                                        BlaBlaManualSeatSyncCoordinator.enqueueForManualBooking(
                                            context = context,
                                            trip = trip,
                                            booking = booking,
                                            seatDelta = booking.seats,
                                        )
                                    } else null
                                    onChanged(
                                        when {
                                            external != null -> "Passageiro manual removido. Vaga interna liberada • devolvendo ${booking.seats} vaga(s) ao BlaBlaCar…"
                                            priorExternalDecreaseVerified -> "Passageiro manual removido. Vaga interna liberada • sincronização externa pendente ⚠️"
                                            else -> "Passageiro manual removido. Vaga interna liberada • nenhuma vaga externa foi devolvida porque não havia redução externa confirmada."
                                        },
                                    )
                                    onBlaBlaSyncRequested?.invoke()
                                }
                                .onFailure { error = "Não foi possível liberar a vaga: ${it.message}" }
                            busy = false
                        }
                    }) { Text("Remover") }
                }
            }
        }
    }
}
