package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.SettingsRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

class TripsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 304)
        }
        TripShortcutInstaller.installDynamic(this)
        setContent {
            MaterialTheme {
                TripApp(
                    activity = this,
                    startCreating = intent?.action == TripActions.ACTION_NEW_TRIP,
                    initialTripId = intent?.getStringExtra(TripActions.EXTRA_TRIP_ID),
                )
            }
        }
    }
}

private enum class TripScreen { LIST, TIMELINE, CREATE, SETTINGS }

@Composable
private fun TripApp(
    activity: ComponentActivity,
    startCreating: Boolean,
    initialTripId: String?,
) {
    val store = remember { TripStore(activity) }
    val settingsRepository = remember(activity) { SettingsRepository(activity) }
    val appSettings by settingsRepository.settings.collectAsState(initial = AppSettings())
    var trips by remember { mutableStateOf(store.trips()) }
    var bookings by remember { mutableStateOf(store.bookings()) }
    var autoBlaBlaSyncToken by remember { mutableStateOf(0) }
    var publicAgendaSyncRevision by remember { mutableStateOf(0) }
    var screen by remember {
        mutableStateOf(
            when {
                startCreating -> TripScreen.CREATE
                initialTripId != null -> TripScreen.LIST
                else -> TripScreen.TIMELINE
            },
        )
    }
    var selectedId by remember { mutableStateOf(initialTripId) }
    var message by remember { mutableStateOf<String?>(null) }
    val shareScope = rememberCoroutineScope()
    val refresh = {
        trips = store.trips()
        bookings = store.bookings()
        TripWidgetProvider.updateAll(activity)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val result = PublicBookingRemoteSync0296.pullAndReconcile(activity, store)
        if (result.importedCount > 0) {
            refresh()
            publicAgendaSyncRevision++
            message = "${result.importedCount} reserva(s) recebida(s) pelo link público."
            if (result.seatSyncQueued > 0) autoBlaBlaSyncToken++
        }
    }

    androidx.compose.runtime.LaunchedEffect(appSettings.vehicleCapacity, publicAgendaSyncRevision) {
        val online = store.onlineSettings()
        if (online.configured) {
            val result = PublicAgendaAutoSync0300.sync(
                context = activity,
                store = store,
                configuredVehicleCapacity = appSettings.vehicleCapacity,
            )
            runCatching { BookingPushRegistration0304.ensureRegistered(activity, store) }
            if (result.localPublished + result.externalPublished > 0) {
                refresh()
                message = "Agenda pública atualizada: ${result.localPublished + result.externalPublished} viagem(ns) • ${result.seatClaimsSynced} ocupação(ões) sincronizada(s)."
            } else if (result.failures > 0) {
                message = "Não foi possível enviar as viagens para a Agenda Pública. Tente abrir a Agenda novamente."
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Agenda de Viagens", style = MaterialTheme.typography.headlineSmall)
            Text("Rota Certa • viagens, vagas por trecho e calendário", style = MaterialTheme.typography.bodySmall)
            message?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp))
                }
            }
            when (screen) {
                TripScreen.CREATE -> TripEditor(
                    defaultOrigin = appSettings.tripDepartureAddress,
                    defaultCapacity = appSettings.vehicleCapacity,
                    onCancel = { screen = TripScreen.TIMELINE },
                    onSave = { trip ->
                        store.saveTrip(trip)
                        refresh()
                        publicAgendaSyncRevision++
                        selectedId = trip.id
                        screen = TripScreen.TIMELINE
                        message = "Viagem criada. Publique quando estiver pronta."
                    },
                )
                TripScreen.TIMELINE -> TripTimelineScreen(
                    trips = trips,
                    bookings = bookings,
                    store = store,
                    onChanged = { text -> refresh(); publicAgendaSyncRevision++; message = text },
                    autoSyncToken = autoBlaBlaSyncToken,
                    onRequestBlaBlaSync = { autoBlaBlaSyncToken++ },
                    onCreateTrip = { screen = TripScreen.CREATE },
                    onPinShortcut = {
                        val requested = TripShortcutInstaller.requestPinnedCreateShortcut(activity)
                        message = if (requested) "Pedido de atalho enviado ao Android." else "O launcher não permite fixar atalhos automaticamente."
                    },
                    onOpenOnlineSettings = { screen = TripScreen.SETTINGS },
                    onManageLocal = { tripId ->
                        selectedId = tripId
                        screen = TripScreen.LIST
                    },
                    onBack = { activity.finish() },
                )
                TripScreen.SETTINGS -> OnlineSettingsEditor(
                    initial = store.onlineSettings(),
                    onSave = { saved ->
                        store.saveOnlineSettings(saved)
                        screen = TripScreen.TIMELINE
                        if (saved.configured) {
                            message = "Salvando Integração online…"
                            shareScope.launch {
                                runCatching {
                                    val response = TripRemoteApi(saved).ensurePublicAgenda(saved.publicCalendarToken)
                                    val validated = saved.copy(
                                        publicCalendarToken = response.publicAgendaToken,
                                        driverDisplayName = response.displayName.ifBlank { saved.driverDisplayName },
                                        driverUsername = response.username.ifBlank { saved.driverUsername },
                                    )
                                    store.saveOnlineSettings(validated)
                                    validated
                                }.onSuccess {
                                    message = "Integração online salva e perfil público atualizado."
                                }.onFailure {
                                    message = "Configuração salva no aparelho, mas o perfil público ainda não sincronizou: ${it.message ?: "erro de conexão"}"
                                }
                            }
                        } else {
                            message = "Configuração salva; modo online ainda desativado."
                        }
                    },
                    onCancel = { screen = TripScreen.TIMELINE },
                )
                TripScreen.LIST -> {
                    OutlinedButton(onClick = { screen = TripScreen.TIMELINE }) {
                        Text("Voltar à Timeline")
                    }
                    val onlineSettings = store.onlineSettings()
                    if (onlineSettings.publicAgendaUrl != null) {
                        OutlinedButton(onClick = {
                            if (!onlineSettings.configured) {
                                message = "A integração online precisa da chave privada do motorista antes de compartilhar."
                            } else {
                                message = "Validando seu link público…"
                                shareScope.launch {
                                    runCatching {
                                        val response = TripRemoteApi(onlineSettings).ensurePublicAgenda(onlineSettings.publicCalendarToken)
                                        val validated = onlineSettings.copy(
                                            publicCalendarToken = response.publicAgendaToken,
                                            driverDisplayName = response.displayName.ifBlank { onlineSettings.driverDisplayName },
                                            driverUsername = response.username.ifBlank { onlineSettings.driverUsername },
                                        )
                                        store.saveOnlineSettings(validated)
                                        response to validated
                                    }.onSuccess { (response, validated) ->
                                        if (TripCalendarBridge.sharePublicAgenda(activity, validated)) {
                                            message = if (response.repaired) {
                                                "Link da Agenda Pública corrigido no servidor e pronto para compartilhar."
                                            } else {
                                                "Link da Agenda Pública validado e pronto para compartilhar."
                                            }
                                        } else {
                                            message = "Não foi possível montar o link público validado."
                                        }
                                    }.onFailure {
                                        message = "Não foi possível validar o link público: ${it.message ?: "erro de conexão"}"
                                    }
                                }
                            }
                        }) { Text("Compartilhar minha agenda") }
                    }
                    if (onlineSettings.googleCalendarMirrorUrl != null) {
                        OutlinedButton(onClick = {
                            if (TripCalendarBridge.shareGoogleCalendarFallback(activity, onlineSettings)) message = "Link do Google Agenda pronto para compartilhar."
                        }) { Text("Compartilhar Google Agenda") }
                    }
                    if (trips.isEmpty()) {
                        Text("Nenhuma viagem local neste aparelho. A Timeline continua exibindo publicações sincronizadas.")
                    } else {
                        trips.sortedBy { it.departureAtMillis }.forEach { trip ->
                            TripCard(
                                activity = activity,
                                store = store,
                                trip = trip,
                                expanded = selectedId == trip.id,
                                onToggle = { selectedId = if (selectedId == trip.id) null else trip.id },
                                onChanged = { text -> refresh(); publicAgendaSyncRevision++; message = text },
                                onRequestBlaBlaSync = {
                                    autoBlaBlaSyncToken++
                                    screen = TripScreen.TIMELINE
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripEditor(
    defaultOrigin: String,
    defaultCapacity: Int,
    onCancel: () -> Unit,
    onSave: (Trip) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
    val defaultTime = remember { LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0).format(formatter) }
    var origin by remember(defaultOrigin) { mutableStateOf(defaultOrigin.trim()) }
    var destination by remember { mutableStateOf("") }
    var intermediate by remember { mutableStateOf("") }
    var departure by remember { mutableStateOf(defaultTime) }
    var capacity by remember(defaultCapacity) {
        mutableStateOf(defaultCapacity.takeIf { it in 1..999 }?.toString().orEmpty())
    }
    var notes by remember { mutableStateOf("") }
    var segmentPrices by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var routePlan by remember { mutableStateOf<TripRoutePlan?>(null) }

    Text("Criar viagem", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(origin, { origin = it }, label = { Text("Origem") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(destination, { destination = it }, label = { Text("Destino") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        intermediate,
        { intermediate = it },
        label = { Text("Paradas intermediárias — uma por linha") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    OutlinedTextField(
        segmentPrices,
        { segmentPrices = it },
        label = { Text("Valores por trecho em R$ — uma linha por trecho") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    Text("Ex.: origem → parada = 20,00; parada → destino = 25,00. Deixe vazio para não publicar valor.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(departure, { departure = it }, label = { Text("Saída — dd/MM/aaaa HH:mm") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit).take(3) }, label = { Text("Capacidade do veículo") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(notes, { notes = it }, label = { Text("Observações públicas opcionais") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    val planningNames = buildList {
        if (origin.isNotBlank()) add(origin.trim())
        addAll(intermediate.lines().map(String::trim).filter(String::isNotBlank))
        if (destination.isNotBlank()) add(destination.trim())
    }
    val planningDepartureMillis = runCatching {
        LocalDateTime.parse(departure.trim(), formatter)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
    TripRoutePlannerControl(
        stopNames = planningNames,
        departureAtMillis = planningDepartureMillis,
        onPlan = { routePlan = it },
    )
    error?.let { Text(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            runCatching {
                require(origin.isNotBlank()) { "Informe a origem." }
                require(destination.isNotBlank()) { "Informe o destino." }
                val seats = capacity.toIntOrNull() ?: throw IllegalArgumentException("Informe uma quantidade de vagas válida.")
                require(seats in 1..999) { "Informe uma capacidade entre 1 e 999 lugares." }
                val departureMillis = LocalDateTime.parse(departure.trim(), formatter)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val names = buildList {
                    add(origin.trim())
                    addAll(intermediate.lines().map(String::trim).filter(String::isNotBlank))
                    add(destination.trim())
                }
                require(names.size >= 2) { "A viagem precisa de origem e destino." }
                val rawPrices = segmentPrices.lines().map(String::trim).filter(String::isNotBlank)
                val prices = if (rawPrices.isEmpty()) List(names.size - 1) { 0L } else {
                    require(rawPrices.size == names.size - 1) { "Informe exatamente ${names.size - 1} valor(es), um para cada trecho." }
                    rawPrices.map { raw -> parseFareCents(raw) ?: throw IllegalArgumentException("Valor inválido: $raw") }
                }
                val planned = routePlan?.takeIf { plan ->
                    plan.stops.map(TripStop::name) == names &&
                        plan.stops.firstOrNull()?.plannedDepartureMillis == departureMillis
                }
                val stops = (planned?.stops ?: names.mapIndexed { index, name ->
                    TripStop(
                        order = index,
                        name = name,
                        address = name,
                        plannedDepartureMillis = if (index == 0) departureMillis else null,
                        plannedArrivalMillis = if (index == 0) departureMillis else null,
                    )
                }).mapIndexed { index, stop ->
                    stop.copy(priceToNextCents = prices.getOrElse(index) { 0L })
                }
                Trip(
                    title = "${origin.trim()} → ${destination.trim()}",
                    departureAtMillis = departureMillis,
                    capacity = seats,
                    stops = stops,
                    notes = notes.trim(),
                )
            }.onSuccess(onSave).onFailure { error = it.message ?: "Não foi possível criar a viagem." }
        }) { Text("Salvar rascunho") }
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }
}

@Composable
private fun TripCard(
    activity: ComponentActivity,
    store: TripStore,
    trip: Trip,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChanged: (String) -> Unit,
    onRequestBlaBlaSync: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
    val scope = rememberCoroutineScope()
    val bookings = store.bookingsFor(trip.id)
    val seatRange = SeatAvailabilityEngine.availableSeatRange(trip, bookings)
    val availabilityText = if (seatRange.variesBySegment) {
        "vagas por trecho ${seatRange.minimum}–${seatRange.maximum}/${trip.capacity}"
    } else {
        "${seatRange.maximum}/${trip.capacity} vagas livres"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleMedium)
            Text("${formatter.format(Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault()))} • ${trip.status} • $availabilityText")
            OutlinedButton(onClick = onToggle) { Text(if (expanded) "Fechar" else "Gerenciar") }
            if (expanded) {
                HorizontalDivider()
                trip.stops.sortedBy(TripStop::order).forEachIndexed { index, stop ->
                    Text("${index + 1}. ${stop.name}")
                }
                val loads = SeatAvailabilityEngine.segmentLoads(trip, bookings)
                if (loads.isNotEmpty()) {
                    Text("Ocupação por trecho", style = MaterialTheme.typography.titleSmall)
                    loads.forEach { load ->
                        val price = load.from.priceToNextCents
                        Text(buildString {
                            append("${load.from.name} → ${load.to.name}: ${load.occupiedSeats}/${trip.capacity} ocupadas")
                            if (price > 0L) append(" • ${formatFare(price)} por pessoa")
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trip.status == TripStatus.DRAFT) {
                        Button(onClick = {
                            store.saveTrip(trip.copy(status = TripStatus.PUBLISHED))
                            onChanged("Viagem publicada localmente.")
                        }) { Text("Publicar") }
                    }
                    if (trip.status !in setOf(TripStatus.CANCELLED, TripStatus.COMPLETED)) {
                        OutlinedButton(onClick = {
                            store.saveTrip(trip.copy(status = TripStatus.CANCELLED))
                            onChanged("Viagem cancelada.")
                        }) { Text("Cancelar viagem") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { TripCalendarBridge.shareTrip(activity, trip) }) { Text("Compartilhar") }
                    OutlinedButton(onClick = { TripCalendarBridge.addToDeviceCalendar(activity, trip) }) { Text("Google/Agenda") }
                    OutlinedButton(onClick = { TripCalendarBridge.shareIcs(activity, trip) }) { Text("ICS") }
                }
                val settings = store.onlineSettings()
                if (trip.status !in setOf(TripStatus.CANCELLED, TripStatus.COMPLETED)) {
                    OutlinedButton(onClick = {
                        val next = trip.copy(publicBookingEnabled = !trip.publicBookingEnabled)
                        store.saveTrip(next)
                        if (settings.configured && next.remoteId != null) {
                            scope.launch {
                                runCatching { TripRemoteApi(settings).update(next) }
                                    .onSuccess { onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas para esta viagem." else "Reservas pelo link desativadas para esta viagem.") }
                                    .onFailure { onChanged("Estado salvo no Rota Certa, mas ainda não sincronizado online: ${it.message}") }
                            }
                        } else {
                            onChanged(if (next.publicBookingEnabled) "Reservas pelo link ativadas localmente. Publique/sincronize online para compartilhar." else "Reservas pelo link desativadas.")
                        }
                    }) { Text(if (trip.publicBookingEnabled) "Reservas pelo link: ATIVADAS" else "Reservas pelo link: DESATIVADAS") }
                    if (trip.publicBookingEnabled && !trip.publicUrl.isNullOrBlank()) {
                        OutlinedButton(onClick = {
                            if (!TripPublicBookingLink0296.share(activity, trip.publicUrl.orEmpty())) {
                                onChanged("Link público ainda não está disponível.")
                            }
                        }) { Text("📲 Compartilhar reservas") }
                    }
                }
                if (settings.configured && trip.status != TripStatus.DRAFT && trip.status != TripStatus.CANCELLED) {
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                val response = if (trip.remoteId == null) TripRemoteApi(settings).publish(trip) else TripRemoteApi(settings).update(trip)
                                store.saveTrip(trip.copy(remoteId = response.tripId, publicToken = response.publicToken, publicUrl = response.publicUrl))
                            }.onSuccess { onChanged("Viagem sincronizada com a agenda pública.") }
                                .onFailure { onChanged("Falha online: ${it.message}") }
                        }
                    }) { Text(if (trip.remoteId == null) "Publicar online" else "Sincronizar online") }
                    if (trip.remoteId != null) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    TripRemoteApi(settings).listBookings(trip.remoteId).bookings
                                }.onSuccess { remoteBookings ->
                                    remoteBookings.forEach { remote ->
                                        store.saveBooking(remote.toLocalBooking(trip.id))
                                    }
                                    onChanged("Reservas online atualizadas: ${remoteBookings.size}.")
                                }.onFailure {
                                    onChanged("Falha ao atualizar reservas: ${it.message}")
                                }
                            }
                        }) { Text("Atualizar reservas online") }
                    }
                } else if (!settings.configured) {
                    Text("Modo online não configurado. Compartilhamento local, Google Agenda e ICS continuam funcionando.", style = MaterialTheme.typography.bodySmall)
                }
                if (trip.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL)) {
                    QuickPassengerPanel(trip, store, onChanged, onRequestBlaBlaSync)
                }
                if (bookings.isNotEmpty()) {
                    Text("Reservas locais", style = MaterialTheme.typography.titleSmall)
                    bookings.forEach { booking ->
                        val from = trip.stops.firstOrNull { it.id == booking.boardingStopId }?.name.orEmpty()
                        val to = trip.stops.firstOrNull { it.id == booking.dropoffStopId }?.name.orEmpty()
                        Text("${booking.passengerName}: $from → $to • ${booking.seats} vaga(s) • ${booking.status}")
                    }
                }
                TextButton(onClick = {
                    store.deleteTrip(trip.id)
                    onChanged("Viagem excluída do aparelho.")
                }) { Text("Excluir viagem") }
            }
        }
    }
}

@Composable
private fun ManualBookingEditor(
    trip: Trip,
    store: TripStore,
    onChanged: (String) -> Unit,
) {
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) return
    var name by remember(trip.id) { mutableStateOf("") }
    var contact by remember(trip.id) { mutableStateOf("") }
    var seatsText by remember(trip.id) { mutableStateOf("1") }
    var fromIndex by remember(trip.id) { mutableStateOf(0) }
    var toIndex by remember(trip.id) { mutableStateOf(stops.lastIndex) }
    val requested = seatsText.toIntOrNull()?.coerceIn(1, trip.capacity) ?: 1
    val availability = runCatching {
        SeatAvailabilityEngine.availability(trip, store.bookingsFor(trip.id), stops[fromIndex].id, stops[toIndex].id, requested)
    }.getOrNull()

    HorizontalDivider()
    Text("Adicionar passageiro manualmente", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(contact, { contact = it }, label = { Text("Contato opcional") }, modifier = Modifier.fillMaxWidth())
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
    OutlinedTextField(seatsText, { seatsText = it.filter(Char::isDigit).take(3) }, label = { Text("Lugares reservados") })
    Text("Disponíveis nesse trecho: ${availability?.availableSeats ?: 0}")
    val farePerSeat = runCatching { TripFareEngine.farePerSeatCents(trip, stops[fromIndex].id, stops[toIndex].id) }.getOrDefault(0L)
    if (farePerSeat > 0L) Text("Valor: ${formatFare(farePerSeat)} por pessoa • total ${formatFare(farePerSeat * requested.toLong())}")
    Button(
        enabled = name.isNotBlank() && availability?.canBook == true,
        onClick = {
            store.saveBooking(
                Booking(
                    tripId = trip.id,
                    passengerName = name.trim(),
                    passengerContact = contact.trim(),
                    boardingStopId = stops[fromIndex].id,
                    dropoffStopId = stops[toIndex].id,
                    seats = requested,
                    status = BookingStatus.CONFIRMED,
                ),
            )
            name = ""
            contact = ""
            seatsText = "1"
            onChanged("Passageiro adicionado sem ultrapassar a capacidade do trecho.")
        },
    ) { Text("Confirmar reserva") }
}

private fun parseFareCents(value: String): Long? {
    val normalized = value.trim().replace("R$", "", ignoreCase = true).replace(" ", "").replace(".", "").replace(",", ".")
    val amount = normalized.toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount < 0.0 || amount > 1_000_000.0) return null
    return (amount * 100.0).roundToLong()
}

private fun formatFare(cents: Long): String = String.format(Locale("pt", "BR"), "R$ %.2f", cents.coerceAtLeast(0L) / 100.0)

@Composable
private fun OnlineSettingsEditor(
    initial: TripOnlineSettings,
    onSave: (TripOnlineSettings) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val linkedProfiles = remember(context) { BlaBlaDynamicAccountRegistry(context).list() }
    var api by remember { mutableStateOf(initial.apiBaseUrl) }
    var publicBase by remember { mutableStateOf(initial.publicBaseUrl) }
    var token by remember { mutableStateOf(initial.driverToken) }
    var calendarToken by remember { mutableStateOf(initial.publicCalendarToken) }
    var driverName by remember { mutableStateOf(initial.driverDisplayName) }
    var driverUsername by remember { mutableStateOf(initial.driverUsername) }
    var driverWhatsapp by remember { mutableStateOf(initial.driverWhatsapp) }
    var driverPhotoUrl by remember { mutableStateOf(initial.driverPhotoUrl) }
    var driverAbout by remember { mutableStateOf(initial.driverPublicAbout) }
    var driverRating by remember { mutableStateOf(initial.driverPublicRating) }
    var driverReviewCount by remember { mutableStateOf(initial.driverPublicReviewCount.toString()) }
    var driverBadge by remember { mutableStateOf(initial.driverPublicBadge) }
    var vehicleMakeModel by remember { mutableStateOf(initial.vehicleMakeModel) }
    var vehicleColor by remember { mutableStateOf(initial.vehicleColor) }
    var vehicleAmenities by remember { mutableStateOf(initial.vehicleAmenities) }
    var driverPreferences by remember { mutableStateOf(initial.driverPreferences) }
    var paymentInstructions by remember { mutableStateOf(initial.paymentInstructions) }
    var googleCalendarUrl by remember { mutableStateOf(initial.googleCalendarPublicUrl) }
    var registrationMessage by remember { mutableStateOf<String?>(null) }
    val registrationScope = rememberCoroutineScope()
    Text("Integração online", style = MaterialTheme.typography.titleLarge)
    Text("Sem essas credenciais, nenhuma informação é enviada para servidor algum. O módulo local continua operacional.")
    Text("Conta do motorista", style = MaterialTheme.typography.titleMedium)
    Text(
        "Existe uma única conta de motorista. A chave privada, o token público e o link da agenda pertencem ao motorista e valem para todos os perfis vinculados.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(api, { api = it.trim() }, label = { Text("API HTTPS") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(publicBase, { publicBase = it.trim() }, label = { Text("URL pública") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverName, { driverName = it }, label = { Text("Nome público do motorista") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverUsername, { driverUsername = DriverIdentityRules.normalizeUsername(it) }, label = { Text("Nome de usuário no link") }, modifier = Modifier.fillMaxWidth(), enabled = token.isBlank())
    Text("Dados públicos mostrados ao passageiro", style = MaterialTheme.typography.titleMedium)
    Text("Preencha somente informações verdadeiras. Campos vazios não aparecem na Agenda Pública.")
    OutlinedTextField(driverWhatsapp, { driverWhatsapp = it.filter { ch -> ch.isDigit() || ch == '+' || ch == '(' || ch == ')' || ch == '-' || ch == ' ' }.take(24) }, label = { Text("WhatsApp do motorista — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverPhotoUrl, { driverPhotoUrl = it.trim().take(500) }, label = { Text("Foto pública do motorista — URL HTTPS opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverAbout, { driverAbout = it.take(320) }, label = { Text("Apresentação do motorista — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverRating, { driverRating = it.take(12) }, label = { Text("Nota pública — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverReviewCount, { driverReviewCount = it.filter(Char::isDigit).take(7) }, label = { Text("Quantidade de avaliações — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverBadge, { driverBadge = it.take(80) }, label = { Text("Selo ou destaque — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(vehicleMakeModel, { vehicleMakeModel = it.take(120) }, label = { Text("Veículo — marca/modelo — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(vehicleColor, { vehicleColor = it.take(60) }, label = { Text("Cor do veículo — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(vehicleAmenities, { vehicleAmenities = it.take(240) }, label = { Text("Comodidades — ex.: Ar-condicionado, USB") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverPreferences, { driverPreferences = it.take(240) }, label = { Text("Preferências — ex.: Não fumar, animais") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(paymentInstructions, { paymentInstructions = it.take(240) }, label = { Text("Pagamento — ex.: Pix ou dinheiro no carro") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(googleCalendarUrl, { googleCalendarUrl = it.trim() }, label = { Text("Link público do Google Agenda — opcional") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        token,
        { token = it },
        label = { Text("Chave privada do motorista") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        calendarToken,
        { calendarToken = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
        label = { Text("Token público da agenda de viagens") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text("Perfis vinculados", style = MaterialTheme.typography.titleMedium)
    Text(
        "Um motorista pode usar vários perfis. Cada perfil mantém sua identidade, sua posição e suas viagens de forma independente.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (linkedProfiles.isEmpty()) {
        Text("Nenhum perfil BlaBlaCar vinculado neste aparelho.", style = MaterialTheme.typography.bodySmall)
    } else {
        linkedProfiles.forEach { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(profile.displayLabel, style = MaterialTheme.typography.titleSmall)
                    val identityStatus = when {
                        !profile.profileName.isNullOrBlank() && !profile.profileUuid.isNullOrBlank() -> "${profile.profileName} • identidade vinculada"
                        !profile.profileUuid.isNullOrBlank() -> "Identidade vinculada"
                        else -> "Perfil aguardando confirmação de identidade"
                    }
                    Text(identityStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(
        "Vincular outro perfil não cria outro motorista e não gera outra chave privada ou outro token da agenda.",
        style = MaterialTheme.typography.bodySmall,
    )
    registrationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (token.isBlank()) {
        Button(enabled = api.startsWith("https://") && publicBase.startsWith("https://") && driverName.isNotBlank(), onClick = {
            val normalizedUsername = DriverIdentityRules.normalizeUsername(driverUsername.ifBlank { driverName })
            if (!DriverIdentityRules.isValidUsername(normalizedUsername)) {
                registrationMessage = "Escolha um nome de usuário com pelo menos 3 caracteres."
            } else {
                driverUsername = normalizedUsername
                registrationScope.launch {
                    val candidate = TripOnlineSettings(
                        apiBaseUrl = api.trimEnd('/'),
                        publicBaseUrl = publicBase.trimEnd('/'),
                        driverDisplayName = driverName.trim(),
                        driverUsername = normalizedUsername,
                        driverWhatsapp = driverWhatsapp.trim(),
                        driverPhotoUrl = driverPhotoUrl.trim(),
                        driverPublicAbout = driverAbout.trim(),
                        driverPublicRating = driverRating.trim(),
                        driverPublicReviewCount = driverReviewCount.toIntOrNull() ?: 0,
                        driverPublicBadge = driverBadge.trim(),
                        vehicleMakeModel = vehicleMakeModel.trim(),
                        vehicleColor = vehicleColor.trim(),
                        vehicleAmenities = vehicleAmenities.trim(),
                        driverPreferences = driverPreferences.trim(),
                        paymentInstructions = paymentInstructions.trim(),
                        googleCalendarPublicUrl = googleCalendarUrl.trim(),
                    )
                    runCatching { TripRemoteApi(candidate).registerDriver(driverName.trim(), normalizedUsername) }
                        .onSuccess { response ->
                            driverName = response.displayName
                            driverUsername = response.username
                            token = response.driverToken
                            calendarToken = response.publicAgendaToken
                            registrationMessage = "Link exclusivo gerado: ${response.publicAgendaUrl}"
                        }
                        .onFailure { registrationMessage = "Não foi possível gerar o link: ${it.message}" }
                }
            }
        }) { Text("Gerar meu link exclusivo") }
    } else {
        val preview = TripOnlineSettings(
            apiBaseUrl = api.trimEnd('/'),
            publicBaseUrl = publicBase.trimEnd('/'),
            driverToken = token,
            publicCalendarToken = calendarToken,
            driverDisplayName = driverName.trim(),
            driverUsername = driverUsername,
            driverWhatsapp = driverWhatsapp.trim(),
            driverPhotoUrl = driverPhotoUrl.trim(),
            driverPublicAbout = driverAbout.trim(),
            driverPublicRating = driverRating.trim(),
            driverPublicReviewCount = driverReviewCount.toIntOrNull() ?: 0,
            driverPublicBadge = driverBadge.trim(),
            vehicleMakeModel = vehicleMakeModel.trim(),
            vehicleColor = vehicleColor.trim(),
            vehicleAmenities = vehicleAmenities.trim(),
            driverPreferences = driverPreferences.trim(),
            paymentInstructions = paymentInstructions.trim(),
            googleCalendarPublicUrl = googleCalendarUrl.trim(),
        ).publicAgendaUrl
        preview?.let { Text("Seu link: $it", style = MaterialTheme.typography.bodySmall) }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            onSave(
                TripOnlineSettings(
                    apiBaseUrl = api.trimEnd('/'),
                    publicBaseUrl = publicBase.trimEnd('/'),
                    driverToken = token.trim(),
                    publicCalendarToken = calendarToken.trim(),
                    driverDisplayName = driverName.trim(),
                    driverUsername = DriverIdentityRules.normalizeUsername(driverUsername),
                    driverWhatsapp = driverWhatsapp.trim(),
                    driverPhotoUrl = driverPhotoUrl.trim(),
                    driverPublicAbout = driverAbout.trim(),
                    driverPublicRating = driverRating.trim(),
                    driverPublicReviewCount = driverReviewCount.toIntOrNull() ?: 0,
                    driverPublicBadge = driverBadge.trim(),
                    vehicleMakeModel = vehicleMakeModel.trim(),
                    vehicleColor = vehicleColor.trim(),
                    vehicleAmenities = vehicleAmenities.trim(),
                    driverPreferences = driverPreferences.trim(),
                    paymentInstructions = paymentInstructions.trim(),
                    googleCalendarPublicUrl = googleCalendarUrl.trim(),
                ),
            )
        }) { Text("Salvar") }
        TextButton(onClick = onCancel) { Text("Voltar") }
    }
}
