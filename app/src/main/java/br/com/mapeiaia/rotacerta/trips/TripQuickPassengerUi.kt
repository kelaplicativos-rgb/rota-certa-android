package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
) {
    val context = LocalContext.current
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return
    val scope = rememberCoroutineScope()
    var name by remember(trip.id) { mutableStateOf("") }
    var contact by remember(trip.id) { mutableStateOf("") }
    var seats by remember(trip.id) { mutableIntStateOf(1) }
    var fromIndex by remember(trip.id) { mutableIntStateOf(0) }
    var toIndex by remember(trip.id) { mutableIntStateOf(stops.lastIndex) }
    var busy by remember(trip.id) { mutableStateOf(false) }
    var error by remember(trip.id) { mutableStateOf<String?>(null) }
    val bookings = store.bookingsFor(trip.id)
    val availability = runCatching { SeatAvailabilityEngine.availability(trip, bookings, stops[fromIndex].id, stops[toIndex].id, seats) }.getOrNull()

    HorizontalDivider()
    Text("Adicionar passageiro particular")
    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(contact, { contact = it }, label = { Text("WhatsApp (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    ResponsiveTripActions(listOf(
        ResponsiveTripAction("Embarque: ${stops[fromIndex].name}") {
            fromIndex = (fromIndex + 1).coerceAtMost(stops.lastIndex - 1)
            if (toIndex <= fromIndex) toIndex = fromIndex + 1
        },
        ResponsiveTripAction("Destino: ${stops[toIndex].name}") {
            toIndex++
            if (toIndex > stops.lastIndex) toIndex = fromIndex + 1
        },
    ))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { if (seats > 1) seats-- }) { Text("−") }
        Text("$seats vaga(s)")
        OutlinedButton(onClick = { if (seats < trip.capacity) seats++ }) { Text("+") }
    }
    val free = availability?.availableSeats ?: 0
    Text(if (availability?.canBook == true) "$free vaga(s) livre(s) neste trecho" else "Sem vaga física neste trecho")
    error?.let { Text(it) }
    Button(
        enabled = !busy && name.isNotBlank() && availability?.canBook == true,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            error = null
            val request = QuickPassengerRequest(
                passengerName = name,
                passengerContact = contact,
                boardingStopId = stops[fromIndex].id,
                dropoffStopId = stops[toIndex].id,
                seats = seats,
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
                }.onSuccess {
                    val external = if (onBlaBlaSyncRequested != null) {
                        BlaBlaManualSeatSyncCoordinator.enqueueForManualBooking(
                            context = context,
                            trip = trip,
                            booking = plan.passenger,
                            seatDelta = -plan.passenger.seats,
                        )
                    } else null
                    name = ""; contact = ""; seats = 1
                    onChanged(
                        if (external != null) {
                            "Passageiro particular adicionado. Ocupação interna recalculada • sincronizando ${plan.passenger.seats} vaga(s) no BlaBlaCar…"
                        } else {
                            "Passageiro particular adicionado. Ocupação interna recalculada • sincronização externa pendente ⚠️"
                        },
                    )
                    onBlaBlaSyncRequested?.invoke()
                }.onFailure { error = "Não foi possível salvar: ${it.message}" }
                busy = false
            }
        },
    ) { Text(if (busy) "Salvando…" else "Adicionar passageiro") }

    val active = bookings.filter { it.capacityClaimType == CapacityClaimType.PASSENGER && (it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD) }
    if (active.isNotEmpty()) {
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
                                    val external = if (onBlaBlaSyncRequested != null) {
                                        BlaBlaManualSeatSyncCoordinator.enqueueForManualBooking(
                                            context = context,
                                            trip = trip,
                                            booking = booking,
                                            seatDelta = booking.seats,
                                        )
                                    } else null
                                    onChanged(
                                        if (external != null) {
                                            "Passageiro manual removido. Vaga interna liberada • devolvendo ${booking.seats} vaga(s) ao BlaBlaCar…"
                                        } else {
                                            "Passageiro manual removido. Vaga interna liberada • sincronização externa pendente ⚠️"
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
