package br.com.mapeiaia.rotacerta.trips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.Coordinate
import br.com.mapeiaia.rotacerta.R
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class EnhancedPassengerCardRow(
    val name: String,
    val phone: String?,
    val seats: Int,
    val boarding: String?,
    val dropoff: String?,
    val sources: Set<BookingSource>,
    val passengerId: String? = null,
    val localBookingId: String? = null,
    val externalReservationKey: String? = null,
    val externalBookingHref: String? = null,
    val externalProfileUuid: String? = null,
    val bookingStatus: BookingStatus? = null,
    val operationalStatus: PassengerOperationalStatus = PassengerOperationalStatus.CONFIRMED,
    val paymentStatus: PassengerPaymentStatus = PassengerPaymentStatus.UNPAID,
    val lastDriverSelection: String = "",
    val fareMinorUnits: Long? = null,
    val fareCurrencyCode: String = "",
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
    val boardingStopIndex: Int? = null,
    val matchedByPhone: Boolean = false,
    val probableMatch: Boolean = false,
    val externalPassengerId: String? = null,
)

internal data class PassengerTimelineRenderSnapshot0394(
    val rows: List<EnhancedPassengerCardRow>,
    val profilesByRowKey: Map<String, PassengerProfile>,
    val bookingsById: Map<String, Booking>,
    val historiesByProfileId: Map<String, PassengerPersistentHistory>,
    val completedRowKeys: Set<String>,
)

internal fun passengerTimelineRowKey0394(row: EnhancedPassengerCardRow): String =
    row.localBookingId?.trim()?.takeIf(String::isNotEmpty)
        ?: row.externalReservationKey?.trim()?.takeIf(String::isNotEmpty)
        ?: row.externalPassengerId?.trim()?.takeIf(String::isNotEmpty)
        ?: listOf(row.name.trim().lowercase(), row.phone.orEmpty().filter(Char::isDigit), row.boarding.orEmpty(), row.dropoff.orEmpty())
            .joinToString("|")

internal fun buildPassengerTimelineRenderSnapshot0394(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
    completionService: PassengerCompletionService,
    canonicalBookings0494: List<Booking>? = null,
): PassengerTimelineRenderSnapshot0394 {
    val externalMetadata = passengerStore.externalMetadataSnapshot0394()
    val localBookings = trip?.let { selectedTrip ->
        canonicalBookings0494?.filter { it.tripId == selectedTrip.id }
            ?: store.bookingsFor(selectedTrip.id)
    }.orEmpty()
    val rows = enhancedPassengerRows(
        entry = entry,
        trip = trip,
        store = store,
        passengerStore = passengerStore,
        externalMetadataSnapshot0394 = externalMetadata,
        localBookingsSnapshot0394 = localBookings,
    )
    val profilesByRowKey = rows.mapNotNull { row ->
        completionService.resolvedProfile(row)?.let { profile -> passengerTimelineRowKey0394(row) to profile }
    }.toMap()
    val historiesByProfileId = passengerStore.persistentHistorySnapshot(
        profilesByRowKey.values.map(PassengerProfile::id).toSet(),
    )
    val completedRowKeys = rows.mapNotNull { row ->
        val rowKey = passengerTimelineRowKey0394(row)
        val profile = profilesByRowKey[rowKey] ?: return@mapNotNull null
        val occurrenceKey = completionService.occurrenceKey(entry, row)
        val completed = historiesByProfileId[profile.id]
            ?.rides
            .orEmpty()
            .any { record -> record.rideKey == occurrenceKey && record.status == PassengerOccurrenceStatus.COMPLETED }
        rowKey.takeIf { completed }
    }.toSet()
    return PassengerTimelineRenderSnapshot0394(
        rows = rows,
        profilesByRowKey = profilesByRowKey,
        bookingsById = localBookings.associateBy(Booking::id),
        historiesByProfileId = historiesByProfileId,
        completedRowKeys = completedRowKeys,
    )
}

@Composable
internal fun EnhancedPassengerTimelineSection(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    currentCoordinate: Coordinate?,
    onChanged: (String) -> Unit,
    onAddManualPassenger: (() -> Unit)? = null,
    focusedBookingId: String? = null,
    canonicalBookings0494: List<Booking>? = null,
) {
    val context = LocalContext.current
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    val completionService = remember(context) { PassengerCompletionService(context) }
    val mutationCoordinator = remember(context, store) { TripMutationCoordinator0387(context, store) }
    val scope = rememberCoroutineScope()
    var identityRevision by remember { mutableIntStateOf(0) }
    var completionRevision by remember { mutableIntStateOf(0) }
    var renderSnapshot0394 by remember(entry.tripId, trip?.id) {
        mutableStateOf<PassengerTimelineRenderSnapshot0394?>(null)
    }
    LaunchedEffect(entry, trip, identityRevision, completionRevision) {
        renderSnapshot0394 = withContext(Dispatchers.IO) {
            buildPassengerTimelineRenderSnapshot0394(
                entry = entry,
                trip = trip,
                store = store,
                passengerStore = passengerStore,
                completionService = completionService,
                canonicalBookings0494 = canonicalBookings0494,
            )
        }
    }
    val renderSnapshot = renderSnapshot0394
    val rawRows = renderSnapshot?.rows.orEmpty()
    val externalObservationKey = rawRows
        .filter { BookingSource.BLABLACAR in it.sources }
        .joinToString("|") { row ->
            listOf(row.externalPassengerId.orEmpty(), row.name, row.phone.orEmpty(), row.externalReservationKey.orEmpty()).joinToString("~")
        }
    LaunchedEffect(entry.tripId, entry.blablaTripId, entry.blablaProfileUuid, externalObservationKey) {
        if (entry.canonicalBackendAuthoritative0494 || externalObservationKey.isBlank()) return@LaunchedEffect
        val observed = withContext(Dispatchers.IO) {
            var anyObserved = false
            rawRows.filter { BookingSource.BLABLACAR in it.sources }.forEach { row ->
                val profile = passengerStore.observeExternalPassenger(
                    displayName = row.name,
                    whatsapp = row.phone,
                    externalPassengerId = row.externalPassengerId,
                    reservationKey = row.externalReservationKey,
                    externalTripId = entry.blablaTripId,
                    driverProfileUuid = entry.blablaProfileUuid,
                )
                if (profile != null) anyObserved = true
            }
            anyObserved
        }
        if (observed) identityRevision++
    }
    @Suppress("UNUSED_VARIABLE")
    val identityRefresh = identityRevision
    @Suppress("UNUSED_VARIABLE")
    val completionRefresh = completionRevision
    if (hasExternalTripActionEvidence(entry)) {
        TripBlaBlaTripActionRow(entry, onAddManualPassenger)
    }
    if (renderSnapshot == null) {
        Text("Carregando passageiros…", style = MaterialTheme.typography.bodySmall)
        return
    }
    if (rawRows.isEmpty()) return

    val progress = trip?.let { TripPassengerRouteOrder.progress(it, currentCoordinate) }
    // Keep trusted route/GPS ordering internally, but do not expose a
    // "next action" status in the card. The pickup/dropoff emojis are the
    // explicit GPS actions while the place labels keep their existing editor action.
    val rows = passengerTimelineOperationalOrder(rawRows, progress)
        .sortedBy { row -> if (row.localBookingId == focusedBookingId) 0 else 1 }

    var profileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var blockProfile by remember { mutableStateOf<PassengerProfile?>(null) }
    var historyRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var editManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var cancelManualRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var createProfileRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var fareEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var boardingAddressEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }
    var dropoffAddressEditRow by remember { mutableStateOf<EnhancedPassengerCardRow?>(null) }

    val selectedHistoryRow = historyRow
    if (selectedHistoryRow != null) {
        val profile = selectedHistoryRow.passengerId?.let(passengerStore::profile)
            ?: passengerStore.profileByExternalPassengerId(selectedHistoryRow.externalPassengerId)
        PassengerHistoryPanel(
            history = profile?.let { passengerStore.persistentHistory(it.id) },
            onBack = { historyRow = null },
            onArchiveToggle = { selectedProfile ->
                passengerStore.setArchived(selectedProfile.id, !selectedProfile.archived)
                identityRevision++
                historyRow = null
                onChanged(
                    if (selectedProfile.archived) "Passageiro restaurado na lista; histórico preservado."
                    else "Passageiro arquivado da lista; histórico, UUIDs, bloqueios e viagens foram preservados.",
                )
            },
        )
        return
    }

    rows.forEachIndexed { index, passenger ->
        if (index > 0) HorizontalDivider()
        val rowKey0394 = passengerTimelineRowKey0394(passenger)
        val rowProfile = renderSnapshot.profilesByRowKey[rowKey0394]
        val currentBooking = passenger.localBookingId?.let(renderSnapshot.bookingsById::get)
        var statusMenuOpen by remember(passenger.localBookingId, passenger.externalPassengerId) {
            mutableStateOf(false)
        }
        var decisionRunning by remember(passenger.localBookingId) { mutableStateOf<String?>(null) }
        var rejectConfirmOpen by remember(passenger.localBookingId) { mutableStateOf(false) }
        var rejectReason by remember(passenger.localBookingId) { mutableStateOf("") }
        val pendingApproval = currentBooking?.status == BookingStatus.REQUESTED &&
            currentBooking.source == BookingSource.ROTA_CERTA
        val rejected = currentBooking?.status == BookingStatus.REJECTED
        val completed = rowKey0394 in renderSnapshot.completedRowKeys
        val statusLabel = when {
            rejected -> "Recusado"
            pendingApproval -> "Aguardando aprovação"
            completed || passenger.operationalStatus == PassengerOperationalStatus.COMPLETED -> "Concluído"
            passenger.lastDriverSelection == "PAID" -> "Pago"
            passenger.operationalStatus == PassengerOperationalStatus.AT_LOCATION -> "No local"
            passenger.operationalStatus == PassengerOperationalStatus.IN_CAR -> "No carro"
            passenger.operationalStatus == PassengerOperationalStatus.CANCELLED -> "Cancelado"
            passenger.operationalStatus == PassengerOperationalStatus.PENDING -> "Aguardando"
            else -> "Confirmado"
        }
        val selectOperationalStatus: (String) -> Unit = select@{ selection ->
            statusMenuOpen = false
            val occurrenceCompleted =
                completed || passenger.operationalStatus == PassengerOperationalStatus.COMPLETED
            if (occurrenceCompleted && selection !in setOf("COMPLETED", "PAID")) {
                onChanged("Esta ocorrência já foi concluída. A conclusão é permanente; apenas o pagamento ainda pode ser confirmado.")
                return@select
            }
            if (passenger.operationalStatus == PassengerOperationalStatus.CANCELLED && selection != "CANCELLED") {
                onChanged("Esta reserva já foi cancelada. Uma nova participação precisa nascer como nova reserva/ocorrência.")
                return@select
            }
            if (selection == "CANCELLED") {
                val trace = passengerCancellationDebugContext(entry, passenger, currentBooking)
                val inCarBefore = passenger.operationalStatus == PassengerOperationalStatus.IN_CAR
                UnifiedDebugEventStore.record(
                    "PASSENGER_STATUS_CHANGE_REQUESTED",
                    context.packageName,
                    "$trace requested=CANCELLED",
                )
                UnifiedDebugEventStore.record(
                    "PASSENGER_STATUS_PREVIOUS",
                    context.packageName,
                    "$trace bookingStatus=${passenger.bookingStatus?.name ?: "EXTERNAL_ONLY"} operationalStatus=${passenger.operationalStatus.name} inCar=$inCarBefore seats=${passenger.seats}",
                )
                UnifiedDebugEventStore.record(
                    "PASSENGER_STATUS_CANCEL_SELECTED",
                    context.packageName,
                    "$trace source=TIMELINE internalOnly=true",
                )
                if (currentBooking != null ||
                    (BookingSource.BLABLACAR in passenger.sources && !passenger.externalReservationKey.isNullOrBlank())
                ) {
                    cancelManualRow = passenger
                } else {
                    onChanged("Não foi possível identificar a reserva/ocorrência exata para cancelar com segurança.")
                }
                return@select
            }

            if (pendingApproval) {
                onChanged("Use Aprovar ou Recusar para resolver esta solicitação. O seletor operacional não aprova reservas pendentes.")
                return@select
            }
            scope.launch {
                val selectedTrip = trip
                val booking = currentBooking
                val settings0494 = store.onlineSettings()
                val remoteId0494 = selectedTrip?.remoteId?.takeIf(String::isNotBlank)
                when {
                    selectedTrip == null || booking == null -> {
                        onChanged("A ocorrência canônica não está disponível para alterar o status.")
                        return@launch
                    }
                    !settings0494.configured || remoteId0494 == null -> {
                        onChanged("Backend canônico indisponível. Nada foi alterado; o coletor não será usado como fallback.")
                        return@launch
                    }
                }

                runCatching {
                    TripRemoteApi(settings0494).updateDriverPassengerOperationalStatus(
                        remoteTripId = remoteId0494!!,
                        bookingId = booking.id,
                        selection = selection,
                    )
                }.onSuccess { ack0494 ->
                    if (selection == "COMPLETED") {
                        completionService.confirm(entry, passenger)?.let {
                            completionRevision++
                            identityRevision++
                        }
                    }
                    BookingRealtimeEvents0356.notifyChanged()
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CANONICAL_PASSENGER_MUTATION_0494",
                        context.packageName,
                        "canonicalTripId=${seatSyncDiagnosticKey(selectedTrip.id)} bookingIdPresent=true selection=$selection entityRevision=${ack0494.entityRevision} authority=CANONICAL_BACKEND localBusinessWrite=false",
                    )
                    val message = when (selection) {
                        "CONFIRMED" -> "Passageiro confirmado no estado canônico."
                        "AT_LOCATION" -> "Status No local salvo no estado canônico."
                        "IN_CAR" -> "Status No carro salvo no estado canônico."
                        "PAID" -> "Pagamento confirmado no estado canônico."
                        "COMPLETED" -> "Passageiro concluído no estado canônico."
                        else -> "Status atualizado no estado canônico."
                    }
                    onChanged(message)
                }.onFailure { error ->
                    onChanged("Nada foi alterado: ${error.message ?: "falha ao gravar no backend canônico"}")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val phone = passenger.phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = {
                        if (!phone.isNullOrBlank()) {
                            UnifiedDebugEventStore.record(
                                "PASSENGER_WHATSAPP_OPEN",
                                context.packageName,
                                "timeline=true phone_present=true",
                            )
                            openPassengerWhatsApp(context, phone)
                        }
                    },
                    enabled = !phone.isNullOrBlank(),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_whatsapp_action),
                        contentDescription = "Abrir WhatsApp do passageiro",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp),
                    )
                }

                val passengerTarget = externalPassengerTarget(passenger)
                if (passengerTarget != null) {
                    IconButton(
                        onClick = {
                            if (!openExternalPassengerBlaBla(context, passenger)) {
                                Toast.makeText(
                                    context,
                                    "Conta BlaBlaCar deste passageiro não está conectada.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_blablacar_action),
                            contentDescription = "Abrir passageiro no BlaBlaCar",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (passenger.fareMinorUnits != null) {
                            copyPassengerFareValue(context, passenger)
                        } else {
                            fareEditRow = passenger
                        }
                    },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💰", maxLines = 1)
                }

                OutlinedButton(
                    onClick = { copyPassengerConfirmationMessage(context, entry, passenger) },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💬", maxLines = 1)
                }
            }


            if (pendingApproval) {
                Text(
                    "🟠 Aguardando aprovação",
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled = decisionRunning == null,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val selectedTrip = trip
                            val booking = currentBooking
                            if (selectedTrip == null || booking == null) {
                                onChanged("Não foi possível localizar a viagem/reserva canônica para aprovar.")
                            } else {
                                decisionRunning = "APPROVE"
                                scope.launch {
                                    runCatching {
                                        val settings0494 = store.onlineSettings()
                                        val remoteId0494 = selectedTrip.remoteId?.takeIf(String::isNotBlank)
                                            ?: error("Viagem sem identidade remota canônica.")
                                        check(settings0494.configured) { "Backend canônico indisponível." }
                                        TripRemoteApi(settings0494).decideDriverBooking(
                                            remoteTripId = remoteId0494,
                                            bookingId = booking.id,
                                            action = "APPROVE",
                                        )
                                    }.onSuccess { ack0494 ->
                                        BookingRealtimeEvents0356.notifyChanged()
                                        UnifiedDebugEventStore.record(
                                            "TIMELINE_CANONICAL_BOOKING_DECISION_0494",
                                            context.packageName,
                                            "action=APPROVE authority=CANONICAL_BACKEND entityRevision=${ack0494.entityRevision} localBusinessWrite=false",
                                        )
                                        onChanged("Reserva aprovada no estado canônico ✅")
                                    }.onFailure { error ->
                                        onChanged("Nada foi alterado: ${error.message ?: error.javaClass.simpleName}")
                                    }
                                    decisionRunning = null
                                }
                            }
                        },
                    ) { Text(if (decisionRunning == "APPROVE") "Aprovando…" else "Aprovar") }
                    OutlinedButton(
                        enabled = decisionRunning == null,
                        modifier = Modifier.weight(1f),
                        onClick = { rejectConfirmOpen = true },
                    ) { Text("Recusar") }
                }
            }

            if (rejectConfirmOpen) {
                AlertDialog(
                    onDismissRequest = { if (decisionRunning == null) rejectConfirmOpen = false },
                    title = { Text("Recusar solicitação?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("A mesma reserva será marcada como Recusada e as vagas deste trecho serão liberadas.")
                            OutlinedTextField(
                                value = rejectReason,
                                onValueChange = { rejectReason = it.take(240) },
                                label = { Text("Motivo opcional") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = decisionRunning == null,
                            onClick = {
                                val selectedTrip = trip
                                val booking = currentBooking
                                if (selectedTrip == null || booking == null) {
                                    rejectConfirmOpen = false
                                    onChanged("Não foi possível localizar a viagem/reserva canônica para recusar.")
                                } else {
                                    decisionRunning = "REJECT"
                                    scope.launch {
                                        runCatching {
                                            val settings0494 = store.onlineSettings()
                                            val remoteId0494 = selectedTrip.remoteId?.takeIf(String::isNotBlank)
                                                ?: error("Viagem sem identidade remota canônica.")
                                            check(settings0494.configured) { "Backend canônico indisponível." }
                                            TripRemoteApi(settings0494).decideDriverBooking(
                                                remoteTripId = remoteId0494,
                                                bookingId = booking.id,
                                                action = "REJECT",
                                                reason = rejectReason,
                                            )
                                        }.onSuccess { ack0494 ->
                                            BookingRealtimeEvents0356.notifyChanged()
                                            UnifiedDebugEventStore.record(
                                                "TIMELINE_CANONICAL_BOOKING_DECISION_0494",
                                                context.packageName,
                                                "action=REJECT authority=CANONICAL_BACKEND entityRevision=${ack0494.entityRevision} localBusinessWrite=false",
                                            )
                                            rejectConfirmOpen = false
                                            rejectReason = ""
                                            onChanged("Solicitação recusada no estado canônico")
                                        }.onFailure { error ->
                                            onChanged("Nada foi alterado: ${error.message ?: error.javaClass.simpleName}")
                                        }
                                        decisionRunning = null
                                    }
                                }
                            },
                        ) { Text(if (decisionRunning == "REJECT") "Recusando…" else "Recusar") }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = decisionRunning == null,
                            onClick = { rejectConfirmOpen = false },
                        ) { Text("Voltar") }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    onClick = {
                        historyRow = passenger.copy(passengerId = rowProfile?.id ?: passenger.passengerId)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = COMPACT_NAME_PADDING,
                ) {
                    Text(
                        (if (rowProfile?.blocked == true) "🚫 " else "") + passenger.name.ifBlank { "Passageiro" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (pendingApproval || rejected || currentBooking?.status == BookingStatus.CANCELLED) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = COMPACT_ACTION_PADDING,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text(statusLabel, maxLines = 1)
                    }
                } else {
                    Column {
                        OutlinedButton(
                            onClick = { statusMenuOpen = true },
                            contentPadding = COMPACT_ACTION_PADDING,
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Text(statusLabel + " ▼", maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = statusMenuOpen,
                            onDismissRequest = { statusMenuOpen = false },
                        ) {
                            DropdownMenuItem(text = { Text("Confirmado") }, onClick = { selectOperationalStatus("CONFIRMED") })
                            DropdownMenuItem(text = { Text("No local") }, onClick = { selectOperationalStatus("AT_LOCATION") })
                            DropdownMenuItem(text = { Text("No carro") }, onClick = { selectOperationalStatus("IN_CAR") })
                            DropdownMenuItem(text = { Text("Pago") }, onClick = { selectOperationalStatus("PAID") })
                            DropdownMenuItem(text = { Text("Concluído") }, onClick = { selectOperationalStatus("COMPLETED") })
                        }
                    }
                    if (!completed && passenger.operationalStatus != PassengerOperationalStatus.CANCELLED) {
                        TextButton(
                            onClick = {
                                if (currentBooking != null ||
                                    (BookingSource.BLABLACAR in passenger.sources && !passenger.externalReservationKey.isNullOrBlank())
                                ) {
                                    cancelManualRow = passenger
                                } else {
                                    onChanged("Não foi possível identificar a reserva/ocorrência exata para cancelar com segurança.")
                                }
                            },
                        ) { Text("Cancelar") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val pickupTarget = passengerPickupMapTarget(passenger)
                TextButton(
                    onClick = {
                        if (pickupTarget != null) openPassengerPickupMap(context, pickupTarget)
                    },
                    enabled = pickupTarget != null,
                    modifier = Modifier.size(36.dp),
                    contentPadding = ADDRESS_ICON_PADDING,
                ) {
                    Text("📍", maxLines = 1)
                }
                TextButton(
                    onClick = { boardingAddressEditRow = passenger },
                    modifier = Modifier.weight(1f),
                    contentPadding = ADDRESS_PLACE_PADDING,
                ) {
                    Text(
                        passengerTimelineCompactPlace(passenger.boarding),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("→")
                val dropoffTarget = passengerDropoffMapTarget(passenger)
                TextButton(
                    onClick = {
                        if (dropoffTarget != null) openPassengerDropoffMap(context, dropoffTarget)
                    },
                    enabled = dropoffTarget != null,
                    modifier = Modifier.size(36.dp),
                    contentPadding = ADDRESS_ICON_PADDING,
                ) {
                    Text("🏁", maxLines = 1)
                }
                TextButton(
                    onClick = { dropoffAddressEditRow = passenger },
                    modifier = Modifier.weight(1f),
                    contentPadding = ADDRESS_PLACE_PADDING,
                ) {
                    Text(
                        passengerTimelineCompactPlace(passenger.dropoff),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val source = passenger.sources.joinToString(" + ") { enhancedSourceShort(it) }
            val seats = if (passenger.seats == 1) "1 lugar" else "${passenger.seats} lugares"
            val identity = when {
                passenger.matchedByPhone -> " • ✓"
                passenger.probableMatch -> " • ⚠"
                passenger.phone.isNullOrBlank() -> " • tel.?"
                else -> ""
            }

            Text(
                "$source • $seats$identity",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val canonicalProfile = rowProfile
            val persistentHistory = canonicalProfile?.id?.let(renderSnapshot.historiesByProfileId::get)
            val identityLabel = when {
                canonicalProfile?.blocked == true -> "⛔ NÃO ACEITO NO MEU CARRO • ${persistentHistory?.totalRides ?: 0} concluída(s) • ${persistentHistory?.totalOccurrences ?: 0} ocorrência(s)"
                persistentHistory != null -> "${persistentHistory.totalRides} concluída(s) • ${persistentHistory.totalOccurrences} ocorrência(s)/reserva(s)"
                passenger.externalPassengerId != null -> "Identidade BlaBlaCar disponível"
                else -> null
            }
            identityLabel?.let { label ->
                TextButton(
                    onClick = {
                        if (canonicalProfile != null) profileRow = passenger.copy(passengerId = canonicalProfile.id)
                        else createProfileRow = passenger
                    },
                    contentPadding = COMPACT_NAME_PADDING,
                ) { Text(label, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }


    profileRow?.let { row ->
        val profile = passengerStore.profile(row.passengerId)
            ?: passengerStore.profileByExternalPassengerId(row.externalPassengerId)
        val history = profile?.let { passengerStore.rideHistory(it.id) }
        val manualBooking = trip?.let { currentTrip ->
            row.localBookingId?.let { bookingId ->
                store.bookingsFor(currentTrip.id).firstOrNull { booking ->
                    booking.id == bookingId &&
                        booking.source in setOf(BookingSource.ROTA_CERTA, BookingSource.PRIVATE, BookingSource.OTHER) &&
                        booking.capacityClaimType == CapacityClaimType.PASSENGER &&
                        booking.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { profileRow = null },
            title = { Text("Passageiro Rota Certa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile?.displayName ?: row.name)
                    val phone = profile?.whatsapp?.takeIf(String::isNotBlank) ?: row.phone
                    Text(phone?.takeIf(String::isNotBlank) ?: "Telefone não informado")
                    Text("Identidade canônica vinculada", style = MaterialTheme.typography.bodySmall)
                    history?.let { Text("${it.totalRides} viagem(ns) concluída(s)", style = MaterialTheme.typography.bodySmall) }
                    if (profile?.blocked == true) Text("⛔ NÃO ACEITO NO MEU CARRO", color = MaterialTheme.colorScheme.error)
                    if (manualBooking != null) {
                        TextButton(onClick = {
                            editManualRow = row
                            profileRow = null
                        }) { Text("Editar lugares / trecho") }
                        TextButton(onClick = {
                            cancelManualRow = row
                            profileRow = null
                        }) { Text("Cancelar / excluir desta viagem") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    blockProfile = profile
                    profileRow = null
                }) { Text(if (profile?.blocked == true) "✅" else "🚫") }
            },
            dismissButton = { TextButton(onClick = { profileRow = null }) { Text("Fechar") } },
        )
    }

    blockProfile?.let { profile ->
        val blocking = !profile.blocked
        AlertDialog(
            onDismissRequest = { blockProfile = null },
            title = { Text(if (blocking) "Não aceito no meu carro?" else "Remover bloqueio?") },
            text = {
                Text(
                    if (blocking) {
                        "O bloqueio fica ligado ao passengerId/identidade forte, nega a Agenda de Viagens e cancela reservas Rota Certa ativas, liberando as vagas."
                    } else {
                        "O desbloqueio é explícito. PassengerId, cadastro, histórico e viagens permanecem preservados."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    val saved = passengerStore.setBlocked(
                        profile.id,
                        blocking,
                        if (blocking) "Não aceito no meu carro" else "",
                    ) ?: profile
                    identityRevision++
                    blockProfile = null
                    onChanged(
                        if (blocking) "⛔ Não aceito no meu carro. Bloqueio salvo no passengerId."
                        else "Passageiro desbloqueado explicitamente.",
                    )
                    val settings = store.onlineSettings()
                    val accessContact = saved.agendaAccessContact()
                    if (settings.configured && passengerContactKey(accessContact).isNotBlank()) {
                        scope.launch {
                            runCatching {
                                TripRemoteApi(settings).setPassengerAccessBlocked(
                                    passengerContact = accessContact,
                                    blocked = blocking,
                                    passengerId = saved.id,
                                )
                            }.onSuccess { response ->
                                if (blocking) {
                                    AgendaBackgroundSync0392.enqueueImmediate(context, "passenger_block_changed")
                                }
                                onChanged(
                                    if (blocking) {
                                        "⛔ Bloqueio sincronizado. ${response.cancelledBookings} reserva(s) ativa(s) cancelada(s); vagas recalculadas."
                                    } else {
                                        "Desbloqueio sincronizado. Acesso automático à Agenda de Viagens restaurado."
                                    },
                                )
                            }.onFailure { error ->
                                onChanged(
                                    "Bloqueio local preservado; sincronização online pendente: ${error.message ?: "erro de conexão"}",
                                )
                            }
                        }
                    }
                }) { Text(if (blocking) "Confirmar ⛔" else "Desbloquear") }
            },
            dismissButton = { TextButton(onClick = { blockProfile = null }) { Text("Cancelar") } },
        )
    }

    editManualRow?.let { row ->
        val currentTrip = trip
        val booking = currentTrip?.let { selectedTrip ->
            row.localBookingId?.let { bookingId ->
                renderSnapshot.bookingsById[bookingId]?.takeIf { candidate ->
                    candidate.tripId == selectedTrip.id &&
                        candidate.source in setOf(BookingSource.ROTA_CERTA, BookingSource.PRIVATE, BookingSource.OTHER) &&
                        candidate.capacityClaimType == CapacityClaimType.PASSENGER &&
                        candidate.status in setOf(BookingStatus.CONFIRMED, BookingStatus.HELD)
                }
            }
        }
        if (currentTrip != null && booking != null) {
            ManualPassengerOccupancyEditorDialog(
                trip = currentTrip,
                booking = booking,
                existingBookings = renderSnapshot.bookingsById.values.filter { it.tripId == currentTrip.id },
                onDismiss = { editManualRow = null },
                onSave = { updated ->
                    scope.launch {
                        runCatching {
                            val settings0494 = store.onlineSettings()
                            val remoteId0494 = currentTrip.remoteId?.takeIf(String::isNotBlank)
                                ?: error("Viagem sem identidade remota canônica.")
                            check(settings0494.configured) { "Backend canônico indisponível." }
                            val ack0494 = if (updated.source == BookingSource.ROTA_CERTA) {
                                TripRemoteApi(settings0494).updateProtectedDriverBooking(remoteId0494, updated)
                            } else {
                                TripRemoteApi(settings0494).upsertDriverBooking(remoteId0494, updated)
                            }
                            UnifiedDebugEventStore.record(
                                "TIMELINE_CANONICAL_BOOKING_EDIT_0494",
                                context.packageName,
                                "canonicalTripId=${seatSyncDiagnosticKey(currentTrip.id)} entityRevision=${ack0494.entityRevision} authority=CANONICAL_BACKEND localBusinessWrite=false",
                            )
                            BookingRealtimeEvents0356.notifyChanged()

                        }.onSuccess {
                            editManualRow = null
                            onChanged("Passageiro atualizado. Vagas por trecho recalculadas no estado canônico.")
                        }.onFailure { error ->
                            onChanged("Não foi possível persistir a alteração: ${error.message ?: error.javaClass.simpleName}")
                        }
                    }
                },
                onError = onChanged,
            )
        } else {
            editManualRow = null
        }
    }

    cancelManualRow?.let { row ->
        val currentTrip = trip
        val booking = row.localBookingId?.let(renderSnapshot.bookingsById::get)
        AlertDialog(
            onDismissRequest = { cancelManualRow = null },
            title = { Text("Cancelar no Rota Certa e liberar a(s) vaga(s)?") },
            text = {
                Text(
                    if (BookingSource.BLABLACAR in row.sources) {
                        "O backend canônico do Rota Certa marcará esta ocorrência como cancelada e recalculará as vagas. A publicação BlaBlaCar não será cancelada automaticamente."
                    } else {
                        "A reserva será cancelada no backend canônico e as vagas serão recalculadas nos trechos correspondentes."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = currentTrip?.remoteId?.isNotBlank() == true && booking != null,
                    onClick = {
                        val selectedTrip = currentTrip
                        val selectedBooking = booking
                        if (selectedTrip == null || selectedBooking == null) {
                            cancelManualRow = null
                            onChanged("Reserva canônica não localizada. Nada foi alterado.")
                        } else {
                            scope.launch {
                                val settings0494 = store.onlineSettings()
                                val remoteId0494 = selectedTrip.remoteId?.takeIf(String::isNotBlank)
                                if (!settings0494.configured || remoteId0494 == null) {
                                    onChanged("Backend canônico indisponível. Nada foi alterado; o coletor não será usado como fallback.")
                                    return@launch
                                }
                                runCatching {
                                    TripRemoteApi(settings0494).updateDriverPassengerOperationalStatus(
                                        remoteTripId = remoteId0494,
                                        bookingId = selectedBooking.id,
                                        selection = "CANCELLED",
                                    )
                                }.onSuccess { ack0494 ->
                                    cancelManualRow = null
                                    identityRevision++
                                    BookingRealtimeEvents0356.notifyChanged()
                                    UnifiedDebugEventStore.record(
                                        "TIMELINE_CANONICAL_BOOKING_CANCEL_0494",
                                        context.packageName,
                                        "authority=CANONICAL_BACKEND entityRevision=${ack0494.entityRevision} localBusinessWrite=false blablaPlatformChanged=false",
                                    )
                                    onChanged(
                                        if (BookingSource.BLABLACAR in row.sources) {
                                            "Reserva cancelada no Rota Certa e vagas canônicas recalculadas. A BlaBlaCar não foi alterada."
                                        } else {
                                            "Reserva cancelada no estado canônico e vagas recalculadas."
                                        },
                                    )
                                }.onFailure { error ->
                                    onChanged("Nada foi alterado: ${error.message ?: error.javaClass.simpleName}")
                                }
                            }
                        }
                    },
                ) { Text("Cancelar reserva") }
            },
            dismissButton = { TextButton(onClick = { cancelManualRow = null }) { Text("Voltar") } },
        )
    }

    createProfileRow?.let { row ->
        val exact = passengerStore.exactContactMatches(row.phone.orEmpty()).singleOrNull()
        AlertDialog(
            onDismissRequest = { createProfileRow = null },
            title = { Text("Vincular passageiro") },
            text = {
                Text(
                    if (exact != null) {
                        "Existe um cadastro com o mesmo contato (${exact.displayName}). O vínculo só será feito se você confirmar."
                    } else {
                        "Criar um cadastro Rota Certa separado para ${row.name}? Nome ou telefone nunca serão fundidos automaticamente."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val profile = exact ?: passengerStore.createProfile(row.name, row.phone.orEmpty())
                    val canonicalBooking0494 = row.localBookingId?.let(renderSnapshot.bookingsById::get)
                    val canonicalTrip0494 = trip
                    val remoteId0494 = canonicalTrip0494?.remoteId?.takeIf(String::isNotBlank)
                    if (canonicalBooking0494 != null && canonicalTrip0494 != null && remoteId0494 != null) {
                        scope.launch {
                            val settings0494 = store.onlineSettings()
                            if (!settings0494.configured) {
                                onChanged("Backend canônico indisponível. O vínculo não foi aplicado ao booking.")
                                return@launch
                            }
                            runCatching {
                                val updated0494 = canonicalBooking0494.copy(passengerId = profile.id)
                                if (updated0494.source == BookingSource.ROTA_CERTA) {
                                    TripRemoteApi(settings0494).updateProtectedDriverBooking(remoteId0494, updated0494)
                                } else {
                                    TripRemoteApi(settings0494).upsertDriverBooking(remoteId0494, updated0494)
                                }
                            }.onSuccess { ack0494 ->
                                row.externalPassengerId?.let { externalId ->
                                    passengerStore.linkExternalPassengerId(profile.id, externalId)
                                }
                                BookingRealtimeEvents0356.notifyChanged()
                                UnifiedDebugEventStore.record(
                                    "TIMELINE_CANONICAL_PASSENGER_ID_LINK_0494",
                                    context.packageName,
                                    "canonicalTripId=${seatSyncDiagnosticKey(canonicalTrip0494.id)} entityRevision=${ack0494.entityRevision} passengerIdHash=${passengerDebugIdentityHash(profile.id)} authority=CANONICAL_BACKEND",
                                )
                                onChanged("Cadastro do passageiro vinculado ao booking canônico.")
                            }.onFailure { error ->
                                onChanged("Vínculo não aplicado: ${error.message ?: error.javaClass.simpleName}")
                            }
                        }
                    } else if (linkPassengerProfileLegacyMetadata0494(row, profile.id, passengerStore)) {
                        row.externalPassengerId?.let { externalId ->
                            passengerStore.linkExternalPassengerId(profile.id, externalId)
                        }
                        onChanged("Cadastro vinculado à ocorrência legada.")
                    } else {
                        Toast.makeText(
                            context,
                            "Reserva sem identidade canônica estável; vínculo não aplicado.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    createProfileRow = null
                }) { Text(if (exact != null) "Vincular cadastro" else "Criar cadastro") }
            },
            dismissButton = { TextButton(onClick = { createProfileRow = null }) { Text("Cancelar") } },
        )
    }

    fareEditRow?.let { row ->
        PassengerFareEditorDialog(
            row = row,
            onDismiss = { fareEditRow = null },
            onSave = { amount, currency ->
                if (savePassengerFare(row, amount, currency, store, passengerStore)) {
                    copyPassengerFareValue(
                        context,
                        row.copy(
                            fareMinorUnits = amount,
                            fareCurrencyCode = currency,
                        ),
                    )
                    onChanged("Valor salvo e copiado.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; valor não foi salvo.", Toast.LENGTH_LONG).show()
                }
                fareEditRow = null
            },
        )
    }

    boardingAddressEditRow?.let { row ->
        PassengerAddressEditorDialog(
            title = "Endereço completo de embarque",
            initialValue = passengerAddressEditorInitialValue(row.boardingAddress, row.boarding),
            onDismiss = { boardingAddressEditRow = null },
            onSave = { address ->
                if (savePassengerAddress(row, address, true, store, passengerStore)) {
                    onChanged("Endereço de embarque salvo.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; endereço não foi salvo.", Toast.LENGTH_LONG).show()
                }
                boardingAddressEditRow = null
            },
        )
    }

    dropoffAddressEditRow?.let { row ->
        PassengerAddressEditorDialog(
            title = "Endereço completo de destino",
            initialValue = passengerAddressEditorInitialValue(row.dropoffAddress, row.dropoff),
            onDismiss = { dropoffAddressEditRow = null },
            onSave = { address ->
                if (savePassengerAddress(row, address, false, store, passengerStore)) {
                    onChanged("Endereço de destino salvo.")
                } else {
                    Toast.makeText(context, "Reserva sem referência estável; endereço não foi salvo.", Toast.LENGTH_LONG).show()
                }
                dropoffAddressEditRow = null
            },
        )
    }
}

private fun passengerCancellationDebugContext(
    entry: TripTimelineEntry,
    row: EnhancedPassengerCardRow,
    booking: Booking?,
): String = listOf(
    "tripKey=" + passengerCancellationHash(entry.localTripId ?: entry.blablaTripId ?: entry.tripId),
    "bookingKey=" + passengerCancellationHash(booking?.id ?: row.externalReservationKey),
    "passengerKey=" + passengerCancellationHash(row.passengerId ?: row.externalPassengerId),
).joinToString(" ")

private fun passengerCancellationHash(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return "none"
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun enhancedPassengerRows(
    entry: TripTimelineEntry,
    trip: Trip?,
    store: TripStore,
    passengerStore: PassengerIdentityStore,
    externalMetadataSnapshot0394: Map<String, ExternalPassengerMetadata>? = null,
    localBookingsSnapshot0394: List<Booking>? = null,
): List<EnhancedPassengerCardRow> {
    val rows = entry.blablaPassengers.map { passenger ->
        val metadataKey = externalPassengerReservationKey(entry.blablaProfileUuid, passenger.booking_href)
        val metadata = externalMetadataSnapshot0394?.get(metadataKey) ?: passengerStore.externalMetadata(metadataKey)
        val hrefExternalId = stableExternalPassengerId(BlaBlaCollectorUrlModule.passengerIdentityKey(passenger.booking_href))
        val externalId = metadata?.externalPassengerId?.takeIf(String::isNotBlank) ?: hrefExternalId
        val boarding = passengerTimelinePlaceLabel(passenger.name, passenger.boarding)
        val dropoff = passengerTimelinePlaceLabel(passenger.name, passenger.dropoff)
        EnhancedPassengerCardRow(
            name = passenger.name.trim(),
            phone = passenger.phone?.trim()?.takeIf(String::isNotEmpty),
            seats = passenger.seats.coerceAtLeast(1),
            boarding = boarding,
            dropoff = dropoff,
            sources = setOf(BookingSource.BLABLACAR),
            passengerId = metadata?.passengerId?.takeIf(String::isNotBlank),
            externalReservationKey = metadataKey,
            externalBookingHref = passenger.booking_href?.trim()?.takeIf(String::isNotEmpty),
            externalProfileUuid = entry.blablaProfileUuid?.trim()?.takeIf(String::isNotEmpty),
            operationalStatus = metadata?.operationalStatus ?: PassengerOperationalStatus.CONFIRMED,
            paymentStatus = metadata?.paymentStatus ?: PassengerPaymentStatus.UNPAID,
            lastDriverSelection = metadata?.lastDriverSelection.orEmpty(),
            fareMinorUnits = metadata?.fareMinorUnits,
            fareCurrencyCode = metadata?.fareCurrencyCode.orEmpty(),
            boardingAddress = metadata?.boardingAddress.orEmpty(),
            externalPassengerId = externalId,
            dropoffAddress = metadata?.dropoffAddress.orEmpty(),
            boardingStopIndex = trip?.let { TripPassengerRouteOrder.stopIndexForLabel(it, boarding) },
        )
    }.toMutableList()

    if (trip != null) {
        val stops = trip.stops.associateBy(TripStop::id)
        val local = (localBookingsSnapshot0394 ?: store.bookingsFor(trip.id))
            .filter { it.tripId == trip.id }
            .filter { it.capacityClaimType == CapacityClaimType.PASSENGER }
            .filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.HELD }
            .filter { it.seats > 0 }

        local.forEach { booking ->
            val privateMetadataKey0494 = canonicalBookingPrivateMetadataKey0494(booking.id)
            val privateMetadata0494 = externalMetadataSnapshot0394?.get(privateMetadataKey0494)
                ?: passengerStore.externalMetadata(privateMetadataKey0494)
            val phone = booking.passengerContact.trim().takeIf(String::isNotEmpty)
            val boarding = stops[booking.boardingStopId]?.name
            val dropoff = stops[booking.dropoffStopId]?.name
            val phoneKey = passengerContactKey(phone)
            val candidateIndex = rows.indexOfFirst { current ->
                val currentPhone = passengerContactKey(current.phone)
                phoneKey.isNotBlank() && currentPhone.isNotBlank() && phoneKey == currentPhone
            }
            val secondaryIndex = if (candidateIndex >= 0) -1 else rows.indexOfFirst { current ->
                enhancedPassengerNameKey(current.name) == enhancedPassengerNameKey(booking.passengerName) &&
                    current.seats == booking.seats &&
                    enhancedRouteEvidenceMatches(current.boarding, current.dropoff, boarding, dropoff)
            }
            val index = if (candidateIndex >= 0) candidateIndex else secondaryIndex
            val stopIndex = TripPassengerRouteOrder.stopIndexForId(trip, booking.boardingStopId)
            if (index >= 0) {
                val current = rows[index]
                rows[index] = current.copy(
                    name = current.name.ifBlank { booking.passengerName.trim() },
                    phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
                    seats = maxOf(current.seats, booking.seats),
                    boarding = current.boarding ?: boarding,
                    dropoff = current.dropoff ?: dropoff,
                    sources = current.sources + booking.source,
                    passengerId = if (candidateIndex >= 0) {
                        booking.passengerId.takeIf(String::isNotBlank) ?: current.passengerId
                    } else {
                        // Probable name/route similarity is visual evidence only; never canonical identity.
                        current.passengerId
                    },
                    localBookingId = booking.id,
                    bookingStatus = booking.status,
                    operationalStatus = booking.operationalStatus,
                    paymentStatus = booking.paymentStatus,
                    lastDriverSelection = booking.lastDriverSelection,
                    fareMinorUnits = privateMetadata0494?.fareMinorUnits ?: booking.fareMinorUnits ?: current.fareMinorUnits,
                    fareCurrencyCode = privateMetadata0494?.fareCurrencyCode?.takeIf(String::isNotBlank)
                        ?: booking.fareCurrencyCode.takeIf(String::isNotBlank)
                        ?: current.fareCurrencyCode,
                    boardingAddress = privateMetadata0494?.boardingAddress?.takeIf(String::isNotBlank)
                        ?: booking.boardingAddress.takeIf(String::isNotBlank)
                        ?: current.boardingAddress,
                    dropoffAddress = privateMetadata0494?.dropoffAddress?.takeIf(String::isNotBlank)
                        ?: booking.dropoffAddress.takeIf(String::isNotBlank)
                        ?: current.dropoffAddress,
                    boardingStopIndex = stopIndex ?: current.boardingStopIndex,
                    matchedByPhone = candidateIndex >= 0,
                    probableMatch = candidateIndex < 0,
                )
            } else {
                rows += EnhancedPassengerCardRow(
                    name = booking.passengerName.trim(),
                    phone = phone,
                    seats = booking.seats,
                    boarding = boarding,
                    dropoff = dropoff,
                    sources = setOf(booking.source),
                    passengerId = booking.passengerId.takeIf(String::isNotBlank),
                    localBookingId = booking.id,
                    bookingStatus = booking.status,
                    operationalStatus = booking.operationalStatus,
                    paymentStatus = booking.paymentStatus,
                    lastDriverSelection = booking.lastDriverSelection,
                    fareMinorUnits = privateMetadata0494?.fareMinorUnits ?: booking.fareMinorUnits,
                    fareCurrencyCode = privateMetadata0494?.fareCurrencyCode?.takeIf(String::isNotBlank)
                        ?: booking.fareCurrencyCode,
                    boardingAddress = privateMetadata0494?.boardingAddress?.takeIf(String::isNotBlank)
                        ?: booking.boardingAddress,
                    dropoffAddress = privateMetadata0494?.dropoffAddress?.takeIf(String::isNotBlank)
                        ?: booking.dropoffAddress,
                    boardingStopIndex = stopIndex,
                )
            }
        }
    }

    return rows
        .filter { it.name.isNotBlank() }
        .sortedWith(
            compareBy<EnhancedPassengerCardRow> { it.boardingStopIndex == null }
                .thenBy { it.boardingStopIndex ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
}

internal fun passengerTimelinePlaceLabel(passengerName: String, raw: String?): String? {
    val value = raw
        ?.replace('\u00A0', ' ')
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val expectedName = enhancedPassengerNameKey(passengerName)
    if (expectedName.isBlank()) return value

    val separators = charArrayOf(',', '•', '·', ':', ';')
    separators.forEach { separator ->
        val index = value.indexOf(separator)
        if (index <= 0 || index >= value.lastIndex) return@forEach
        val left = value.substring(0, index)
            .trim()
            .replace(Regex("""\s*\(\s*\d+\s*\)\s*$"""), "")
            .trim()
        if (enhancedPassengerNameKey(left) == expectedName) {
            return value.substring(index + 1).trim().takeIf(String::isNotEmpty)
        }
    }
    return value
}

internal fun passengerTimelineCompactPlace(raw: String?, maxLength: Int = 18): String {
    val place = raw
        ?.replace('\u00A0', ' ')
        ?.substringBefore(',')
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()
    if (place.isBlank()) return "Pendente"
    if (place.length <= maxLength) return place

    val words = place.split(' ').filter(String::isNotBlank)
    if (words.size > 2) {
        val candidate = buildString {
            append(words.take(2).joinToString(" "))
            words.drop(2).forEach { word ->
                append(' ')
                append(if (word.length <= 2) word else "${word.first()}.")
            }
        }
        if (candidate.length <= maxLength) return candidate
    }

    val safeLength = maxLength.coerceAtLeast(4)
    return place.take(safeLength - 1).trimEnd() + "…"
}

internal fun passengerTimelineFareClipboardText(
    amountMinorUnits: Long,
    currencyCode: String,
    localeTag: String,
): String = PassengerMoney.formatMinorUnits(amountMinorUnits, currencyCode, localeTag)

internal fun passengerAddressEditorInitialValue(savedAddress: String?, collectedPlace: String?): String =
    savedAddress?.trim()?.takeIf(String::isNotEmpty)
        ?: collectedPlace?.trim()?.takeIf(String::isNotEmpty)
        ?: ""

@Composable
private fun PassengerAddressEditorDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    val normalized = value.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(240) },
                    label = { Text("Rua, número, bairro, cidade") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Este endereço pertence a esta reserva/viagem. Os atalhos 📍/🏁 abrem o GPS usando o endereço completo salvo quando ele estiver disponível.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized.isNotBlank(),
                onClick = { onSave(normalized) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ManualPassengerOccupancyEditorDialog(
    trip: Trip,
    booking: Booking,
    @Suppress("UNUSED_PARAMETER") existingBookings: List<Booking>,
    onDismiss: () -> Unit,
    onSave: (Booking) -> Unit,
    onError: (String) -> Unit,
) {
    val stops = trip.stops.sortedBy(TripStop::order)
    var seats by remember(booking.id) { mutableStateOf(booking.seats) }
    var fromId by remember(booking.id) { mutableStateOf(booking.boardingStopId) }
    var toId by remember(booking.id) { mutableStateOf(booking.dropoffStopId) }
    var fromOpen by remember(booking.id) { mutableStateOf(false) }
    var toOpen by remember(booking.id) { mutableStateOf(false) }
    val fromIndex = stops.indexOfFirst { it.id == fromId }
    val toIndex = stops.indexOfFirst { it.id == toId }
    val valid = fromIndex >= 0 && toIndex > fromIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar lugares / trecho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(booking.passengerName)
                Column {
                    OutlinedButton(onClick = { fromOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Embarque: ${stops.firstOrNull { it.id == fromId }?.name ?: "Selecionar"}")
                    }
                    DropdownMenu(expanded = fromOpen, onDismissRequest = { fromOpen = false }) {
                        stops.dropLast(1).forEach { stop ->
                            DropdownMenuItem(
                                text = { Text(stop.name) },
                                onClick = {
                                    fromId = stop.id
                                    if (stops.indexOfFirst { it.id == toId } <= stop.order) toId = ""
                                    fromOpen = false
                                },
                            )
                        }
                    }
                }
                Column {
                    OutlinedButton(enabled = fromIndex >= 0, onClick = { toOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Destino: ${stops.firstOrNull { it.id == toId }?.name ?: "Selecionar"}")
                    }
                    DropdownMenu(expanded = toOpen, onDismissRequest = { toOpen = false }) {
                        stops.filterIndexed { index, _ -> fromIndex >= 0 && index > fromIndex }.forEach { stop ->
                            DropdownMenuItem(text = { Text(stop.name) }, onClick = { toId = stop.id; toOpen = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (seats > 1) seats-- }) { Text("−") }
                    Text(if (seats == 1) "1 lugar" else "$seats lugares")
                    OutlinedButton(onClick = { if (seats < trip.capacity) seats++ }) { Text("+") }
                }
                Text(
                    if (valid) {
                        "A capacidade deste trecho será validada pelo backend canônico ao salvar."
                    } else {
                        "Selecione embarque e destino."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && seats in 1..999,
                onClick = {
                    runCatching {
                        booking.copy(
                            boardingStopId = fromId,
                            dropoffStopId = toId,
                            seats = seats,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    }.onSuccess(onSave).onFailure { onError(it.message ?: "Não foi possível preparar a alteração.") }
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PassengerFareEditorDialog(
    row: EnhancedPassengerCardRow,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
) {
    val context = LocalContext.current
    val spec = remember(context) { PassengerMoney.spec(context) }
    var value by remember(row.localBookingId, row.externalReservationKey) { mutableStateOf("") }
    val parsed = PassengerMoney.parseMinorUnits(value, spec)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${row.name} • ${passengerTimelineCompactPlace(row.boarding)} → ${passengerTimelineCompactPlace(row.dropoff)}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(32) },
                    label = { Text(if (spec.currencyCode.isBlank()) "Valor" else "Valor (${spec.currencyCode})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isNotBlank() && parsed == null) {
                    Text("Valor inválido.", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Ao salvar, o valor é copiado. O preço geral da viagem não é usado como valor individual.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let { onSave(it, spec.currencyCode) } },
            ) { Text("Salvar e copiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun canonicalBookingPrivateMetadataKey0494(bookingId: String): String =
    "canonical-booking-private:" + bookingId.trim()

/**
 * Private driver annotation only. It never changes canonical trip/booking capacity,
 * status, identity or public projection.
 */
private fun savePassengerAddress(
    row: EnhancedPassengerCardRow,
    addressRaw: String,
    boarding: Boolean,
    @Suppress("UNUSED_PARAMETER") store: TripStore,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val address = addressRaw.trim().takeIf(String::isNotEmpty) ?: return false
    val key = row.localBookingId?.takeIf(String::isNotBlank)?.let(::canonicalBookingPrivateMetadataKey0494)
        ?: row.externalReservationKey
        ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(
        if (boarding) current.copy(boardingAddress = address) else current.copy(dropoffAddress = address),
    )
    return true
}

/** Private driver annotation; fare is not used as Timeline capacity/identity authority. */
private fun savePassengerFare(
    row: EnhancedPassengerCardRow,
    amount: Long,
    currency: String,
    @Suppress("UNUSED_PARAMETER") store: TripStore,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val key = row.localBookingId?.takeIf(String::isNotBlank)?.let(::canonicalBookingPrivateMetadataKey0494)
        ?: row.externalReservationKey
        ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(current.copy(fareMinorUnits = amount, fareCurrencyCode = currency))
    return true
}

/** Legacy-only metadata link for rows that genuinely have no canonical booking id. */
private fun linkPassengerProfileLegacyMetadata0494(
    row: EnhancedPassengerCardRow,
    passengerId: String,
    passengerStore: PassengerIdentityStore,
): Boolean {
    val key = row.externalReservationKey ?: return false
    val current = passengerStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
    passengerStore.saveExternalMetadata(current.copy(passengerId = passengerId))
    return true
}

internal fun passengerConfirmationMessage(
    entry: TripTimelineEntry,
    row: EnhancedPassengerCardRow,
    context: Context? = null,
): String {
    val zone = java.time.ZoneId.systemDefault()
    val departure = java.time.Instant.ofEpochMilli(entry.departureAtMillis).atZone(zone)
    val date = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(departure)
    val origin = row.boarding?.trim()?.takeIf(String::isNotEmpty) ?: entry.origin.trim()
    val destination = row.dropoff?.trim()?.takeIf(String::isNotEmpty) ?: entry.destination.trim()
    val lines = mutableListOf<String>()
    lines += "Olá, ${row.name.ifBlank { "passageiro" }}! Sua viagem está confirmada ✅"
    lines += ""
    if (origin.isNotBlank() && destination.isNotBlank()) lines += "🚗 $origin → $destination"
    lines += "📅 $date"
    lines += if (row.seats == 1) "💺 1 vaga" else "💺 ${row.seats} vagas"
    if (row.fareMinorUnits != null && context != null) {
        val formatted = passengerTimelineFareClipboardText(row.fareMinorUnits, row.fareCurrencyCode, PassengerMoney.spec(context).localeTag)
        lines += "💰 $formatted"
    }
    lines += ""
    lines += "Quando eu estiver a caminho, envio a localização. 👍"
    return lines.joinToString("\n")
}

private fun copyPassengerConfirmationMessage(context: Context, entry: TripTimelineEntry, row: EnhancedPassengerCardRow) {
    val message = passengerConfirmationMessage(entry, row, context)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Confirmação da viagem", message))
    UnifiedDebugEventStore.record(
        "PASSENGER_CONFIRMATION_MESSAGE_COPIED",
        context.packageName,
        "timeline=true seats=${row.seats} farePresent=${row.fareMinorUnits != null} boardingPresent=${row.boarding?.isNotBlank() == true} dropoffPresent=${row.dropoff?.isNotBlank() == true}",
    )
    Toast.makeText(context, "Mensagem de confirmação copiada.", Toast.LENGTH_SHORT).show()
}

private fun copyPassengerFareValue(context: Context, row: EnhancedPassengerCardRow) {
    val amount = row.fareMinorUnits ?: return
    val localeTag = PassengerMoney.spec(context).localeTag
    val formatted = passengerTimelineFareClipboardText(amount, row.fareCurrencyCode, localeTag)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Valor da reserva", formatted))
    Toast.makeText(context, "Valor copiado: $formatted", Toast.LENGTH_SHORT).show()
}

internal data class ExternalTripTarget(val profileUuid: String, val href: String)

internal fun externalTripTarget(profileUuid: String?, href: String?): ExternalTripTarget? {
    val profile = profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    if (!CANONICAL_PROFILE_UUID.matches(profile)) return null
    val rawHref = href?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { java.net.URI(rawHref) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != "www.blablacar.com.br") return null
    val path = uri.path.orEmpty().trimEnd('/')
    val pathIdentity = Regex(
        "^/rides/offer/(?!edit(?:/|$)|passenger(?:/|$))[^/?#]+$",
        RegexOption.IGNORE_CASE,
    ).matches(path) || Regex("^/trip/[^/?#]+$", RegexOption.IGNORE_CASE).matches(path)
    val rawQuery = uri.rawQuery.orEmpty()
    val queryId = rawQuery.split('&')
        .firstOrNull { it.substringBefore('=') == "id" }
        ?.substringAfter('=', "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val queryIdentity = path in setOf("/rides/offer", "/trip") && queryId != null
    if (!pathIdentity && !queryIdentity) return null
    val keptQuery = rawQuery.split('&')
        .filter(String::isNotBlank)
        .filterNot { it.substringBefore('=') == "search_uuid" }
        .joinToString("&")
        .takeIf(String::isNotBlank)
    val canonicalHref = runCatching {
        java.net.URI(uri.scheme, uri.authority, uri.path, keptQuery, null).toString()
    }.getOrNull() ?: return null
    return ExternalTripTarget(profileUuid = profile, href = canonicalHref)
}

internal fun hasExternalTripActionEvidence(entry: TripTimelineEntry): Boolean =
    entry.sourcePassengerSeats[BookingSource.BLABLACAR]?.let { it > 0 } == true ||
        !entry.blablaTripId.isNullOrBlank() ||
        !entry.blablaTripHref.isNullOrBlank() ||
        !entry.blablaPublicHref.isNullOrBlank() ||
        !entry.blablaProfileUuid.isNullOrBlank()

@Composable
private fun TripBlaBlaTripActionRow(
    entry: TripTimelineEntry,
    onAddManualPassenger: (() -> Unit)?,
) {
    val context = LocalContext.current
    val target = BlaBlaReliableSeatSyncBridge.targetForTimeline(entry)
    val seatStateStore = remember(context) { BlaBlaPublicationSeatSyncStateStore(context) }
    val seatState = target?.let { seatStateStore.get(it.profileUuid, it.tripId) }
    val seatLabel = when (seatState?.state) {
        BlaBlaPublicationSeatSyncVisualState.SYNCING -> "💺⏳"
        BlaBlaPublicationSeatSyncVisualState.SYNCED -> "💺✅"
        BlaBlaPublicationSeatSyncVisualState.PENDING -> "💺⚠️"
        BlaBlaPublicationSeatSyncVisualState.ERROR -> "💺❌"
        BlaBlaPublicationSeatSyncVisualState.AVAILABLE, null -> "💺🔄"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (onAddManualPassenger != null) {
            TextButton(
                onClick = {
                    UnifiedDebugEventStore.record(
                        "AGENDA_CARD_MANUAL_PASSENGER_OPEN",
                        context.packageName,
                        "timeline=true externalPublication=true",
                    )
                    onAddManualPassenger()
                },
                contentPadding = COMPACT_ACTION_PADDING,
            ) { Text("👤➕") }
        }
        val canonicalPublicHref0490 = canonicalTimelineBlaBlaPublicHref0490(entry)
        if (canonicalPublicHref0490 != null) {
            TextButton(
                onClick = {
                    if (!openPublicTripBlaBla(context, canonicalPublicHref0490)) {
                        Toast.makeText(
                            context,
                            "A URL pública canônica desta viagem ainda não está disponível.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                contentPadding = COMPACT_ACTION_PADDING,
            ) { Text("🔗 Público") }
        }
        IconButton(
            onClick = {
                if (!openExternalTripBlaBla(context, entry.blablaProfileUuid, entry.blablaTripHref)) {
                    Toast.makeText(
                        context,
                        "Link direto da viagem indisponível. A referência será recuperada pela atualização automática quando houver evidência suficiente.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_blablacar_action),
                contentDescription = "Abrir viagem no BlaBlaCar",
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp),
            )
        }
        }
    }
}

internal data class PassengerPickupMapTarget(val query: String)

internal fun passengerPickupMapTarget(row: EnhancedPassengerCardRow): PassengerPickupMapTarget? {
    val exact = row.boardingAddress.trim().takeIf(String::isNotEmpty)
    val collected = row.boarding?.trim()?.takeIf(String::isNotEmpty)
    val query = exact ?: collected ?: return null
    return PassengerPickupMapTarget(query)
}

internal fun passengerDropoffMapTarget(row: EnhancedPassengerCardRow): PassengerPickupMapTarget? {
    val exact = row.dropoffAddress.trim().takeIf(String::isNotEmpty)
    val collected = row.dropoff?.trim()?.takeIf(String::isNotEmpty)
    val query = exact ?: collected ?: return null
    return PassengerPickupMapTarget(query)
}

internal data class ExternalPassengerTarget(val profileUuid: String, val href: String)

internal fun externalPassengerTarget(row: EnhancedPassengerCardRow): ExternalPassengerTarget? {
    val profileUuid = row.externalProfileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
    val href = row.externalBookingHref?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!href.startsWith("https://www.blablacar.com.br/") || !href.contains("/rides/offer/passenger/")) return null
    return ExternalPassengerTarget(profileUuid = profileUuid, href = href)
}

private fun openPublicTripBlaBla(context: Context, canonicalHref: String?): Boolean {
    val target = canonicalHref?.trim()?.takeIf(String::isNotBlank) ?: return false
    UnifiedDebugEventStore.record(
        "BLABLACAR_PUBLIC_TRIP_OPEN_EXPLICIT",
        context.packageName,
        "timeline=true canonical_public_href=true",
    )
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}

private fun openExternalTripBlaBla(context: Context, profileUuid: String?, href: String?): Boolean {
    val target = externalTripTarget(profileUuid, href) ?: return false
    val account = BlaBlaDynamicAccountRegistry(context).list()
        .firstOrNull { it.profileUuid?.trim()?.lowercase() == target.profileUuid }
        ?: return false
    UnifiedDebugEventStore.record(
        "BLABLACAR_TRIP_OPEN_EXPLICIT",
        context.packageName,
        "timeline=true profile_uuid=${target.profileUuid} href_present=true",
    )
    context.startActivity(
        BlaBlaDynamicSessionIntents.manage(context, account, target.href)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}

private fun openPassengerPickupMap(context: Context, target: PassengerPickupMapTarget) {
    val uri = Uri.parse("geo:0,0?q=${Uri.encode(target.query)}")
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri)
        .setPackage("com.google.android.apps.maps")
        .addFlags(flags)
    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(flags)
    UnifiedDebugEventStore.record(
        "PASSENGER_PICKUP_MAP_OPEN",
        context.packageName,
        "timeline=true exact_or_collected_pickup=true",
    )
    runCatching { context.startActivity(mapsIntent) }
        .recoverCatching { context.startActivity(fallbackIntent) }
        .onFailure {
            Toast.makeText(context, "Não foi possível abrir o local de embarque.", Toast.LENGTH_LONG).show()
        }
}

private fun openPassengerDropoffMap(context: Context, target: PassengerPickupMapTarget) {
    val uri = Uri.parse("geo:0,0?q=${Uri.encode(target.query)}")
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri)
        .setPackage("com.google.android.apps.maps")
        .addFlags(flags)
    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(flags)
    UnifiedDebugEventStore.record(
        "PASSENGER_DROPOFF_MAP_OPEN",
        context.packageName,
        "timeline=true exact_or_collected_dropoff=true",
    )
    runCatching { context.startActivity(mapsIntent) }
        .recoverCatching { context.startActivity(fallbackIntent) }
        .onFailure {
            Toast.makeText(context, "Não foi possível abrir o local de destino.", Toast.LENGTH_LONG).show()
        }
}

private fun openExternalPassengerBlaBla(context: Context, row: EnhancedPassengerCardRow): Boolean {
    val target = externalPassengerTarget(row) ?: return false
    val account = BlaBlaDynamicAccountRegistry(context).list()
        .firstOrNull { it.profileUuid?.trim()?.lowercase() == target.profileUuid }
        ?: return false
    context.startActivity(
        BlaBlaDynamicSessionIntents.manage(context, account, target.href)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}

internal fun openPassengerWhatsApp(context: Context, raw: String) {
    val digits = raw.filter(Char::isDigit)
    if (digits.length !in 8..15) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "Não foi possível abrir o WhatsApp.", Toast.LENGTH_LONG).show()
    }
}

private fun enhancedPassengerNameKey(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun enhancedRouteEvidenceMatches(
    aBoard: String?,
    aDrop: String?,
    bBoard: String?,
    bDrop: String?,
): Boolean {
    if (aBoard.isNullOrBlank() || aDrop.isNullOrBlank() || bBoard.isNullOrBlank() || bDrop.isNullOrBlank()) return false
    return enhancedPlaceKey(aBoard) == enhancedPlaceKey(bBoard) &&
        enhancedPlaceKey(aDrop) == enhancedPlaceKey(bDrop)
}

private fun enhancedPlaceKey(raw: String): String = Normalizer.normalize(raw.substringBefore(',').trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun enhancedSourceShort(source: BookingSource): String = when (source) {
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.OTHER -> "Outro"
}

private val CANONICAL_PROFILE_UUID = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
)

private val COMPACT_ACTION_PADDING = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
private val COMPACT_NAME_PADDING = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
private val ADDRESS_PLACE_PADDING = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
private val ADDRESS_ICON_PADDING = PaddingValues(0.dp)
