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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun QuickPassengerPanel(
    trip: Trip,
    store: TripStore,
    onChanged: (String) -> Unit,
) {
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return
    val scope = rememberCoroutineScope()
    val sources = remember { listOf(BookingSource.PRIVATE, BookingSource.BLABLACAR, BookingSource.ROTA_CERTA, BookingSource.OTHER) }
    var name by remember(trip.id) { mutableStateOf("") }
    var contact by remember(trip.id) { mutableStateOf("") }
    var seatsText by remember(trip.id) { mutableStateOf("1") }
    var fromIndex by remember(trip.id) { mutableStateOf(0) }
    var toIndex by remember(trip.id) { mutableStateOf(stops.lastIndex) }
    var sourceIndex by remember(trip.id) { mutableStateOf(0) }
    var mirrorSource by remember(trip.id) { mutableStateOf<BookingSource?>(null) }
    var linkIndex by remember(trip.id) { mutableStateOf(-1) }
    var busy by remember(trip.id) { mutableStateOf(false) }
    var error by remember(trip.id) { mutableStateOf<String?>(null) }

    val currentBookings = store.bookingsFor(trip.id)
    val reservedLinks = QuickPassengerEngine.activeReservedSeatLinks(currentBookings)
    if (linkIndex >= reservedLinks.size) linkIndex = -1
    val source = sources[sourceIndex]
    val seats = seatsText.toIntOrNull()?.coerceIn(1, trip.capacity) ?: 1
    val availability = runCatching {
        SeatAvailabilityEngine.availability(
            trip,
            currentBookings,
            stops[fromIndex].id,
            stops[toIndex].id,
            seats,
        )
    }.getOrNull()

    HorizontalDivider()
    Text("Passageiro rápido")
    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(contact, { contact = it }, label = { Text("Contato opcional") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            sourceIndex = (sourceIndex + 1) % sources.size
            if (mirrorSource == sources[sourceIndex]) mirrorSource = null
        }) { Text("Origem: ${quickSourceLabel(source)}") }
        OutlinedButton(onClick = {
            val candidates = sources.filter { it != source }
            mirrorSource = when (val current = mirrorSource) {
                null -> candidates.firstOrNull()
                else -> {
                    val index = candidates.indexOf(current)
                    if (index < 0 || index == candidates.lastIndex) null else candidates[index + 1]
                }
            }
            if (mirrorSource != null) linkIndex = -1
        }) { Text("Espelho: ${mirrorSource?.let(::quickSourceLabel) ?: "Nenhum"}") }
    }
    if (reservedLinks.isNotEmpty()) {
        OutlinedButton(onClick = {
            linkIndex = if (linkIndex < reservedLinks.lastIndex) linkIndex + 1 else -1
            if (linkIndex >= 0) mirrorSource = null
        }) {
            val linked = reservedLinks.getOrNull(linkIndex)
            Text(if (linked == null) "Vincular vaga existente: não" else "Vincular: ${quickSourceLabel(linked.source)} ${linked.seats} vaga(s)")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            fromIndex = (fromIndex + 1).coerceAtMost(stops.lastIndex - 1)
            if (toIndex <= fromIndex) toIndex = fromIndex + 1
        }) { Text("Embarque: ${stops[fromIndex].name}") }
        OutlinedButton(onClick = {
            toIndex++
            if (toIndex > stops.lastIndex) toIndex = fromIndex + 1
        }) { Text("Desce: ${stops[toIndex].name}") }
    }
    OutlinedTextField(
        seatsText,
        { seatsText = it.filter(Char::isDigit).take(3) },
        label = { Text("Vagas") },
    )
    Text("Disponíveis nesse trecho: ${availability?.availableSeats ?: 0}")
    error?.let { Text(it) }
    Button(
        enabled = !busy && name.isNotBlank() && availability?.canBook == true,
        onClick = {
            error = null
            val request = QuickPassengerRequest(
                passengerName = name,
                passengerContact = contact,
                boardingStopId = stops[fromIndex].id,
                dropoffStopId = stops[toIndex].id,
                seats = seats,
                source = source,
                mirrorSource = mirrorSource,
                linkReservedSeatBookingId = reservedLinks.getOrNull(linkIndex)?.id,
            )
            val plan = runCatching { QuickPassengerEngine.build(trip, currentBookings, request) }
                .onFailure { error = it.message ?: "Não foi possível incluir o passageiro." }
                .getOrNull() ?: return@Button
            busy = true
            scope.launch {
                val settings = store.onlineSettings()
                val remoteTripId = trip.remoteId
                val syncOnline = settings.configured && remoteTripId != null
                val ordered = buildList {
                    plan.linkedReservedSeatUpdate?.let(::add)
                    add(plan.passenger)
                    plan.mirror?.let(::add)
                }
                var saved = 0
                runCatching {
                    ordered.forEach { booking ->
                        if (syncOnline) TripRemoteApi(settings).upsertDriverBooking(remoteTripId!!, booking)
                        store.saveBooking(booking)
                        saved++
                    }
                }.onSuccess {
                    name = ""
                    contact = ""
                    seatsText = "1"
                    mirrorSource = null
                    linkIndex = -1
                    onChanged(if (syncOnline) "Passageiro e vagas conciliados com a agenda online." else "Passageiro adicionado e vagas conciliadas.")
                }.onFailure {
                    error = if (saved > 0) {
                        "Conciliação parcial ($saved/${ordered.size}). Revise antes de liberar novas vagas: ${it.message}"
                    } else {
                        "Não foi possível conciliar: ${it.message}"
                    }
                    if (saved > 0) onChanged("Conciliação parcial; revise a ocupação antes de aceitar outra reserva.")
                }
                busy = false
            }
        },
    ) { Text(if (busy) "Salvando…" else "+ Passageiro") }

    val active = currentBookings.filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD }
    if (active.isNotEmpty()) {
        HorizontalDivider()
        Text("Ocupação registrada")
        active.forEach { booking ->
            val from = stops.firstOrNull { it.id == booking.boardingStopId }?.name.orEmpty()
            val to = stops.firstOrNull { it.id == booking.dropoffStopId }?.name.orEmpty()
            val kind = if (booking.capacityClaimType == CapacityClaimType.RESERVED_SEAT) "vaga reservada" else "passageiro"
            Column {
                Text("${quickSourceLabel(booking.source)} • $kind • ${booking.seats} • $from → $to")
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (booking.source == BookingSource.ROTA_CERTA && trip.remoteId != null && store.onlineSettings().configured) {
                            error = "Reserva pública do Rota Certa deve ser cancelada pelo fluxo próprio da reserva."
                            return@TextButton
                        }
                        busy = true
                        scope.launch {
                            val cancelled = booking.copy(status = BookingStatus.CANCELLED)
                            val settings = store.onlineSettings()
                            runCatching {
                                if (settings.configured && trip.remoteId != null) {
                                    TripRemoteApi(settings).upsertDriverBooking(trip.remoteId, cancelled)
                                }
                                store.saveBooking(cancelled)
                            }.onSuccess {
                                onChanged("Vaga liberada e ocupação recalculada.")
                            }.onFailure {
                                error = "Não foi possível liberar a vaga: ${it.message}"
                            }
                            busy = false
                        }
                    },
                ) { Text("Cancelar/liberar") }
            }
        }
    }
}

private fun quickSourceLabel(source: BookingSource): String = when (source) {
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.OTHER -> "Outro"
}
