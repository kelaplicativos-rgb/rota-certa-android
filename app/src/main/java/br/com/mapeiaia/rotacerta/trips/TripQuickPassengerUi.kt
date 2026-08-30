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
    externalSeatTarget: BlaBlaManualSeatExternalTarget? = null,
    onSaved: (() -> Unit)? = null,
    showExistingPassengers: Boolean = true,
    initialPassenger: PassengerProfile? = null,
    lockPassengerIdentity: Boolean = false,
    requireConfirmation: Boolean = false,
    primaryActionLabel: String = "Adicionar passageiro",
) {
    val context = LocalContext.current
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    val passengerRepository = remember(context) { PassengerRepository(context) }
    val moneySpec = remember(context) { PassengerMoney.spec(context) }
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return
    val scope = rememberCoroutineScope()
    var name by remember(trip.id, initialPassenger?.id) { mutableStateOf(initialPassenger?.displayName.orEmpty()) }
    var contact by remember(trip.id, initialPassenger?.id) { mutableStateOf(initialPassenger?.whatsapp.orEmpty()) }
    var selectedPassengerId by remember(trip.id, initialPassenger?.id) { mutableStateOf(initialPassenger?.id.orEmpty()) }
    var fareText by remember(trip.id, initialPassenger?.id) { mutableStateOf("") }
    var seats by remember(trip.id) { mutableIntStateOf(1) }
    var fromStopId by remember(trip.id) { mutableStateOf<String?>(null) }
    var toStopId by remember(trip.id) { mutableStateOf<String?>(null) }
    var fromMenuOpen by remember(trip.id) { mutableStateOf(false) }
    var toMenuOpen by remember(trip.id) { mutableStateOf(false) }
    var busy by remember(trip.id, initialPassenger?.id) { mutableStateOf(false) }
    var error by remember(trip.id, initialPassenger?.id) { mutableStateOf<String?>(null) }
    var reviewPending by remember(trip.id, initialPassenger?.id) { mutableStateOf(false) }
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
    val passengerSuggestions = remember(name, contact, selectedPassengerId, lockPassengerIdentity) {
        if (lockPassengerIdentity || selectedPassengerId.isNotBlank()) emptyList()
        else passengerRepository.search(
            contact.takeIf { it.filter(Char::isDigit).length >= 4 } ?: name,
            6,
        )
    }
    val selectedProfile = selectedPassengerId.takeIf(String::isNotBlank)?.let(passengerStore::profile)
    val selectedPassengerBlocked = selectedProfile?.blocked == true

    HorizontalDivider()
    Text(if (lockPassengerIdentity) "Dados da reserva" else "Adicionar passageiro")
    if (lockPassengerIdentity) {
        Text("Passageiro: ${initialPassenger?.displayName ?: name}", style = MaterialTheme.typography.titleSmall)
        initialPassenger?.agendaAccessContact()?.takeIf(String::isNotBlank)?.let {
            Text("WhatsApp: ${formatPassengerContactForDisplay(it)}", style = MaterialTheme.typography.bodySmall)
        }
        if (selectedPassengerBlocked) {
            Text("⛔ NÃO ACEITO NO MEU CARRO — este passageiro não pode ser incluído.", style = MaterialTheme.typography.bodySmall)
        }
    } else {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                selectedPassengerId = ""
                reviewPending = false
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
                reviewPending = false
            },
            label = { Text("WhatsApp / telefone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (passengerSuggestions.isNotEmpty()) {
            Text("Passageiros já cadastrados", style = MaterialTheme.typography.bodySmall)
            passengerSuggestions.forEach { existing ->
                OutlinedButton(
                    onClick = {
                        selectedPassengerId = existing.id
                        name = existing.displayName
                        contact = existing.whatsapp
                        reviewPending = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Usar cadastro: ${existing.displayName}") }
            }
        }
        if (exactContactMatches.size == 1 && selectedPassengerId.isBlank()) {
            Text(
                "WhatsApp exato e único encontrado; este cadastro será reutilizado automaticamente.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (exactContactMatches.size > 1) {
            Text(
                "Há mais de um cadastro com esse contato. Selecione manualmente; nenhum será unido automaticamente.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (selectedPassengerId.isNotBlank()) {
            Text("✓ Cadastro de passageiro selecionado", style = MaterialTheme.typography.bodySmall)
        }
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
                        reviewPending = false
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
                        reviewPending = false
                        toMenuOpen = false
                    },
                )
            }
        }
    }

    OutlinedTextField(
        value = fareText,
        onValueChange = { fareText = it.take(32); reviewPending = false },
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
        OutlinedButton(onClick = { if (seats > 1) seats--; reviewPending = false }) { Text("−") }
        Text("$seats lugar(es)")
        OutlinedButton(onClick = { if (seats < trip.capacity) seats++; reviewPending = false }) { Text("+") }
    }
    when {
        !validSegment -> Text("Selecione embarque e destino para calcular as vagas deste trecho.")
        availability?.canBook == true -> Text("${availability.availableSeats} vaga(s) livre(s) neste trecho")
        availability != null -> Text("Sem vaga física neste trecho")
        else -> Text("Não foi possível calcular as vagas deste trecho.")
    }
    error?.let { Text(it) }

    val phoneValid = passengerContactKey(contact).isNotBlank()
    val canSubmit = !busy &&
        name.isNotBlank() &&
        phoneValid &&
        validSegment &&
        parsedFare != null &&
        availability?.canBook == true &&
        !selectedPassengerBlocked

    fun submitPassenger() {
        error = null
        val boarding = fromStopId ?: return
        val dropoff = toStopId ?: return
        val fare = parsedFare ?: return
        if (!phoneValid) {
            error = "Informe um WhatsApp/telefone válido."
            return
        }
        if (selectedPassengerId.isBlank() && exactContactMatches.size > 1) {
            error = "Há mais de um cadastro com esse WhatsApp. Selecione o passageiro correto."
            return
        }
        val canonicalPassengerId = selectedPassengerId.ifBlank {
            exactContactMatches.singleOrNull()?.id.orEmpty()
        }
        if (canonicalPassengerId.isNotBlank() && passengerStore.profile(canonicalPassengerId)?.blocked == true) {
            error = "⛔ Passageiro marcado como NÃO ACEITO NO MEU CARRO."
            reviewPending = false
            return
        }
        if (QuickPassengerEngine.hasActivePassengerBooking(bookings, canonicalPassengerId)) {
            AgendaTrace.event(
                context,
                "PASSENGER_ALREADY_IN_TRIP",
                "passengerHash=${passengerDebugIdentityHash(canonicalPassengerId)} tripHash=${passengerDebugIdentityHash(trip.id)}",
            )
            error = "Este passageiro já está vinculado a esta viagem."
            reviewPending = false
            return
        }
        val request = QuickPassengerRequest(
            passengerName = name,
            passengerContact = contact,
            passengerId = canonicalPassengerId,
            boardingStopId = boarding,
            dropoffStopId = dropoff,
            seats = seats,
            fareMinorUnits = fare,
            fareCurrencyCode = moneySpec.currencyCode,
            source = BookingSource.PRIVATE,
        )
        val plan = runCatching { QuickPassengerEngine.build(trip, bookings, request) }
            .onFailure { error = it.message ?: "Sem vaga para incluir este passageiro." }
            .getOrNull() ?: return
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
                AgendaTrace.event(
                    context,
                    "PASSENGER_ADDED_TO_TRIP",
                    "passengerHash=${passengerDebugIdentityHash(plan.passenger.passengerId)} tripHash=${passengerDebugIdentityHash(trip.id)}",
                )
                name = ""
                contact = ""
                selectedPassengerId = ""
                fareText = ""
                seats = 1
                fromStopId = null
                toStopId = null
                reviewPending = false
                onChanged("Passageiro adicionado à viagem. Ocupação física por trecho recalculada.")
                onBlaBlaSyncRequested?.invoke()
                onSaved?.invoke()
            }.onFailure { error = "Não foi possível salvar: ${it.message}" }
            busy = false
        }
    }

    if (requireConfirmation && reviewPending) {
        val boardingLabel = stops.firstOrNull { it.id == fromStopId }?.name.orEmpty()
        val destinationLabel = stops.firstOrNull { it.id == toStopId }?.name.orEmpty()
        val departureLabel = java.time.Instant.ofEpochMilli(trip.departureAtMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        HorizontalDivider()
        Text("Confirmar inclusão", style = MaterialTheme.typography.titleSmall)
        Text("Passageiro: $name")
        Text("Viagem: ${trip.title}")
        Text("Data/hora: $departureLabel")
        Text("Embarque: $boardingLabel")
        Text("Destino: $destinationLabel")
        Text("Vagas: $seats")
        Text("Valor: ${fareText.trim()} ${moneySpec.currencyCode}".trim())
        Button(
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            onClick = { submitPassenger() },
        ) { Text(if (busy) "Salvando…" else primaryActionLabel) }
        TextButton(
            enabled = !busy,
            onClick = { reviewPending = false },
        ) { Text("Editar dados") }
    } else {
        Button(
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (requireConfirmation) reviewPending = true
                else submitPassenger()
            },
        ) {
            Text(
                when {
                    busy -> "Salvando…"
                    requireConfirmation -> "Revisar inclusão"
                    else -> primaryActionLabel
                },
            )
        }
    }

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
                                    onChanged("Passageiro manual cancelado. Ocupação física por trecho recalculada.")
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
