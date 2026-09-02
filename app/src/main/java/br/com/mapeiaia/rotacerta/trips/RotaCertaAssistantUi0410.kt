package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private data class AssistantPreparedExecution0410(
    val command: RotaCertaStructuredCommand0410,
    val plan: RotaCertaResolvedExecutionPlan0410,
)

private class RotaCertaAssistantIdempotencyStore0410(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "rota_certa_assistant_idempotency_0410",
        Context.MODE_PRIVATE,
    )

    fun alreadyCompleted(key: String): Boolean =
        prefs.getBoolean("done_" + key.take(64), false)

    fun markCompleted(key: String) {
        prefs.edit().putBoolean("done_" + key.take(64), true).apply()
    }
}

@Composable
internal fun RotaCertaAssistantPanel0410(
    trips: List<Trip>,
    bookings: List<Booking>,
    store: TripStore,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val idempotencyStore = remember(context) { RotaCertaAssistantIdempotencyStore0410(context) }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<AssistantPreparedExecution0410?>(null) }
    var activeIdempotencyKey by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val spoken = activityResult.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                input = spoken.take(1200)
                result = "Comando de voz reconhecido. Revise e toque em Enviar."
            } else {
                result = "Não consegui reconhecer o comando de voz. Você pode tentar novamente ou digitar."
            }
        } else {
            result = "Entrada de voz cancelada. Nenhuma ação foi executada."
        }
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK && activeIdempotencyKey.isNotBlank()) {
            idempotencyStore.markCompleted(activeIdempotencyKey)
        }
        AgendaBackgroundSync0392.enqueueImmediate(context, "assistant_mutation_verify_0410")
        val activityMessage = activityResult.data?.getStringExtra(AgendaBatchPublisherActivity.EXTRA_MESSAGE)
        result = activityMessage ?: if (activityResult.resultCode == Activity.RESULT_OK) {
            "A ação terminou. O Sincronizador Central foi acionado para reler e comprovar o estado final."
        } else {
            "A ação não foi confirmada como concluída. O estado real será relido antes de qualquer nova tentativa."
        }
        activeIdempotencyKey = ""
        busy = false
        onChanged(result)
    }

    fun execute(prepared: AssistantPreparedExecution0410) {
        val command = prepared.command
        val plan = prepared.plan

        if (RotaCertaCommandPlanner0410.isStale(plan, store.trips())) {
            result = "Não executei. O estado da viagem mudou depois do plano (STALE_PLAN)."
            pending = null
            return
        }
        if (plan.risk != RotaCertaRisk0410.READ_ONLY &&
            idempotencyStore.alreadyCompleted(plan.idempotencyKey)
        ) {
            result = "Essa mesma operação já foi concluída; não executei novamente."
            pending = null
            return
        }

        when (plan.action) {
            RotaCertaAction0410.CREATE_TRIPS -> {
                val accounts = BlaBlaDynamicAccountRegistry(context).list()
                val publisherStore = AgendaBatchPublisherStore(context)
                val currentDraft = publisherStore.draft(accounts)
                val selected = currentDraft.profiles.filter { it.selected }

                if (selected.size != 1) {
                    result = if (selected.isEmpty()) {
                        "Não executei. Selecione exatamente um perfil em Publicar agenda antes de criar viagens pelo Assistente."
                    } else {
                        "Não executei. Há mais de um perfil selecionado; escolha um perfil para evitar publicar na conta errada."
                    }
                    pending = null
                    return
                }

                val months = plan.dates.map { it.year to it.monthValue }.distinct()
                if (months.size != 1) {
                    result = "Não executei. Este lote cruza meses; divida o pedido por mês para preservar o planner atual."
                    pending = null
                    return
                }

                val days = plan.dates.joinToString(",") { it.dayOfMonth.toString() }
                val month = plan.dates.first()
                val time = plan.time?.format(DateTimeFormatter.ofPattern("HH:mm"))

                var outbound = currentDraft.outbound
                var inbound = currentDraft.inbound
                if (command.origin.isNotBlank()) {
                    outbound = outbound.copy(originAddress = command.origin)
                }
                if (command.destination.isNotBlank()) {
                    outbound = outbound.copy(destinationAddress = command.destination)
                }
                if (command.roundTrip &&
                    command.origin.isNotBlank() &&
                    command.destination.isNotBlank()
                ) {
                    inbound = inbound.copy(
                        originAddress = command.destination,
                        destinationAddress = command.origin,
                    )
                }
                if (!time.isNullOrBlank()) {
                    outbound = outbound.copy(departureTime = time)
                }

                val selectedAccountId = selected.single().accountId
                val nextProfiles = currentDraft.profiles.map { profile ->
                    if (profile.accountId != selectedAccountId) {
                        profile.copy(selected = false)
                    } else {
                        profile.copy(
                            outboundDays = days,
                            inboundDays = if (command.roundTrip) days else "",
                        )
                    }
                }

                val nextDraft = currentDraft.copy(
                    monthYear = "%02d/%04d".format(
                        Locale.ROOT,
                        month.monthValue,
                        month.year,
                    ),
                    outbound = outbound,
                    inbound = inbound,
                    profiles = nextProfiles,
                )
                publisherStore.saveDraft(nextDraft)

                val publishPlan = AgendaBatchPublisherPlanner.plan(
                    nextDraft,
                    accounts,
                    BlaBlaCollectorStateStore(context).lastResponse()?.trips.orEmpty(),
                )

                if (publishPlan.errors.isNotEmpty()) {
                    result = "Não executei. " + publishPlan.errors.joinToString(" ")
                    pending = null
                    return
                }
                if (publishPlan.batches.isEmpty()) {
                    result = "Nenhuma viagem nova foi publicada: todas as datas compatíveis já existem."
                    idempotencyStore.markCompleted(plan.idempotencyKey)
                    pending = null
                    return
                }

                publisherStore.replaceQueue(publishPlan.batches)
                activeIdempotencyKey = plan.idempotencyKey
                pending = null
                busy = true

                UnifiedDebugEventStore.record(
                    "ASSISTANT_COMMAND_EXECUTE_0410",
                    context.packageName,
                    "action=CREATE_TRIPS planId=" + plan.planId +
                        " batches=" + publishPlan.batches.size +
                        " rides=" + publishPlan.batches.sumOf { it.dates.size } +
                        " alreadyPublished=" + publishPlan.alreadyPublished,
                )
                activityLauncher.launch(
                    Intent(context, AgendaBatchPublisherActivity::class.java),
                )
            }

            RotaCertaAction0410.SET_TRIP_SEATS -> {
                val trip = plan.trip
                val desired = command.seats
                if (trip == null || desired == null) {
                    result = "Não executei. Viagem ou quantidade de vagas não resolvida."
                    pending = null
                    return
                }

                val profileUuid = trip.blablaProfileUuid?.trim().orEmpty()
                val tripId = trip.blablaTripId?.trim().orEmpty()
                val state = BlaBlaPublicationSeatSyncStateStore(context).get(
                    profileUuid,
                    tripId,
                )
                if (state?.state != BlaBlaPublicationSeatSyncVisualState.SYNCED ||
                    state.lastObservedPublishedSeats == null
                ) {
                    result = "Não executei. Primeiro preciso de leitura verificada das vagas desse tripId."
                    pending = null
                    return
                }

                val accountMatches = BlaBlaDynamicAccountRegistry(context).list().filter { account ->
                    account.profileUuid?.trim()?.equals(
                        profileUuid,
                        ignoreCase = true,
                    ) == true
                }
                if (accountMatches.size != 1) {
                    result = "Não executei. A conta BlaBlaCar não pôde ser resolvida de forma inequívoca."
                    pending = null
                    return
                }

                val request = BlaBlaManualSeatSyncRequest(
                    id = UUID.randomUUID().toString(),
                    profileUuid = profileUuid,
                    tripId = tripId,
                    seatDelta = 0,
                    desiredPublishedSeats = desired,
                    desiredStateReason = "assistant_command_0410",
                    localTripId = trip.id,
                    localBookingId = "assistant:" + plan.planId,
                    source = "ASSISTANT_COMMAND_0410",
                )
                BlaBlaManualSeatSyncRequestStore(context).replacePublication(request)
                BlaBlaPublicationSeatSyncStateStore(context).markDesired(
                    profileUuid,
                    tripId,
                    desired,
                    "Assistente: aguardando verificação da alteração de vagas.",
                )

                activeIdempotencyKey = plan.idempotencyKey
                pending = null
                busy = true
                activityLauncher.launch(
                    BlaBlaReliableSeatSyncIntents.seatSync(
                        context,
                        accountMatches.single(),
                        request.id,
                    ),
                )
            }

            RotaCertaAction0410.REVERIFY_TRIP -> {
                val trip = plan.trip
                val target = if (trip == null) null else assistantStrongTarget0410(context, trip)
                if (target == null) {
                    result = "Não executei. A identidade forte accountId + profileUuid + tripId não está completa."
                } else {
                    val typed = BlaBlaCommand0407.forTarget(
                        target = target,
                        operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
                        origin = BlaBlaCommandOrigin0407.PLANNER,
                        expectedRevision = plan.observedRevision.toString(),
                    )
                    val queued = AgendaBackgroundSync0392.enqueueTripReverify0407(
                        context = context,
                        target = target,
                        commandId = typed.commandId,
                        requestedAtMillis = typed.requestedAtMillis,
                    )
                    result = if (queued) {
                        "Verificação enfileirada por tripId. O resultado só será aceito após releitura do estado."
                    } else {
                        "Não executei. A verificação foi bloqueada pela proteção de identidade/single-flight."
                    }
                }
                pending = null
            }

            RotaCertaAction0410.OPEN_TRIP -> {
                val trip = plan.trip
                val href = trip?.blablaManageUrl?.takeIf(String::isNotBlank)
                    ?: trip?.blablaPublicUrl?.takeIf(String::isNotBlank)
                    ?: trip?.externalSnapshot?.trip_href?.takeIf(String::isNotBlank)
                    ?: trip?.externalSnapshot?.public_trip_href?.takeIf(String::isNotBlank)

                if (href == null) {
                    result = "A viagem foi encontrada, mas não há link canônico comprovado para abrir."
                } else {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(href)),
                        )
                    }.onSuccess {
                        result = "Abrindo a viagem identificada pelo tripId confirmado."
                    }.onFailure {
                        result = "Não foi possível abrir o link comprovado desta viagem."
                    }
                }
                pending = null
            }

            RotaCertaAction0410.SHARE_TRIP -> {
                val trip = plan.trip
                val href = trip?.blablaPublicUrl?.takeIf(String::isNotBlank)
                    ?: trip?.publicUrl?.takeIf(String::isNotBlank)

                if (href == null) {
                    result = "Não compartilhei: não há link público canônico confirmado."
                } else {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, href)
                    }
                    context.startActivity(
                        Intent.createChooser(send, "Compartilhar viagem"),
                    )
                    result = "Link público canônico preparado para compartilhamento."
                }
                pending = null
            }

            else -> {
                result = assistantReadResult0410(plan, trips, bookings)
                pending = null
            }
        }

        if (result.isNotBlank()) {
            onChanged(result)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Assistente Rota Certa",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Fale naturalmente. O texto vira comando tipado; nenhuma ação pula validação, identidade forte ou política de execução.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1200) },
                label = { Text("Fale ou escreva o que deseja fazer") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                minLines = 2,
                maxLines = 5,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE,
                                Locale.getDefault().toLanguageTag(),
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                "Fale o que deseja fazer no Rota Certa",
                            )
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        runCatching {
                            voiceLauncher.launch(speechIntent)
                        }.onFailure {
                            result = "Reconhecimento de voz não está disponível neste aparelho. Digite o comando."
                        }
                    },
                ) {
                    Text("🎤 Falar")
                }
                Button(
                    enabled = input.isNotBlank() && !busy,
                    onClick = {
                        val text = input.trim()
                        busy = true
                        result = ""
                        pending = null

                        scope.launch {
                            runCatching {
                                val accounts = BlaBlaDynamicAccountRegistry(context).list()
                                val seatStateStore = BlaBlaPublicationSeatSyncStateStore(context)
                                val verifiedSeatTarget = trips.any { trip ->
                                    val state = seatStateStore.get(
                                        trip.blablaProfileUuid?.trim().orEmpty(),
                                        trip.blablaTripId?.trim().orEmpty(),
                                    )
                                    state?.state == BlaBlaPublicationSeatSyncVisualState.SYNCED &&
                                        state.lastObservedPublishedSeats != null
                                }

                                val allowed = RotaCertaCommandRegistry0410.interpreterActions(
                                    hasPublisherAccount = accounts.any {
                                        !it.profileUuid.isNullOrBlank()
                                    },
                                    hasVerifiedSeatTarget = verifiedSeatTarget,
                                ).map { it.name }.sorted()

                                TripRemoteApi(store.onlineSettings()).interpretAssistant0410(
                                    RotaCertaAssistantInterpretRequest0410(
                                        text = text,
                                        timezone = ZoneId.systemDefault().id,
                                        locale = Locale.getDefault().toLanguageTag(),
                                        allowedActions = allowed,
                                    ),
                                )
                            }.onSuccess { interpreted ->
                                val command = interpreted.command
                                val planned = RotaCertaCommandPlanner0410.plan(
                                    command = command,
                                    trips = store.trips(),
                                    bookings = bookings,
                                    now = Instant.now(),
                                    zoneId = ZoneId.systemDefault(),
                                )

                                if (planned.code != RotaCertaValidationCode0410.OK ||
                                    planned.plan == null
                                ) {
                                    result = "Não executei. " +
                                        planned.message.ifBlank {
                                            planned.code.name
                                        }
                                    busy = false
                                } else {
                                    val prepared = AssistantPreparedExecution0410(
                                        command,
                                        planned.plan,
                                    )
                                    if (planned.plan.policy ==
                                        RotaCertaExecutionPolicy0410.CONFIRM_BEFORE_EXECUTION
                                    ) {
                                        pending = prepared
                                        result = assistantPlanSummary0410(prepared)
                                        busy = false
                                    } else {
                                        busy = false
                                        execute(prepared)
                                    }
                                }
                            }.onFailure { error ->
                                result = when (error) {
                                    is TripRemoteApiException -> when (error.backendErrorCode) {
                                        "openai_not_configured" ->
                                            "Assistente ainda não está habilitado no backend. Nenhuma ação foi executada."
                                        "assistant_action_not_allowed" ->
                                            "A solicitação não corresponde a uma ação permitida no Command Registry."
                                        else ->
                                            "Não executei. " +
                                                (error.message ?: "Falha ao interpretar o comando.")
                                    }
                                    else ->
                                        "Não executei. " +
                                            (error.message ?: "Falha ao interpretar o comando.")
                                }
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (busy) "Interpretando..." else "Enviar")
                }
            }

            pending?.let { prepared ->
                Text(
                    result,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { execute(prepared) }) {
                        Text("Confirmar")
                    }
                    TextButton(
                        onClick = {
                            pending = null
                            result = "Operação cancelada. Nenhuma alteração foi feita."
                        },
                    ) {
                        Text("Cancelar")
                    }
                }
            } ?: run {
                if (result.isNotBlank()) {
                    Text(
                        result,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun assistantStrongTarget0410(
    context: Context,
    trip: Trip,
): BlaBlaTripTarget0407? {
    val tenantId = RotaCertaTenantRegistry(
        context.applicationContext,
    ).activeScope().tenantId.trim()
    val profileUuid = trip.blablaProfileUuid?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val tripId = trip.blablaTripId?.trim().orEmpty()
    val href = trip.blablaManageUrl?.trim()?.takeIf(String::isNotEmpty)
        ?: trip.externalSnapshot?.trip_href?.trim()?.takeIf(String::isNotEmpty)
        ?: return null

    if (tenantId.isBlank() ||
        profileUuid.isBlank() ||
        tripId.isBlank() ||
        BlaBlaCollectorUrlModule.tripId(href) != tripId
    ) {
        return null
    }

    val accounts = BlaBlaDynamicAccountRegistry(context.applicationContext).list().filter {
        it.profileUuid?.trim()?.equals(
            profileUuid,
            ignoreCase = true,
        ) == true
    }
    if (accounts.size != 1) return null

    return BlaBlaTripTarget0407(
        tenantId = tenantId,
        accountId = accounts.single().id,
        profileUuid = profileUuid,
        tripId = tripId,
        tripHref = href,
    )
}

private fun assistantPlanSummary0410(
    prepared: AssistantPreparedExecution0410,
): String = when (prepared.plan.action) {
    RotaCertaAction0410.CREATE_TRIPS ->
        "Plano validado: " +
            prepared.plan.dates.size +
            " data(s)" +
            if (prepared.command.roundTrip) {
                " com ida e volta. Confirme para publicar pelo AgendaBatchPublisher."
            } else {
                ". Confirme para publicar pelo AgendaBatchPublisher."
            }

    RotaCertaAction0410.SET_TRIP_SEATS ->
        "Plano validado para o tripId confirmado: alterar para " +
            (prepared.command.seats ?: 0) +
            " vaga(s). A alteração só será aceita após readback."

    else ->
        "Plano validado. Confirme para executar " +
            prepared.plan.action.name +
            "."
}

private fun assistantReadResult0410(
    plan: RotaCertaResolvedExecutionPlan0410,
    trips: List<Trip>,
    bookings: List<Booking>,
): String = when (plan.action) {
    RotaCertaAction0410.LIST_TRIPS -> {
        val active = trips.filter {
            !it.deleted && it.status != TripStatus.CANCELLED
        }
        if (active.isEmpty()) {
            "Nenhuma viagem ativa no estado canônico."
        } else {
            active.sortedBy(Trip::departureAtMillis)
                .take(20)
                .joinToString(
                    prefix = active.size.toString() + " viagem(ns): ",
                    separator = " • ",
                ) { trip ->
                    trip.title +
                        trip.blablaTripId?.let { id ->
                            " [" + id + "]"
                        }.orEmpty()
                }
        }
    }

    RotaCertaAction0410.READ_TRIP -> {
        val trip = plan.trip
        if (trip == null) {
            "Viagem não encontrada."
        } else {
            val date = Instant.ofEpochMilli(
                trip.departureAtMillis,
            ).atZone(ZoneId.systemDefault())
            val stops = trip.stops.sortedBy(TripStop::order)
            stops.firstOrNull()?.name.orEmpty() +
                " → " +
                stops.lastOrNull()?.name.orEmpty() +
                " • " +
                date.format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                ) +
                " • status " +
                trip.status.name +
                " • tripId " +
                (trip.blablaTripId ?: "pendente")
        }
    }

    RotaCertaAction0410.CHECK_SYNC -> {
        val active = trips.filter {
            !it.deleted && it.status != TripStatus.CANCELLED
        }
        val unresolved = active.count {
            it.blablaTripId.isNullOrBlank() ||
                it.blablaProfileUuid.isNullOrBlank()
        }
        val partial = active.count {
            resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING &&
                !it.externalSnapshotComplete
        }
        "Estado canônico: " +
            active.size +
            " ativa(s), " +
            unresolved +
            " sem identidade externa completa e " +
            partial +
            " snapshot(s) externo(s) parcial(is)."
    }

    RotaCertaAction0410.LIST_UNRESOLVED_TRIPS -> {
        val items = trips.filter {
            !it.deleted &&
                it.status != TripStatus.CANCELLED &&
                it.blablaTripId.isNullOrBlank()
        }
        if (items.isEmpty()) {
            "Nenhuma viagem ativa sem tripId confirmado."
        } else {
            items.joinToString(
                prefix = items.size.toString() + " sem tripId: ",
                separator = " • ",
            ) { it.title }
        }
    }

    RotaCertaAction0410.LIST_FULL_TRIPS -> {
        val full = trips.filter {
            !it.deleted && it.status == TripStatus.FULL
        }
        if (full.isEmpty()) {
            "Nenhuma viagem está marcada como lotada no estado canônico."
        } else {
            full.joinToString(
                prefix = full.size.toString() + " lotada(s): ",
                separator = " • ",
            ) { it.title }
        }
    }

    RotaCertaAction0410.GET_TRIP_PRICE -> {
        val trip = plan.trip
        if (trip == null) {
            "Viagem não encontrada."
        } else {
            "Preço observado para " +
                trip.title +
                ": " +
                (trip.externalSnapshot?.price ?: "não confirmado no snapshot atual") +
                "."
        }
    }

    RotaCertaAction0410.READ_BOOKINGS -> {
        val trip = plan.trip
        if (trip == null) {
            "Viagem não encontrada."
        } else {
            val items = bookings.filter { booking ->
                booking.tripId == trip.id &&
                    booking.status !in setOf(
                        BookingStatus.CANCELLED,
                        BookingStatus.REJECTED,
                        BookingStatus.EXPIRED,
                    )
            }
            if (items.isEmpty()) {
                "Nenhuma reserva ativa nessa viagem."
            } else {
                items.joinToString(
                    prefix = items.size.toString() + " reserva(s): ",
                    separator = " • ",
                ) {
                    it.passengerName + " (" + it.status.name + ")"
                }
            }
        }
    }

    RotaCertaAction0410.READ_PASSENGERS -> {
        val trip = plan.trip
        if (trip == null) {
            "Viagem não encontrada."
        } else {
            val items = bookings.filter { booking ->
                booking.tripId == trip.id &&
                    booking.status in setOf(
                        BookingStatus.REQUESTED,
                        BookingStatus.HELD,
                        BookingStatus.CONFIRMED,
                    )
            }
            if (items.isEmpty()) {
                "Nenhum passageiro ativo nessa viagem."
            } else {
                items.joinToString(
                    prefix = items.size.toString() + " passageiro(s): ",
                    separator = " • ",
                ) {
                    it.passengerName + " • " + it.operationalStatus.name
                }
            }
        }
    }

    RotaCertaAction0410.READ_PASSENGER ->
        plan.booking?.let {
            it.passengerName +
                " • reserva " +
                it.status.name +
                " • situação " +
                it.operationalStatus.name
        } ?: "Passageiro não encontrado."

    RotaCertaAction0410.READ_PROFILE ->
        "Perfil é lido da conta BlaBlaCar autenticada pelo coletor; a identidade real não é resolvida pela OpenAI."

    RotaCertaAction0410.READ_VEHICLE ->
        "O veículo é lido do snapshot autenticado do perfil; detalhes desnecessários não são enviados à OpenAI."

    else ->
        "A ação foi interpretada, mas não possui resultado read-only nesta superfície."
}
