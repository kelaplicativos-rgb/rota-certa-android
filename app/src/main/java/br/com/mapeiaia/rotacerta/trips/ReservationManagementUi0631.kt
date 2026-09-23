package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class ReservationFilter0631 {
    PENDING,
    ACTIVE,
    HISTORY,
}

internal data class ManagedReservation0631(
    val booking: Booking,
    val trip: Trip,
    val boarding: TripStop?,
    val dropoff: TripStop?,
)

internal fun reservationIsResolved0631(booking: Booking): Boolean =
    booking.status in setOf(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.EXPIRED) ||
        booking.operationalStatus in setOf(PassengerOperationalStatus.COMPLETED, PassengerOperationalStatus.CANCELLED)

internal fun managedReservations0631(
    trips: List<Trip>,
    bookings: List<Booking>,
    filter: ReservationFilter0631,
    queryRaw: String = "",
    focusedBookingId: String? = null,
): List<ManagedReservation0631> {
    val tripById = trips.associateBy(Trip::id)
    val query = queryRaw.trim().lowercase(Locale.getDefault())
    return bookings.asSequence()
        .filter { it.capacityClaimType == CapacityClaimType.PASSENGER }
        .filter { focusedBookingId.isNullOrBlank() || it.id == focusedBookingId }
        .filter { booking ->
            when (filter) {
                ReservationFilter0631.PENDING -> booking.status == BookingStatus.REQUESTED
                ReservationFilter0631.ACTIVE ->
                    booking.status in setOf(BookingStatus.HELD, BookingStatus.CONFIRMED) &&
                        !reservationIsResolved0631(booking)
                ReservationFilter0631.HISTORY -> reservationIsResolved0631(booking)
            }
        }
        .mapNotNull { booking ->
            val trip = tripById[booking.tripId] ?: return@mapNotNull null
            val stops = trip.stops.associateBy(TripStop::id)
            ManagedReservation0631(
                booking = booking,
                trip = trip,
                boarding = stops[booking.boardingStopId],
                dropoff = stops[booking.dropoffStopId],
            )
        }
        .filter { row ->
            query.isBlank() ||
                row.booking.passengerName.lowercase(Locale.getDefault()).contains(query) ||
                row.booking.passengerContact.lowercase(Locale.getDefault()).contains(query) ||
                row.trip.title.lowercase(Locale.getDefault()).contains(query) ||
                row.boarding?.name.orEmpty().lowercase(Locale.getDefault()).contains(query) ||
                row.dropoff?.name.orEmpty().lowercase(Locale.getDefault()).contains(query)
        }
        .sortedWith(
            compareBy<ManagedReservation0631>(
                { if (it.booking.status == BookingStatus.REQUESTED) 0 else 1 },
                { it.trip.departureAtMillis },
                { it.booking.createdAtMillis },
            ),
        )
        .toList()
}

internal fun reservationEditedBooking0631(
    trip: Trip,
    allBookings: List<Booking>,
    current: Booking,
    boardingStopId: String,
    dropoffStopId: String,
    seats: Int,
    fareMinorUnits: Long?,
    fareCurrencyCode: String,
): Booking {
    require(current.tripId == trip.id) { "BOOKING_TRIP_ID_MISMATCH" }
    require(current.status !in setOf(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
        "RESERVATION_INACTIVE"
    }
    require(current.operationalStatus != PassengerOperationalStatus.COMPLETED) { "RESERVATION_COMPLETED" }
    require(seats in 1..99) { "INVALID_SEAT_COUNT" }

    val stops = trip.stops.sortedBy(TripStop::order)
    val fromIndex = stops.indexOfFirst { it.id == boardingStopId }
    val toIndex = stops.indexOfFirst { it.id == dropoffStopId }
    require(fromIndex >= 0 && toIndex > fromIndex) { "INVALID_RESERVATION_SEGMENT" }

    val otherClaims = allBookings.filterNot { it.id == current.id }
    val availability = SeatAvailabilityEngine.availability(
        trip = trip,
        bookings = otherClaims,
        boardingStopId = boardingStopId,
        dropoffStopId = dropoffStopId,
        requestedSeats = seats,
    )
    require(availability.canBook) {
        "Trecho sem vagas suficientes: disponíveis=${availability.availableSeats}, solicitadas=$seats"
    }

    return current.copy(
        boardingStopId = boardingStopId,
        dropoffStopId = dropoffStopId,
        seats = seats,
        fareMinorUnits = fareMinorUnits,
        fareCurrencyCode = fareCurrencyCode.takeIf { fareMinorUnits != null }.orEmpty(),
        localMetadataTouched = true,
    )
}

internal fun reservationStatusLabel0631(booking: Booking): String = when {
    booking.status == BookingStatus.REQUESTED -> "Aguardando aprovação"
    booking.status == BookingStatus.REJECTED -> "Recusada"
    booking.status == BookingStatus.CANCELLED || booking.operationalStatus == PassengerOperationalStatus.CANCELLED -> "Cancelada"
    booking.status == BookingStatus.EXPIRED -> "Expirada"
    booking.operationalStatus == PassengerOperationalStatus.COMPLETED -> "Concluída"
    booking.paymentStatus == PassengerPaymentStatus.PAID -> "Pago"
    booking.operationalStatus == PassengerOperationalStatus.AT_LOCATION -> "No local"
    booking.operationalStatus == PassengerOperationalStatus.IN_CAR -> "No carro"
    else -> "Confirmada"
}

private fun reservationSourceLabel0631(source: BookingSource): String = when (source) {
    BookingSource.ROTA_CERTA -> "Viagem Certa"
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.OTHER -> "Manual"
}

@Composable
internal fun ReservationManagementScreen0631(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    initialBookingId: String? = null,
    initialPendingOnly: Boolean = false,
    onChanged: (String) -> Unit,
    onOpenTimeline: (tripId: String, bookingId: String) -> Unit,
) {
    val context = LocalContext.current
    val mutationCoordinator = remember(context, store) { TripMutationCoordinator0387(context, store) }
    val moneySpec = remember(context) { PassengerMoney.spec(context) }
    val pendingCount = bookings.count {
        it.capacityClaimType == CapacityClaimType.PASSENGER && it.status == BookingStatus.REQUESTED
    }
    val activeCount = bookings.count {
        it.capacityClaimType == CapacityClaimType.PASSENGER &&
            it.status in setOf(BookingStatus.HELD, BookingStatus.CONFIRMED) &&
            !reservationIsResolved0631(it)
    }
    val historyCount = bookings.count {
        it.capacityClaimType == CapacityClaimType.PASSENGER && reservationIsResolved0631(it)
    }

    var focusedBookingId by rememberSaveable(initialBookingId) { mutableStateOf(initialBookingId) }
    var filterName by rememberSaveable(initialBookingId, initialPendingOnly) {
        mutableStateOf(
            if (!initialBookingId.isNullOrBlank() || initialPendingOnly || pendingCount > 0) {
                ReservationFilter0631.PENDING.name
            } else {
                ReservationFilter0631.ACTIVE.name
            },
        )
    }
    val filter = runCatching { ReservationFilter0631.valueOf(filterName) }.getOrDefault(ReservationFilter0631.ACTIVE)
    var query by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var rejectingId by rememberSaveable { mutableStateOf<String?>(null) }
    var cancellingId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMenuId by rememberSaveable { mutableStateOf<String?>(null) }
    var busyId by rememberSaveable { mutableStateOf<String?>(null) }

    val rows = remember(trips, bookings, filter, query, focusedBookingId) {
        managedReservations0631(
            trips = trips,
            bookings = bookings,
            filter = filter,
            queryRaw = query,
            focusedBookingId = focusedBookingId,
        )
    }

    Text(
        "Reservas ligadas às viagens canônicas. Cancelar preserva o histórico e libera as vagas do trecho; nenhuma ação interna altera a BlaBlaCar silenciosamente.",
        style = MaterialTheme.typography.bodySmall,
    )

    if (focusedBookingId != null) {
        TextButton(onClick = { focusedBookingId = null }) {
            Text("← Ver todas as reservas")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fun select(target: ReservationFilter0631) {
            focusedBookingId = null
            filterName = target.name
        }
        if (filter == ReservationFilter0631.PENDING) {
            Button(onClick = { select(ReservationFilter0631.PENDING) }) { Text("Pendentes ($pendingCount)") }
        } else {
            OutlinedButton(onClick = { select(ReservationFilter0631.PENDING) }) { Text("Pendentes ($pendingCount)") }
        }
        if (filter == ReservationFilter0631.ACTIVE) {
            Button(onClick = { select(ReservationFilter0631.ACTIVE) }) { Text("Ativas ($activeCount)") }
        } else {
            OutlinedButton(onClick = { select(ReservationFilter0631.ACTIVE) }) { Text("Ativas ($activeCount)") }
        }
        if (filter == ReservationFilter0631.HISTORY) {
            Button(onClick = { select(ReservationFilter0631.HISTORY) }) { Text("Histórico ($historyCount)") }
        } else {
            OutlinedButton(onClick = { select(ReservationFilter0631.HISTORY) }) { Text("Histórico ($historyCount)") }
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it.take(100) },
        label = { Text("Buscar passageiro, viagem ou trecho") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    if (rows.isEmpty()) {
        Text(
            when (filter) {
                ReservationFilter0631.PENDING -> "Nenhuma solicitação pendente."
                ReservationFilter0631.ACTIVE -> "Nenhuma reserva ativa."
                ReservationFilter0631.HISTORY -> "Nenhuma reserva no histórico com este filtro."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    val visibleRows = if (filter == ReservationFilter0631.HISTORY && query.isBlank() && focusedBookingId == null) {
        rows.take(100)
    } else {
        rows
    }
    visibleRows.forEach { row ->
        val booking = row.booking
        val trip = row.trip
        val date = remember(trip.departureAtMillis) {
            DateTimeFormatter.ofPattern("EEE, dd/MM • HH:mm", Locale("pt", "BR"))
                .format(Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault()))
        }
        val fare = booking.fareMinorUnits?.let {
            PassengerMoney.formatMinorUnits(
                amountMinorUnits = it,
                currencyCode = booking.fareCurrencyCode.ifBlank { moneySpec.currencyCode },
                localeTag = moneySpec.localeTag,
            )
        } ?: "valor não definido"
        val canDecide = booking.status == BookingStatus.REQUESTED &&
            booking.source == BookingSource.ROTA_CERTA &&
            booking.capacityClaimType == CapacityClaimType.PASSENGER
        val canOperate = booking.status in setOf(BookingStatus.HELD, BookingStatus.CONFIRMED) &&
            booking.operationalStatus !in setOf(PassengerOperationalStatus.COMPLETED, PassengerOperationalStatus.CANCELLED)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        booking.passengerName.ifBlank { "Passageiro" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(reservationStatusLabel0631(booking), style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "${row.boarding?.name ?: "Embarque"} → ${row.dropoff?.name ?: "Destino"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("$date • ${booking.seats} lugar(es) • $fare", style = MaterialTheme.typography.bodySmall)
                Text("${reservationSourceLabel0631(booking.source)} • ${trip.title}", style = MaterialTheme.typography.bodySmall)

                if (canDecide) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = busyId == null,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                busyId = booking.id
                                runCatching {
                                    persistCanonicalPassengerMutation0582(
                                        context = context,
                                        trip = trip,
                                        updated = passengerDecisionMutation0582(booking, "APPROVE"),
                                        store = store,
                                        mutationCoordinator = mutationCoordinator,
                                        mutationType = "RESERVATION_APPROVED",
                                        mutationSource = "RESERVATION_MANAGEMENT_0631",
                                    )
                                }.onSuccess {
                                    UnifiedDebugEventStore.record(
                                        "RESERVATION_MANAGEMENT_DECISION_0631",
                                        context.packageName,
                                        "action=APPROVE bookingId=${passengerCancellationHash(booking.id)}",
                                    )
                                    onChanged("Reserva aprovada ✅")
                                }.onFailure { onChanged("Nada foi alterado: ${it.message ?: "falha ao aprovar"}") }
                                busyId = null
                            },
                        ) { Text(if (busyId == booking.id) "Aprovando…" else "Aceitar") }
                        OutlinedButton(
                            enabled = busyId == null,
                            modifier = Modifier.weight(1f),
                            onClick = { rejectingId = booking.id },
                        ) { Text("Recusar") }
                    }
                } else if (booking.status == BookingStatus.REQUESTED) {
                    Text(
                        "Esta solicitação veio de ${reservationSourceLabel0631(booking.source)} e não pode ser aprovada como se fosse uma reserva da Agenda.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        enabled = booking.passengerContact.isNotBlank(),
                        onClick = { openPassengerWhatsApp(context, booking.passengerContact) },
                    ) { Text("WhatsApp") }

                    if (!reservationIsResolved0631(booking)) {
                        OutlinedButton(onClick = { editingId = booking.id }) { Text("Editar") }
                    }

                    if (canOperate) {
                        Box {
                            OutlinedButton(onClick = { statusMenuId = booking.id }) {
                                Text(reservationStatusLabel0631(booking) + " ▼")
                            }
                            DropdownMenu(
                                expanded = statusMenuId == booking.id,
                                onDismissRequest = { statusMenuId = null },
                            ) {
                                listOf(
                                    "CONFIRMED" to "Confirmado",
                                    "AT_LOCATION" to "No local",
                                    "IN_CAR" to "No carro",
                                    "PAID" to "Pago",
                                    "COMPLETED" to "Concluído",
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            statusMenuId = null
                                            busyId = booking.id
                                            runCatching {
                                                persistCanonicalPassengerMutation0582(
                                                    context = context,
                                                    trip = trip,
                                                    updated = passengerOperationalMutation0582(booking, value),
                                                    store = store,
                                                    mutationCoordinator = mutationCoordinator,
                                                    mutationType = "PASSENGER_STATUS_$value",
                                                    mutationSource = "RESERVATION_MANAGEMENT_0631",
                                                )
                                            }.onSuccess {
                                                onChanged("Status atualizado no estado canônico.")
                                            }.onFailure {
                                                onChanged("Nada foi alterado: ${it.message ?: "falha ao alterar status"}")
                                            }
                                            busyId = null
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedButton(onClick = { cancellingId = booking.id }) { Text("Cancelar") }
                    }

                    OutlinedButton(onClick = { onOpenTimeline(trip.id, booking.id) }) { Text("Viagem") }
                    OutlinedButton(onClick = { addingTripId = trip.id }) { Text("+ Passageiro") }
                }
            }
        }
    }

    if (filter == ReservationFilter0631.HISTORY && rows.size > visibleRows.size) {
        Text(
            "Mostrando as 100 reservas mais próximas. Use a busca para localizar registros mais antigos.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    val editing = editingId?.let { id -> bookings.firstOrNull { it.id == id } }
    val editingTrip = editing?.let { current -> trips.firstOrNull { it.id == current.tripId } }
    if (editing != null && editingTrip != null) {
        val stops = editingTrip.stops.sortedBy(TripStop::order)
        var fromId by remember(editing.id) { mutableStateOf(editing.boardingStopId) }
        var toId by remember(editing.id) { mutableStateOf(editing.dropoffStopId) }
        var seatsText by remember(editing.id) { mutableStateOf(editing.seats.toString()) }
        var fareText by remember(editing.id) {
            mutableStateOf(
                editing.fareMinorUnits?.let {
                    PassengerMoney.formatMinorUnits(
                        it,
                        editing.fareCurrencyCode.ifBlank { moneySpec.currencyCode },
                        moneySpec.localeTag,
                    )
                }.orEmpty(),
            )
        }
        var fromMenuOpen by remember(editing.id) { mutableStateOf(false) }
        var toMenuOpen by remember(editing.id) { mutableStateOf(false) }
        val parsedSeats = seatsText.toIntOrNull()
        val parsedFare = if (fareText.isBlank()) null else PassengerMoney.parseMinorUnits(fareText, moneySpec)

        AlertDialog(
            onDismissRequest = { if (busyId == null) editingId = null },
            title = { Text("Editar reserva") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(editing.passengerName, style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { fromMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stops.firstOrNull { it.id == fromId }?.name ?: "Embarque")
                        }
                        DropdownMenu(expanded = fromMenuOpen, onDismissRequest = { fromMenuOpen = false }) {
                            stops.forEach { stop ->
                                DropdownMenuItem(
                                    text = { Text(stop.name) },
                                    onClick = {
                                        fromId = stop.id
                                        fromMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { toMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stops.firstOrNull { it.id == toId }?.name ?: "Destino")
                        }
                        DropdownMenu(expanded = toMenuOpen, onDismissRequest = { toMenuOpen = false }) {
                            stops.forEach { stop ->
                                DropdownMenuItem(
                                    text = { Text(stop.name) },
                                    onClick = {
                                        toId = stop.id
                                        toMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = seatsText,
                        onValueChange = { seatsText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Lugares") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = fareText,
                        onValueChange = { fareText = it.take(30) },
                        label = { Text("Valor combinado (${moneySpec.currencyCode.ifBlank { "moeda local" }})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "A disponibilidade será recalculada sem contar duas vezes esta própria reserva.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = busyId == null && parsedSeats != null && (fareText.isBlank() || parsedFare != null),
                    onClick = {
                        val seats = parsedSeats ?: return@TextButton
                        busyId = editing.id
                        runCatching {
                            val updated = reservationEditedBooking0631(
                                trip = editingTrip,
                                allBookings = bookings.filter { it.tripId == editingTrip.id },
                                current = editing,
                                boardingStopId = fromId,
                                dropoffStopId = toId,
                                seats = seats,
                                fareMinorUnits = parsedFare,
                                fareCurrencyCode = moneySpec.currencyCode,
                            )
                            persistCanonicalPassengerMutation0582(
                                context = context,
                                trip = editingTrip,
                                updated = updated,
                                store = store,
                                mutationCoordinator = mutationCoordinator,
                                mutationType = "RESERVATION_EDITED_0631",
                                mutationSource = "RESERVATION_MANAGEMENT_0631",
                            )
                        }.onSuccess {
                            editingId = null
                            onChanged("Reserva atualizada; trecho, vagas e valor foram recalculados.")
                        }.onFailure {
                            onChanged("Nada foi alterado: ${it.message ?: "edição inválida"}")
                        }
                        busyId = null
                    },
                ) { Text(if (busyId == editing.id) "Salvando…" else "Salvar") }
            },
            dismissButton = {
                TextButton(enabled = busyId == null, onClick = { editingId = null }) { Text("Voltar") }
            },
        )
    }

    val rejecting = rejectingId?.let { id -> bookings.firstOrNull { it.id == id } }
    val rejectingTrip = rejecting?.let { current -> trips.firstOrNull { it.id == current.tripId } }
    if (rejecting != null && rejectingTrip != null) {
        AlertDialog(
            onDismissRequest = { if (busyId == null) rejectingId = null },
            title = { Text("Recusar solicitação?") },
            text = { Text("A mesma reserva será marcada como recusada e as vagas bloqueadas por ela serão liberadas.") },
            confirmButton = {
                TextButton(
                    enabled = busyId == null,
                    onClick = {
                        busyId = rejecting.id
                        runCatching {
                            persistCanonicalPassengerMutation0582(
                                context = context,
                                trip = rejectingTrip,
                                updated = passengerDecisionMutation0582(rejecting, "REJECT"),
                                store = store,
                                mutationCoordinator = mutationCoordinator,
                                mutationType = "RESERVATION_REJECTED",
                                mutationSource = "RESERVATION_MANAGEMENT_0631",
                            )
                        }.onSuccess {
                            rejectingId = null
                            onChanged("Solicitação recusada; histórico preservado.")
                        }.onFailure { onChanged("Nada foi alterado: ${it.message ?: "falha ao recusar"}") }
                        busyId = null
                    },
                ) { Text(if (busyId == rejecting.id) "Recusando…" else "Recusar") }
            },
            dismissButton = { TextButton(onClick = { rejectingId = null }) { Text("Voltar") } },
        )
    }

    val cancelling = cancellingId?.let { id -> bookings.firstOrNull { it.id == id } }
    val cancellingTrip = cancelling?.let { current -> trips.firstOrNull { it.id == current.tripId } }
    if (cancelling != null && cancellingTrip != null) {
        AlertDialog(
            onDismissRequest = { if (busyId == null) cancellingId = null },
            title = { Text("Cancelar esta reserva?") },
            text = {
                Text("A reserva não será apagada. Ela ficará no histórico como cancelada e deixará de ocupar vagas nos trechos.")
            },
            confirmButton = {
                TextButton(
                    enabled = busyId == null,
                    onClick = {
                        busyId = cancelling.id
                        runCatching {
                            persistCanonicalPassengerMutation0582(
                                context = context,
                                trip = cancellingTrip,
                                updated = passengerOperationalMutation0582(cancelling, "CANCELLED"),
                                store = store,
                                mutationCoordinator = mutationCoordinator,
                                mutationType = "BOOKING_CANCELLED_BY_DRIVER",
                                mutationSource = "RESERVATION_MANAGEMENT_0631",
                            )
                        }.onSuccess {
                            cancellingId = null
                            onChanged("Reserva cancelada; histórico preservado e vagas liberadas.")
                        }.onFailure { onChanged("Nada foi alterado: ${it.message ?: "falha ao cancelar"}") }
                        busyId = null
                    },
                ) { Text(if (busyId == cancelling.id) "Cancelando…" else "Cancelar reserva") }
            },
            dismissButton = { TextButton(onClick = { cancellingId = null }) { Text("Voltar") } },
        )
    }

    val addingTrip = addingTripId?.let { id -> trips.firstOrNull { it.id == id } }
    if (addingTrip != null) {
        val canonicalBackendAuthority = addingTrip.remoteId?.isNotBlank() == true && store.onlineSettings().configured
        AlertDialog(
            onDismissRequest = { addingTripId = null },
            title = { Text("Adicionar passageiro") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(addingTrip.title, style = MaterialTheme.typography.titleSmall)
                    QuickPassengerPanel(
                        trip = addingTrip,
                        store = store,
                        onChanged = onChanged,
                        onBlaBlaSyncRequested = null,
                        onSaved = {
                            addingTripId = null
                            onChanged("Passageiro adicionado à viagem.")
                        },
                        showExistingPassengers = true,
                        canonicalBookings0494 = bookings.filter { it.tripId == addingTrip.id },
                        canonicalBackendAuthority0494 = canonicalBackendAuthority,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addingTripId = null }) { Text("Fechar") } },
        )
    }
}
