package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelection
import br.com.mapeiaia.rotacerta.date.RotaCertaDateSelectionMode
import br.com.mapeiaia.rotacerta.date.rotaCertaDateSelectionSummary
import br.com.mapeiaia.rotacerta.date.rotaCertaInclusiveDates
import br.com.mapeiaia.rotacerta.ui.RotaCertaDatePickerDialog
import br.com.mapeiaia.rotacerta.ui.RotaCertaDateSelectionField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Compatibility markers consumed by the already-validated Step5 materializer:
// UUID perfil 1 • UUID perfil 2 (opcional) • Mês — AAAA-MM • Buscar • rotas dinâmicas da Agenda
// Step7 dynamic accounts: registry starts empty; user adds as many isolated WebView profiles as needed.


internal fun shouldLaunchAutomaticSeatQueueContinuation(
    continuationToken: Int,
    handledContinuationToken: Int,
    manualSeatSyncing: Boolean,
    accountCount: Int,
): Boolean =
    continuationToken > handledContinuationToken &&
        continuationToken > 0 &&
        !manualSeatSyncing &&
        accountCount > 0

internal data class BlaBlaSyncDateValidation(
    val dates: List<LocalDate> = emptyList(),
    val error: String? = null,
)

internal fun validateBlaBlaSyncDateSelection(
    mode: RotaCertaDateSelectionMode,
    singleDate: LocalDate?,
    startDate: LocalDate?,
    endDate: LocalDate?,
): BlaBlaSyncDateValidation = when (mode) {
    RotaCertaDateSelectionMode.SINGLE -> if (singleDate == null) {
        BlaBlaSyncDateValidation(error = "Selecione uma data para sincronizar.")
    } else {
        BlaBlaSyncDateValidation(dates = listOf(singleDate))
    }

    RotaCertaDateSelectionMode.RANGE -> when {
        startDate == null && endDate == null -> BlaBlaSyncDateValidation(error = "Selecione a data inicial e a data final.")
        startDate == null -> BlaBlaSyncDateValidation(error = "Selecione a data inicial.")
        endDate == null -> BlaBlaSyncDateValidation(error = "Selecione a data final.")
        startDate.isAfter(endDate) -> BlaBlaSyncDateValidation(error = "A data inicial não pode ser posterior à data final.")
        else -> BlaBlaSyncDateValidation(dates = rotaCertaInclusiveDates(startDate, endDate))
    }

    else -> BlaBlaSyncDateValidation(error = "Modo de data não suportado para esta sincronização.")
}

internal fun shouldExposeDateScopedCollectorStatusOutsideDialog0391(
    dateScoped: Boolean,
    failure: Boolean = false,
): Boolean = failure || !dateScoped

private enum class BlaBlaSyncDateField {
    SINGLE,
    START,
    END,
}

@Composable
fun BlaBlaCollectorPanel(
    trips: List<Trip>,
    stateStore: BlaBlaCollectorStateStore,
    currentResponse: BlaBlaCollectorMonthResponse?,
    onResult: (BlaBlaCollectorMonthResponse) -> Unit,
    onChanged: (String) -> Unit,
    autoSyncToken: Int = 0,
    autoSyncProfileUuid: String? = null,
    autoSyncTripId: String? = null,
    showAccountManagement: Boolean = true,
) {
    val context = LocalContext.current
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val sessionStore = remember(context) { BlaBlaDynamicSessionStore(context) }
    val manualSeatStore = remember(context) { BlaBlaManualSeatSyncRequestStore(context) }
    val manualSeatAttemptStore = remember(context) { BlaBlaManualSeatSyncAttemptStore(context) }
    val publicationSeatStateStore = remember(context) { BlaBlaPublicationSeatSyncStateStore(context) }
    val dateLocale = remember(context) {
        RotaCertaTenantRegistry(context).activeTenant().localeTag
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let(Locale::forLanguageTag)
            ?.takeIf { it.language.isNotBlank() }
            ?: Locale.getDefault()
    }
    var revision by remember { mutableIntStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    var manualSeatSyncing by remember { mutableStateOf(false) }
    var syncSessionInFlight by remember { mutableStateOf(false) }
    var syncQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var syncCursor by remember { mutableIntStateOf(0) }
    var handledAutoSyncToken by remember { mutableIntStateOf(0) }
    var seatQueueContinuationToken by remember { mutableIntStateOf(0) }
    var handledSeatQueueContinuationToken by remember { mutableIntStateOf(0) }
    var targetedSyncTripId by remember { mutableStateOf<String?>(null) }
    var syncDateScope by remember { mutableStateOf<List<LocalDate>?>(null) }
    var syncScriptSelection0449 by remember { mutableStateOf<BlaBlaDateScopeScriptSelection0449?>(null) }
    var dateScopeSelectedAccountIds0449 by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dateScopeSelectedScripts0449 by remember { mutableStateOf(BlaBlaDateScopeScriptCatalog0449.all) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAddAccount by remember { mutableStateOf(false) }
    var newAccountLabel by remember { mutableStateOf("") }
    var showDateScopeSelector by remember { mutableStateOf(false) }
    var syncDateMode by remember { mutableStateOf(RotaCertaDateSelectionMode.SINGLE) }
    var singleDateSelection by remember {
        mutableStateOf(RotaCertaDateSelection(mode = RotaCertaDateSelectionMode.SINGLE))
    }
    var periodStartSelection by remember {
        mutableStateOf(RotaCertaDateSelection(mode = RotaCertaDateSelectionMode.SINGLE))
    }
    var periodEndSelection by remember {
        mutableStateOf(RotaCertaDateSelection(mode = RotaCertaDateSelectionMode.SINGLE))
    }
    var datePickerTarget by remember { mutableStateOf<BlaBlaSyncDateField?>(null) }

    fun refresh() {
        revision++
    }

    fun publishCombined(messagePrefix: String) {
        val accounts = registry.list()
        val response = sessionStore.combinedResponse(accounts)
        val scopeDates = syncDateScope.orEmpty()
        val published = stateStore.saveResponse(
            response,
            preserveOnPartial = true,
        )
        onResult(published)
        refresh()
        val scopeLabel = scopeDates.takeIf { it.isNotEmpty() }?.let { dates ->
            val summary = rotaCertaDateSelectionSummary(
                RotaCertaDateSelection(
                    mode = if (dates.size == 1) RotaCertaDateSelectionMode.SINGLE else RotaCertaDateSelectionMode.RANGE,
                    dates = dates,
                ),
                locale = dateLocale,
            )
            " • $summary"
        }.orEmpty()
        message = "$messagePrefix$scopeLabel • ${published.coverage.validated_queries}/${accounts.size} contas UUID-confirmadas • ${published.trips.size} viagens."
        val exposeOutsideDialog = shouldExposeDateScopedCollectorStatusOutsideDialog0391(
            dateScoped = scopeDates.isNotEmpty(),
        )
        if (exposeOutsideDialog) {
            onChanged(message.orEmpty())
        } else {
            UnifiedDebugEventStore.record(
                "AGENDA_DATE_SCOPE_STATUS_LOCAL_ONLY_0391",
                context.packageName,
                "phase=published dateCount=${scopeDates.size} timelineBanner=false",
            )
        }
        UnifiedDebugEventStore.record(
            "AGENDA_SYNC_SCOPE_PUBLISHED",
            context.packageName,
            "scope=${if (scopeDates.isEmpty()) "all" else "selected"} dateCount=${scopeDates.size} targetStart=${scopeDates.firstOrNull() ?: "none"} targetEnd=${scopeDates.lastOrNull() ?: "none"} source=normalized_trip_date publishedTrips=${published.trips.size} outsideScopePreserved=true",
        )
        syncDateScope = null
        syncScriptSelection0449 = null
    }

    fun advanceSyncQueue() {
        archiving = false
        if (syncCursor + 1 < syncQueue.size) {
            syncCursor++
        } else {
            syncing = false
            publishCombined(
                if (!syncDateScope.isNullOrEmpty()) "Sincronização por data/período concluída" else "Sincronização direta concluída",
            )
            if (BlockedPassengerCancellationStore(context).list().isNotEmpty()) {
                context.startActivity(BlaBlaBlockedPassengerCancellationIntents.process(context))
            }
        }
    }

    val archiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val accountId = result.data?.getStringExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID)
        val count = result.data?.getIntExtra("archive_count", 0) ?: 0
        UnifiedDebugEventStore.record(
            "MHTML_HARVEST_RETURNED",
            context.packageName,
            "accountPresent=${accountId != null} result=${result.resultCode} archives=$count",
        )
        if (syncing) advanceSyncQueue() else archiving = false
    }

    val sessionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val accountId = result.data?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID)
        syncSessionInFlight = false
        refresh()
        if (syncing) {
            if (result.resultCode == Activity.RESULT_OK) {
                val account = registry.get(accountId)
                if (targetedSyncTripId != null) {
                    syncing = false
                    archiving = false
                    val exactTrip = targetedSyncTripId
                    targetedSyncTripId = null
                    stateStore.lastResponse()?.let(onResult)
                    refresh()
                    message = "${account?.displayLabel ?: "Conta"}: somente o card solicitado foi sincronizado ✅"
                    onChanged(message.orEmpty())
                    UnifiedDebugEventStore.record(
                        "AGENDA_EXACT_CARD_SYNC_FINISHED",
                        context.packageName,
                        "tripIdPresent=${!exactTrip.isNullOrBlank()} accountPresent=${account != null} mhtmlFullAccountSkipped=true",
                    )
                    if (BlockedPassengerCancellationStore(context).list().isNotEmpty()) {
                        context.startActivity(BlaBlaBlockedPassengerCancellationIntents.process(context))
                    }
                } else if (account != null && !syncDateScope.isNullOrEmpty()) {
                    archiving = false
                    message = "${account.displayLabel}: escopo de data/período concluído ✅"
                    if (shouldExposeDateScopedCollectorStatusOutsideDialog0391(dateScoped = true)) {
                        onChanged(message.orEmpty())
                    } else {
                        UnifiedDebugEventStore.record(
                            "AGENDA_DATE_SCOPE_STATUS_LOCAL_ONLY_0391",
                            context.packageName,
                            "phase=account_complete timelineBanner=false",
                        )
                    }
                    UnifiedDebugEventStore.record(
                        "AGENDA_DATE_SCOPE_ACCOUNT_SYNC_FINISHED",
                        context.packageName,
                        "accountPresent=true dateCount=${syncDateScope?.size ?: 0} targetStart=${syncDateScope?.firstOrNull() ?: "none"} targetEnd=${syncDateScope?.lastOrNull() ?: "none"} mhtmlFullAccountSkipped=true outsideScopeCardOpened=false",
                    )
                    advanceSyncQueue()
                } else if (account != null) {
                    archiving = false
                    message = "${account.displayLabel}: leitura direta concluída ✅"
                    onChanged(message.orEmpty())
                    UnifiedDebugEventStore.record(
                        "AGENDA_ALL_ACCOUNTS_DIRECT_SYNC_FINISHED",
                        context.packageName,
                        "accountPresent=true dateScope=all directCollector=true mhtmlFullAccountSkipped=true",
                    )
                    advanceSyncQueue()
                } else {
                    advanceSyncQueue()
                }
            } else {
                syncing = false
                archiving = false
                syncDateScope = null
                syncScriptSelection0449 = null
                val account = registry.get(accountId)
                val failure = result.data?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407).orEmpty()
                message = if (failure == "TEMPORARILY_RESTRICTED") {
                    "BlaBlaCar restringiu temporariamente esta sessão. Seus últimos dados válidos foram preservados. " +
                        "A sincronização será retomada após a sessão voltar a ficar disponível."
                } else {
                    "Sincronização não concluída em ${account?.displayLabel ?: "uma conta"}. O login dessa conta foi preservado."
                }
                onChanged(message.orEmpty())
            }
        } else if (accountId != null) {
            val account = registry.get(accountId)
            val snapshot = account?.let(sessionStore::read)
            message = when {
                account == null -> "Conta não encontrada."
                snapshot?.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED ->
                    "BlaBlaCar restringiu temporariamente esta sessão. Os últimos dados válidos continuam preservados."
                snapshot?.identityVerified == true -> "${account.displayLabel}: UUID confirmado ✅"
                else -> "${account.displayLabel}: sessão salva; o UUID será confirmado pelo perfil ou por uma viagem."
            }
            onChanged(message.orEmpty())
        }
    }

    val seatSyncLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        manualSeatSyncing = false
        val accountId = result.data?.getStringExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID)
        val seatMessage = result.data?.getStringExtra("seat_sync_message")
            ?: if (result.resultCode == Activity.RESULT_OK) "Sincronizado externamente ✅" else "Sincronização externa pendente ⚠️"
        message = seatMessage
        onChanged(seatMessage)
        // Seat-only writer already reloads the exact options page and verifies the
        // published number. Do not chain a full trip/account collector sync here.
        refresh()
        if (result.resultCode == Activity.RESULT_OK && manualSeatStore.list().isNotEmpty()) {
            seatQueueContinuationToken++
        } else if (
            result.resultCode != Activity.RESULT_OK &&
            result.data?.getBooleanExtra("seat_sync_request_retained", false) == true
        ) {
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_SYNC_AUTOMATIC_CHAIN_STOPPED",
                context.packageName,
                "reason=pending_or_error requestRetained=true automaticRetry=false manualRetryAvailable=true",
            )
        }
    }

    @Suppress("UNUSED_VARIABLE") val refreshKey = revision
    val accounts = registry.list()

    fun pendingSeatRequest(): BlaBlaManualSeatSyncRequest? =
        BlaBlaReliableSeatQueuePolicy.select(manualSeatStore.list()) { requestId ->
            manualSeatAttemptStore.get(requestId) != null
        }

    fun pendingSeatTarget(): Pair<BlaBlaManualSeatSyncRequest, BlaBlaDynamicAccount>? {
        val pending = pendingSeatRequest() ?: return null
        val target = accounts.singleOrNull { account ->
            account.profileUuid?.equals(pending.profileUuid, ignoreCase = true) == true
        } ?: return null
        return pending to target
    }

    fun launchPendingSeatSync(origin: String): Boolean {
        val (pending, target) = pendingSeatTarget() ?: return false
        manualSeatSyncing = true
        message = pending.desiredPublishedSeats?.let { desired ->
            "Sincronizando somente as vagas desta publicação • alvo atual: $desired…"
        } ?: if (pending.seatDelta < 0) {
            "Ajustando ${-pending.seatDelta} vaga(s) na publicação correta…"
        } else {
            "Devolvendo ${pending.seatDelta} vaga(s) à publicação correta…"
        }
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_RELIABLE_LAUNCH",
            context.packageName,
            "origin=$origin request=${pending.id} delta=${pending.seatDelta} desired=${pending.desiredPublishedSeats ?: -1} profileUuidPresent=true tripIdPresent=true",
        )
        seatSyncLauncher.launch(BlaBlaReliableSeatSyncIntents.seatSync(context, target, pending.id))
        return true
    }

    LaunchedEffect(seatQueueContinuationToken, manualSeatSyncing, accounts.size) {
        if (
            shouldLaunchAutomaticSeatQueueContinuation(
                continuationToken = seatQueueContinuationToken,
                handledContinuationToken = handledSeatQueueContinuationToken,
                manualSeatSyncing = manualSeatSyncing,
                accountCount = accounts.size,
            )
        ) {
            handledSeatQueueContinuationToken = seatQueueContinuationToken
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_SYNC_AUTOMATIC_CONTINUATION_CONSUMED",
                context.packageName,
                "token=$seatQueueContinuationToken automaticRetryBudget=1",
            )
            val launched = launchPendingSeatSync("automatic_queue_continuation")
            if (!launched) {
                UnifiedDebugEventStore.record(
                    "EXTERNAL_SEAT_SYNC_PENDING",
                    context.packageName,
                    "reason=queued_target_profile_unresolved retained=true normalSyncContinues=true automaticRetry=false",
                )
            }
        }
    }

    fun clearPendingSeatSyncs() {
        val clearedRequests = manualSeatStore.clearAll()
        manualSeatAttemptStore.clearAll()
        val clearedVisualStates = publicationSeatStateStore.clearPendingStates()
        manualSeatSyncing = false
        message = if (clearedRequests.isEmpty() && clearedVisualStates == 0) {
            "Não havia vagas pendentes para limpar."
        } else {
            "Pendências de vagas limpas ✅ • a sincronização normal continua liberada."
        }
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_PENDING_QUEUE_CLEARED",
            context.packageName,
            "requests=${clearedRequests.size} visualStates=$clearedVisualStates userInitiated=true bookingsPreserved=true profilesPreserved=true",
        )
        onChanged(message.orEmpty())
        refresh()
    }

    // Discard snapshots from the old hard-coded two-account candidate. The
    // dynamic registry is authoritative from this version onward and starts empty.
    LaunchedEffect(Unit) {
        val retired = manualSeatStore.discardLegacyDeltaRequests()
        retired.forEach(manualSeatAttemptStore::clear)
        if (retired.isNotEmpty()) {
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_LEGACY_DELTA_RETIRED",
                context.packageName,
                "count=${retired.size} reason=desired_state_migration",
            )
        }
        if (currentResponse != null && currentResponse.strategy != DYNAMIC_STRATEGY) {
            val clean = sessionStore.combinedResponse(registry.list())
            val published = stateStore.saveResponse(clean, preserveOnPartial = false)
            onResult(published)
        }
    }

    LaunchedEffect(autoSyncToken, autoSyncProfileUuid, autoSyncTripId, syncing, archiving, manualSeatSyncing, accounts.size) {
        if (autoSyncToken <= handledAutoSyncToken || syncing || archiving || manualSeatSyncing) return@LaunchedEffect

        val requestedProfile = autoSyncProfileUuid?.trim()?.takeIf(String::isNotEmpty)
        val requestedTrip = autoSyncTripId?.trim()?.takeIf(String::isNotEmpty)
        val pendingManualSeat = pendingSeatRequest()?.takeIf { pending ->
            (requestedProfile == null || pending.profileUuid.equals(requestedProfile, ignoreCase = true)) &&
                (requestedTrip == null || pending.tripId == requestedTrip)
        }
        if (pendingManualSeat != null) {
            val target = accounts.singleOrNull { account ->
                account.profileUuid?.equals(pendingManualSeat.profileUuid, ignoreCase = true) == true
            }
            if (target != null) {
                handledAutoSyncToken = autoSyncToken
                launchPendingSeatSync("automatic_after_booking_change")
                return@LaunchedEffect
            }
            message = "Vaga interna atualizada • pendência externa preservada sem bloquear a sincronização normal."
            onChanged(message.orEmpty())
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_SYNC_PENDING",
                context.packageName,
                "reason=target_profile_unresolved request=${pendingManualSeat.id} manual=true retained=true normalSyncContinues=true",
            )
        }

        if (accounts.isEmpty()) {
            message = "Passageiro salvo • conecte uma conta BlaBlaCar para sincronizar."
            onChanged(message.orEmpty())
            UnifiedDebugEventStore.record(
                "AUTO_SYNC_PENDING",
                context.packageName,
                "reason=occupancy_change accounts=0 token=$autoSyncToken",
            )
            return@LaunchedEffect
        }
        val selectedAccounts = if (requestedProfile == null) {
            accounts
        } else {
            accounts.filter { account -> account.profileUuid?.equals(requestedProfile, ignoreCase = true) == true }
        }
        if (requestedProfile != null && selectedAccounts.isEmpty()) {
            message = "Vagas internas atualizadas • perfil BlaBlaCar selecionado ainda não está vinculado."
            onChanged(message.orEmpty())
            UnifiedDebugEventStore.record(
                "AUTO_SYNC_PENDING",
                context.packageName,
                "reason=target_profile_unresolved requestedProfile=true token=$autoSyncToken",
            )
            return@LaunchedEffect
        }
        handledAutoSyncToken = autoSyncToken
        syncDateScope = null
        syncScriptSelection0449 = null
        targetedSyncTripId = autoSyncTripId?.trim()?.takeIf(String::isNotEmpty)
        syncQueue = selectedAccounts.map { it.id }
        syncCursor = 0
        syncing = true
        archiving = false
        message = "Vagas internas atualizadas • conferindo BlaBlaCar…"
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "AUTO_SYNC_REQUESTED",
            context.packageName,
            "reason=occupancy_change accounts=${selectedAccounts.size} targeted=${requestedProfile != null} token=$autoSyncToken",
        )
    }

    LaunchedEffect(syncing, archiving, syncCursor, syncQueue) {
        if (!syncing || archiving || syncSessionInFlight) return@LaunchedEffect
        val id = syncQueue.getOrNull(syncCursor)
        val account = registry.get(id)
        if (account == null) {
            if (syncCursor + 1 < syncQueue.size) syncCursor++ else {
                syncing = false
                publishCombined("Sincronização concluída")
            }
        } else {
            val exactTripId = targetedSyncTripId
            val dateScope = syncDateScope.orEmpty()
            if (exactTripId != null) {
                val currentTrip = stateStore.lastResponse()?.trips?.singleOrNull { trip ->
                    trip.profile_uuid.equals(account.profileUuid, ignoreCase = true) && trip.trip_id == exactTripId
                }
                val href = currentTrip?.trip_href
                if (href.isNullOrBlank()) {
                    syncing = false
                    syncDateScope = null
                    targetedSyncTripId = null
                    message = "Card exato sem link canônico; sincronização individual não iniciada."
                    onChanged(message.orEmpty())
                } else {
                    UnifiedDebugEventStore.record(
                        "AGENDA_SYNC_SESSION_LAUNCH",
                        context.packageName,
                        "cursor=${syncCursor + 1}/${syncQueue.size} account=${account.displayLabel} exact=${exactTripId != null} dateScoped=${dateScope.isNotEmpty()} sequentialGate=true",
                    )
                    syncSessionInFlight = true
                    sessionLauncher.launch(BlaBlaDynamicSessionIntents.syncExact(context, account, exactTripId, href))
                }
            } else if (dateScope.isNotEmpty()) {
                val summary = rotaCertaDateSelectionSummary(
                    RotaCertaDateSelection(
                        mode = if (dateScope.size == 1) RotaCertaDateSelectionMode.SINGLE else RotaCertaDateSelectionMode.RANGE,
                        dates = dateScope,
                    ),
                    locale = dateLocale,
                )
                message = "Sincronizando $summary • conta ${syncCursor + 1}/${syncQueue.size}: ${account.displayLabel}…"
                if (shouldExposeDateScopedCollectorStatusOutsideDialog0391(dateScoped = true)) {
                    onChanged(message.orEmpty())
                } else {
                    UnifiedDebugEventStore.record(
                        "AGENDA_DATE_SCOPE_STATUS_LOCAL_ONLY_0391",
                        context.packageName,
                        "phase=account_launch timelineBanner=false",
                    )
                }
                UnifiedDebugEventStore.record(
                    "AGENDA_SYNC_SESSION_LAUNCH",
                    context.packageName,
                    "cursor=${syncCursor + 1}/${syncQueue.size} account=${account.displayLabel} exact=false dateScoped=true dateCount=${dateScope.size} targetStart=${dateScope.first()} targetEnd=${dateScope.last()} sequentialGate=true",
                )
                syncSessionInFlight = true
                sessionLauncher.launch(
                    BlaBlaDynamicSessionIntents.syncDates(
                        context = context,
                        account = account,
                        targetDates = dateScope,
                        enabledScripts = syncScriptSelection0449?.requested,
                    ),
                )
            } else {
                UnifiedDebugEventStore.record(
                    "AGENDA_SYNC_SESSION_LAUNCH",
                    context.packageName,
                    "cursor=${syncCursor + 1}/${syncQueue.size} account=${account.displayLabel} exact=${exactTripId != null} dateScoped=false sequentialGate=true",
                )
                syncSessionInFlight = true
                sessionLauncher.launch(BlaBlaDynamicSessionIntents.sync(context, account))
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contas BlaBlaCar")
            Text("Cada conta adicionada mantém sessão isolada. Todas as contas conectadas alimentam automaticamente a mesma Timeline e a mesma Agenda Pública; não existe filtro de perfil para as viagens.")

            if (showAccountManagement) {
                if (accounts.isEmpty()) {
                    Text("Nenhuma conta adicionada.")
                } else {
                    accounts.forEach { account ->
                        DynamicAccountRow(
                            account = account,
                            snapshot = sessionStore.read(account),
                            onOpen = { sessionLauncher.launch(BlaBlaDynamicSessionIntents.login(context, account)) },
                            onRemove = {
                                registry.remove(account.id)
                                refresh()
                                publishCombined("Conta removida")
                            },
                        )
                    }
                }

                Button(
                    onClick = {
                        newAccountLabel = ""
                        showAddAccount = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("+ Adicionar conta")
                }
            } else if (accounts.isEmpty()) {
                Text("Nenhuma conta adicionada. Use Contas e navegadores acima para conectar uma conta antes de sincronizar.")
            } else {
                Text("${accounts.size} conta(s) configurada(s) acima • use os controles abaixo para executar a coleta agora.")
            }

            Spacer(Modifier.height(2.dp))
            Button(
                enabled = !syncing && !archiving && !manualSeatSyncing && accounts.isNotEmpty(),
                onClick = {
                    targetedSyncTripId = null
                    syncDateScope = null
                    syncQueue = accounts.map { it.id }
                    syncCursor = 0
                    syncing = true
                    archiving = false
                    message = "Sincronizando ${accounts.size} conta(s), uma por vez…"
                    onChanged(message.orEmpty())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        manualSeatSyncing -> "Sincronizando lugares…"
                        archiving -> "Baixando MHTMLs…"
                        syncing -> "Sincronizando…"
                        else -> "Sincronizar todas as contas"
                    },
                )
            }

            val pendingSeatCount = manualSeatStore.list().size
            if (pendingSeatCount > 0) {
                Text("Vagas pendentes: $pendingSeatCount • isso não bloqueia as outras sincronizações.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !syncing && !archiving && !manualSeatSyncing && accounts.isNotEmpty(),
                        onClick = { launchPendingSeatSync("manual_pending_button") },
                    ) { Text("Tentar vagas pendentes") }
                    TextButton(
                        enabled = !syncing && !archiving && !manualSeatSyncing,
                        onClick = { clearPendingSeatSyncs() },
                    ) { Text("Limpar pendências") }
                }
            }

            OutlinedButton(
                enabled = !syncing && !archiving && !manualSeatSyncing && accounts.isNotEmpty(),
                onClick = {
                    dateScopeSelectedAccountIds0449 = accounts.map { it.id }.toSet()
                    dateScopeSelectedScripts0449 = BlaBlaDateScopeScriptCatalog0449.all
                    showDateScopeSelector = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("📅 Sincronizar por data/período")
            }

            Text("A leitura usa somente a interface oficial logada. Senha não é capturada nem enviada ao Railway.")
            Text("Após cada leitura, o Rota Certa guarda em área privada do app os MHTMLs necessários: /rides, resumo de cada viagem, passageiros individuais e opções de lugares. Esses arquivos podem conter dados pessoais e não são gravados em Downloads público.")
            message?.let { Text(it) }

            val displayResponse = currentResponse?.takeIf { it.strategy == DYNAMIC_STRATEGY }
            if (displayResponse != null) {
                Text("Último resultado: ${displayResponse.status} • ${displayResponse.trips.size} viagens • contas validadas ${displayResponse.coverage.validated_queries}/${displayResponse.coverage.requested_queries}")
                displayResponse.collected_at?.let { collected ->
                    runCatching { Instant.parse(collected) }.getOrNull()?.let { instant ->
                        val formatted = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(instant)
                        Text("Coletado em $formatted")
                    }
                }
            }
            if (trips.isEmpty()) {
                Text("A Agenda local está vazia; viagens BlaBlaCar confirmadas ainda podem aparecer na Linha do tempo.")
            }
        }
    }

    if (showDateScopeSelector) {
        val singleDate = singleDateSelection.normalizedDates.singleOrNull()
        val periodStart = periodStartSelection.normalizedDates.singleOrNull()
        val periodEnd = periodEndSelection.normalizedDates.singleOrNull()
        val validation = validateBlaBlaSyncDateSelection(
            mode = syncDateMode,
            singleDate = singleDate,
            startDate = periodStart,
            endDate = periodEnd,
        )
        AlertDialog(
            onDismissRequest = {
                showDateScopeSelector = false
                datePickerTarget = null
            },
            title = { Text("Sincronizar por data/período") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Escolha exatamente o escopo temporal que o coletor existente deve processar.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (syncDateMode == RotaCertaDateSelectionMode.SINGLE) {
                            Button(onClick = { syncDateMode = RotaCertaDateSelectionMode.SINGLE }) { Text("Data única") }
                        } else {
                            OutlinedButton(onClick = { syncDateMode = RotaCertaDateSelectionMode.SINGLE }) { Text("Data única") }
                        }
                        if (syncDateMode == RotaCertaDateSelectionMode.RANGE) {
                            Button(onClick = { syncDateMode = RotaCertaDateSelectionMode.RANGE }) { Text("Período") }
                        } else {
                            OutlinedButton(onClick = { syncDateMode = RotaCertaDateSelectionMode.RANGE }) { Text("Período") }
                        }
                    }
                    if (syncDateMode == RotaCertaDateSelectionMode.SINGLE) {
                        RotaCertaDateSelectionField(
                            selection = singleDateSelection,
                            onClick = { datePickerTarget = BlaBlaSyncDateField.SINGLE },
                            label = "Data",
                            emptySummary = "Selecionar data",
                            locale = dateLocale,
                        )
                    } else {
                        RotaCertaDateSelectionField(
                            selection = periodStartSelection,
                            onClick = { datePickerTarget = BlaBlaSyncDateField.START },
                            label = "De",
                            emptySummary = "Selecionar data inicial",
                            locale = dateLocale,
                        )
                        RotaCertaDateSelectionField(
                            selection = periodEndSelection,
                            onClick = { datePickerTarget = BlaBlaSyncDateField.END },
                            label = "Até",
                            emptySummary = "Selecionar data final",
                            locale = dateLocale,
                        )
                    }

                    Text("Perfis", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text("Ligue os perfis que devem participar desta execução. Os demais ficam totalmente fora da fila.")
                    accounts.forEach { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.displayLabel)
                                Text(
                                    account.profileUuid ?: "UUID ainda não confirmado",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = account.id in dateScopeSelectedAccountIds0449,
                                onCheckedChange = { enabled ->
                                    dateScopeSelectedAccountIds0449 = if (enabled) {
                                        dateScopeSelectedAccountIds0449 + account.id
                                    } else {
                                        dateScopeSelectedAccountIds0449 - account.id
                                    }
                                },
                            )
                        }
                    }

                    Text("Scripts do orquestrador", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text(
                        "Ligado = sincroniza esse script. Desligado = pula esse resultado. " +
                            "Dependências técnicas mínimas podem ser usadas apenas para alcançar um script ligado; " +
                            "os dados dos scripts desligados não são gravados.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            dateScopeSelectedScripts0449 = BlaBlaDateScopeScriptCatalog0449.all
                        }) { Text("Todos") }
                        TextButton(onClick = {
                            dateScopeSelectedScripts0449 = emptySet()
                        }) { Text("Nenhum") }
                        TextButton(onClick = {
                            dateScopeSelectedScripts0449 = BlaBlaDateScopeScriptCatalog0449.seatRequests
                        }) { Text("Só vagas") }
                    }

                    val publicUrlEnabled0449 =
                        BlaBlaDateScopeScriptCatalog0449.publicUrlRequests.all(dateScopeSelectedScripts0449::contains)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("URL pública")
                            Text(
                                "Desligado impede captura, fallback e atualização do link público.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = publicUrlEnabled0449,
                            onCheckedChange = { enabled ->
                                dateScopeSelectedScripts0449 = if (enabled) {
                                    dateScopeSelectedScripts0449 + BlaBlaDateScopeScriptCatalog0449.publicUrlRequests
                                } else {
                                    dateScopeSelectedScripts0449 - BlaBlaDateScopeScriptCatalog0449.publicUrlRequests
                                }
                            },
                        )
                    }

                    Text(
                        "32 scripts registrados • 22 CAPTURE • 8 NAVIGATION • 2 REMOTE_WRITE",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    BlaBlaDateScopeScriptCatalog0449.groups.forEach { group ->
                        val groupAllEnabled0449 = group.requests.all(dateScopeSelectedScripts0449::contains)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            group.title,
                                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            group.description,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Switch(
                                        checked = groupAllEnabled0449,
                                        onCheckedChange = { enabled ->
                                            dateScopeSelectedScripts0449 = if (enabled) {
                                                dateScopeSelectedScripts0449 + group.requests
                                            } else {
                                                dateScopeSelectedScripts0449 - group.requests.toSet()
                                            }
                                        },
                                    )
                                }

                                group.requests.forEach { request ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(BlaBlaDateScopeScriptCatalog0449.label(request))
                                            Text(
                                                request.assetName + " • " +
                                                    BlaBlaDateScopeScriptCatalog0449.operationLabel(request),
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            )
                                            if (request.operation == BlaBlaBrowserOperation.REMOTE_WRITE) {
                                                Text(
                                                    "Escrita remota: o toggle autoriza o script, mas ele só executa quando " +
                                                        "já existir uma operação explícita de alteração de vagas.",
                                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = request in dateScopeSelectedScripts0449,
                                            onCheckedChange = { enabled ->
                                                dateScopeSelectedScripts0449 = if (enabled) {
                                                    dateScopeSelectedScripts0449 + request
                                                } else {
                                                    dateScopeSelectedScripts0449 - request
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    validation.error?.let { Text("⚠️ $it") }
                    if (dateScopeSelectedAccountIds0449.isEmpty()) {
                        Text("⚠️ Selecione pelo menos um perfil.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validation.error == null &&
                        validation.dates.isNotEmpty() &&
                        dateScopeSelectedAccountIds0449.isNotEmpty(),
                    onClick = {
                        val dates = validation.dates.distinct().sorted()
                        if (dates.isEmpty()) return@TextButton
                        val selectedAccounts0449 = accounts.filter { it.id in dateScopeSelectedAccountIds0449 }
                        if (selectedAccounts0449.isEmpty()) return@TextButton
                        val summary = rotaCertaDateSelectionSummary(
                            RotaCertaDateSelection(mode = syncDateMode, dates = dates),
                            locale = dateLocale,
                        )
                        showDateScopeSelector = false
                        datePickerTarget = null
                        targetedSyncTripId = null
                        if (dateScopeSelectedScripts0449.isEmpty()) {
                            syncDateScope = null
                            syncScriptSelection0449 = null
                            message = "Nenhum script ligado • nada foi sincronizado."
                            onChanged(message.orEmpty())
                            UnifiedDebugEventStore.record(
                                "AGENDA_DATE_SCOPE_SYNC_SKIPPED_0449",
                                context.packageName,
                                "reason=no_scripts_selected profiles=${selectedAccounts0449.size} dateCount=${dates.size}",
                            )
                            return@TextButton
                        }
                        syncDateScope = dates
                        syncScriptSelection0449 = BlaBlaDateScopeScriptSelection0449.explicit(dateScopeSelectedScripts0449)
                        syncQueue = selectedAccounts0449.map { it.id }
                        syncCursor = 0
                        syncing = true
                        archiving = false
                        message = "Sincronizando $summary • 0/${selectedAccounts0449.size} perfis processados…"
                        if (shouldExposeDateScopedCollectorStatusOutsideDialog0391(dateScoped = true)) {
                            onChanged(message.orEmpty())
                        } else {
                            UnifiedDebugEventStore.record(
                                "AGENDA_DATE_SCOPE_STATUS_LOCAL_ONLY_0391",
                                context.packageName,
                                "phase=requested timelineBanner=false",
                            )
                        }
                        UnifiedDebugEventStore.record(
                            "AGENDA_DATE_SCOPE_SYNC_REQUESTED",
                            context.packageName,
                            "accounts=${selectedAccounts0449.size} mode=${syncDateMode.name} dateCount=${dates.size} targetStart=${dates.first()} targetEnd=${dates.last()} inclusive=true scripts=${dateScopeSelectedScripts0449.size}/${BlaBlaDateScopeScriptCatalog0449.all.size} selectiveScripts=${dateScopeSelectedScripts0449 != BlaBlaDateScopeScriptCatalog0449.all} authority=normalized_trip_date outsideScopeMutationAllowed=false pendingSeatQueueDoesNotBlock=true",
                        )
                    },
                ) {
                    Text(if (syncDateMode == RotaCertaDateSelectionMode.SINGLE) "Sincronizar esta data" else "Sincronizar período")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDateScopeSelector = false
                    datePickerTarget = null
                }) { Text("Cancelar") }
            },
        )
    }

    datePickerTarget?.let { target ->
        val selection = when (target) {
            BlaBlaSyncDateField.SINGLE -> singleDateSelection
            BlaBlaSyncDateField.START -> periodStartSelection
            BlaBlaSyncDateField.END -> periodEndSelection
        }
        val pickerTitle = when (target) {
            BlaBlaSyncDateField.SINGLE -> "Selecionar data"
            BlaBlaSyncDateField.START -> "Selecionar data inicial"
            BlaBlaSyncDateField.END -> "Selecionar data final"
        }
        RotaCertaDatePickerDialog(
            selection = selection,
            onDismiss = { datePickerTarget = null },
            onConfirm = { picked ->
                val normalized = picked.copy(
                    mode = RotaCertaDateSelectionMode.SINGLE,
                    dates = picked.normalizedDates.take(1),
                )
                when (target) {
                    BlaBlaSyncDateField.SINGLE -> singleDateSelection = normalized
                    BlaBlaSyncDateField.START -> periodStartSelection = normalized
                    BlaBlaSyncDateField.END -> periodEndSelection = normalized
                }
                datePickerTarget = null
            },
            minDate = null,
            allowedModes = setOf(RotaCertaDateSelectionMode.SINGLE),
            allowEmptySelection = false,
            emptyConfirmLabel = "Selecione uma data",
            title = pickerTitle,
            description = "Escolha a data exata que deve compor o escopo da sincronização.",
            locale = dateLocale,
        )
    }

    if (showAddAccount) {
        AlertDialog(
            onDismissRequest = { showAddAccount = false },
            title = { Text("Adicionar conta BlaBlaCar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O nome é apenas um apelido local. Depois do login, o Rota Certa tenta descobrir o nome público e o UUID real da conta.")
                    OutlinedTextField(
                        value = newAccountLabel,
                        onValueChange = { newAccountLabel = it },
                        label = { Text("Apelido opcional") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val account = registry.add(newAccountLabel)
                    showAddAccount = false
                    refresh()
                    sessionLauncher.launch(BlaBlaDynamicSessionIntents.login(context, account))
                }) { Text("Adicionar e entrar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccount = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
internal fun DynamicAccountRow(
    account: BlaBlaDynamicAccount,
    snapshot: BlaBlaDynamicSessionSnapshot?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    showBrowserDetails: Boolean = false,
) {
    val connected = snapshot?.identityVerified == true && !account.profileUuid.isNullOrBlank()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(account.displayLabel)
            Text(account.profileUuid ?: "UUID será descoberto após login/validação")
            Text(
                when {
                    snapshot?.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED ->
                        "BlaBlaCar temporariamente restrito • últimos dados preservados ⚠️"
                    connected -> "Conectado • UUID confirmado ✅"
                    snapshot != null -> "Sessão salva • UUID pendente ⏳"
                    else -> "Ainda não conectado"
                },
            )
            if (snapshot != null) Text("Última leitura local: ${snapshot.trips.size} viagens")
            if (snapshot?.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED) {
                Text("Abra esta conta quando quiser revalidar a sessão. O Rota Certa não ficará tentando em segundo plano.")
            }
            if (showBrowserDetails) {
                Text("Perfil do navegador: ${account.webProfileName}")
                Text("Sessão isolada: ${if (snapshot == null) "ainda não iniciada" else "salva no aparelho"}")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onOpen) {
                Text(if (snapshot == null) "Entrar" else if (showBrowserDetails) "Abrir navegador" else "Abrir")
            }
            TextButton(onClick = onRemove) { Text("Remover") }
        }
    }
}

private const val DYNAMIC_STRATEGY = "authenticated_on_device_batch_first_dynamic_multi_profile"
