package br.com.mapeiaia.rotacerta.trips

import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.AppSettings
import br.com.mapeiaia.rotacerta.SettingsRepository
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
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

private enum class TripScreen { LIST, TIMELINE, CREATE, SETTINGS, PASSENGERS }

@OptIn(ExperimentalMaterial3Api::class)
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
    var forceAllBlaBlaSyncToken by remember { mutableStateOf(0) }
    var publicAgendaSyncRevision by remember { mutableStateOf(0) }
    var refreshAllRunning by remember { mutableStateOf(false) }
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
    val requestFullTimelineRefresh = {
        if (screen == TripScreen.TIMELINE && !refreshAllRunning) {
            refreshAllRunning = true
            message = "Sincronizando tudo: contas BlaBlaCar, reservas e Agenda Pública…"
            UnifiedDebugEventStore.record(
                "AGENDA_PULL_REFRESH_ALL_REQUESTED",
                activity.packageName,
                "scope=all_accounts public_bookings=true public_agenda=true source=timeline_pull",
            )
            shareScope.launch {
                val bookingSync = runCatching {
                    PublicBookingRemoteSync0296.pullAndReconcile(activity, store)
                }
                refresh()

                val nextSyncToken = autoBlaBlaSyncToken + 1
                forceAllBlaBlaSyncToken = nextSyncToken
                autoBlaBlaSyncToken = nextSyncToken
                publicAgendaSyncRevision++

                refreshAllRunning = false
                val imported = bookingSync.getOrNull()?.importedCount ?: 0
                message = if (bookingSync.isFailure) {
                    "Sincronização geral iniciada. BlaBlaCar e Agenda Pública continuam; a leitura das reservas públicas falhou e será tentada novamente no próximo ciclo."
                } else {
                    "Sincronização geral iniciada • todas as contas BlaBlaCar • Agenda Pública • $imported reserva(s) pública(s) recebida(s)."
                }
            }
        }
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
        PullToRefreshBox(
            isRefreshing = refreshAllRunning,
            onRefresh = requestFullTimelineRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
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
                    forceAllSyncToken = forceAllBlaBlaSyncToken,
                    onRequestBlaBlaSync = { autoBlaBlaSyncToken++ },
                    onCreateTrip = { screen = TripScreen.CREATE },
                    onPinShortcut = {
                        val requested = TripShortcutInstaller.requestPinnedCreateShortcut(activity)
                        message = if (requested) "Pedido de atalho enviado ao Android." else "O launcher não permite fixar atalhos automaticamente."
                    },
                    onOpenOnlineSettings = { screen = TripScreen.SETTINGS },
                    onOpenPassengers = { screen = TripScreen.PASSENGERS },
                    onManageLocal = { tripId ->
                        selectedId = tripId
                        screen = TripScreen.LIST
                    },
                    onBack = { activity.finish() },
                )
                TripScreen.PASSENGERS -> PassengerAdminScreen(
                    store = store,
                    onBack = { screen = TripScreen.TIMELINE },
                    onChanged = { text -> refresh(); message = text },
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
                                    val resolvedProfile = PublicDriverProfileResolver(activity).resolve(saved)
                                    val response = TripRemoteApi(saved).ensurePublicAgenda(saved.publicCalendarToken, resolvedProfile)
                                    val validated = saved.copy(
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
                    onRotateLink = { expected, replacement ->
                        store.replacePublicAgendaLinkAfterConfirmedRotation(expected, replacement)
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
                                        val resolvedProfile = PublicDriverProfileResolver(activity).resolve(onlineSettings)
                                        val response = TripRemoteApi(onlineSettings).ensurePublicAgenda(onlineSettings.publicCalendarToken, resolvedProfile)
                                        val validated = onlineSettings.copy(
                                            driverDisplayName = response.displayName.ifBlank { onlineSettings.driverDisplayName },
                                            driverUsername = response.username.ifBlank { onlineSettings.driverUsername },
                                        )
                                        store.saveOnlineSettings(validated)
                                        response to validated
                                    }.onSuccess { (response, validated) ->
                                        if (TripCalendarBridge.sharePublicAgenda(activity, validated)) {
                                            message = "Link da Agenda Pública validado e pronto para compartilhar."
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
