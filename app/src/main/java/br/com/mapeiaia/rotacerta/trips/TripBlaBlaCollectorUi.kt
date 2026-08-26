package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Compatibility markers consumed by the already-validated Step5 materializer:
// UUID perfil 1 • UUID perfil 2 (opcional) • Mês — AAAA-MM • Buscar • rotas dinâmicas da Agenda
// Step7 dynamic accounts: registry starts empty; user adds as many isolated WebView profiles as needed.

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
) {
    val context = LocalContext.current
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val sessionStore = remember(context) { BlaBlaDynamicSessionStore(context) }
    val manualSeatStore = remember(context) { BlaBlaManualSeatSyncRequestStore(context) }
    val manualSeatAttemptStore = remember(context) { BlaBlaManualSeatSyncAttemptStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    var manualSeatSyncing by remember { mutableStateOf(false) }
    var syncQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var syncCursor by remember { mutableIntStateOf(0) }
    var handledAutoSyncToken by remember { mutableIntStateOf(0) }
    var targetedSyncTripId by remember { mutableStateOf<String?>(null) }
    var syncDateScope by remember { mutableStateOf<LocalDate?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAddAccount by remember { mutableStateOf(false) }
    var newAccountLabel by remember { mutableStateOf("") }

    fun refresh() {
        revision++
    }

    fun publishCombined(messagePrefix: String) {
        val accounts = registry.list()
        val response = sessionStore.combinedResponse(accounts)
        val scopeDate = syncDateScope
        val scopedResponse = scopeDate?.let { date ->
            BlaBlaCollectorTimelineModule.scopeResponseToDate(response, date)
        } ?: response
        val published = stateStore.saveResponse(
            scopedResponse,
            preserveOnPartial = scopeDate == null,
        )
        onResult(published)
        refresh()
        val scopeLabel = scopeDate?.let { date ->
            " • somente ${date.format(DateTimeFormatter.ofPattern("dd/MM"))}"
        }.orEmpty()
        message = "$messagePrefix$scopeLabel • ${published.coverage.validated_queries}/${accounts.size} contas UUID-confirmadas • ${published.trips.size} viagens."
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "AGENDA_SYNC_SCOPE_PUBLISHED",
            context.packageName,
            "scope=${if (scopeDate == null) "all" else "today"} targetDate=${scopeDate ?: "none"} source=normalized_trip_date publishedTrips=${published.trips.size}",
        )
        syncDateScope = null
    }

    fun advanceSyncQueue() {
        archiving = false
        if (syncCursor + 1 < syncQueue.size) {
            syncCursor++
        } else {
            syncing = false
            publishCombined(
                if (syncDateScope != null) "Sincronização do card de hoje concluída" else "Sincronização direta concluída",
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
                } else if (account != null && syncDateScope != null) {
                    archiving = false
                    message = "${account.displayLabel}: card de hoje concluído ✅"
                    onChanged(message.orEmpty())
                    UnifiedDebugEventStore.record(
                        "AGENDA_TODAY_CARD_SYNC_FINISHED",
                        context.packageName,
                        "accountPresent=true targetDate=$syncDateScope mhtmlFullAccountSkipped=true noLaterCardsVisited=true",
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
                val account = registry.get(accountId)
                message = "Sincronização não concluída em ${account?.displayLabel ?: "uma conta"}. O login dessa conta foi preservado."
                onChanged(message.orEmpty())
            }
        } else if (accountId != null) {
            val account = registry.get(accountId)
            val snapshot = account?.let(sessionStore::read)
            message = when {
                account == null -> "Conta não encontrada."
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
        if (result.resultCode == Activity.RESULT_OK && !accountId.isNullOrBlank()) {
            // Read-after-write: after the exact options page verified the new value,
            // refresh only the authenticated account that owns that publication.
            syncDateScope = null
            syncQueue = listOf(accountId)
            syncCursor = 0
            syncing = true
            archiving = false
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
        message = if (pending.seatDelta < 0) {
            "Ajustando ${-pending.seatDelta} vaga(s) na publicação correta…"
        } else {
            "Devolvendo ${pending.seatDelta} vaga(s) à publicação correta…"
        }
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_RELIABLE_LAUNCH",
            context.packageName,
            "origin=$origin request=${pending.id} delta=${pending.seatDelta} profileUuidPresent=true tripIdPresent=true",
        )
        seatSyncLauncher.launch(BlaBlaReliableSeatSyncIntents.seatSync(context, target))
        return true
    }

    // Discard snapshots from the old hard-coded two-account candidate. The
    // dynamic registry is authoritative from this version onward and starts empty.
    LaunchedEffect(Unit) {
        if (currentResponse != null && currentResponse.strategy != DYNAMIC_STRATEGY) {
            val clean = sessionStore.combinedResponse(registry.list())
            val published = stateStore.saveResponse(clean, preserveOnPartial = false)
            onResult(published)
        }
    }

    LaunchedEffect(autoSyncToken, autoSyncProfileUuid, autoSyncTripId, syncing, archiving, manualSeatSyncing, accounts.size) {
        if (autoSyncToken <= handledAutoSyncToken || syncing || archiving || manualSeatSyncing) return@LaunchedEffect

        val pendingManualSeat = pendingSeatRequest()
        if (pendingManualSeat != null) {
            val target = accounts.singleOrNull { account ->
                account.profileUuid?.equals(pendingManualSeat.profileUuid, ignoreCase = true) == true
            }
            if (target == null) {
                message = "Vaga interna atualizada • sincronização externa pendente ⚠️ • perfil UUID não resolvido."
                onChanged(message.orEmpty())
                UnifiedDebugEventStore.record(
                    "EXTERNAL_SEAT_SYNC_PENDING",
                    context.packageName,
                    "reason=target_profile_unresolved request=${pendingManualSeat.id} manual=true retained=true",
                )
                return@LaunchedEffect
            }
            handledAutoSyncToken = autoSyncToken
            launchPendingSeatSync("automatic_after_booking_change")
            return@LaunchedEffect
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
        val requestedProfile = autoSyncProfileUuid?.trim()?.takeIf(String::isNotEmpty)
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
        if (!syncing || archiving) return@LaunchedEffect
        val id = syncQueue.getOrNull(syncCursor)
        val account = registry.get(id)
        if (account == null) {
            if (syncCursor + 1 < syncQueue.size) syncCursor++ else {
                syncing = false
                publishCombined("Sincronização concluída")
            }
        } else {
            val exactTripId = targetedSyncTripId
            val dateScope = syncDateScope
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
                    sessionLauncher.launch(BlaBlaDynamicSessionIntents.syncExact(context, account, exactTripId, href))
                }
            } else if (dateScope != null) {
                sessionLauncher.launch(BlaBlaDynamicSessionIntents.syncToday(context, account, dateScope))
            } else {
                sessionLauncher.launch(BlaBlaDynamicSessionIntents.sync(context, account))
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contas BlaBlaCar")
            Text("Nenhuma conta vem pré-cadastrada. Cada conta adicionada ganha um perfil WebView isolado próprio e todas alimentam a mesma Linha do tempo.")

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

            Spacer(Modifier.height(2.dp))
            Button(
                enabled = !syncing && !archiving && !manualSeatSyncing && accounts.isNotEmpty(),
                onClick = {
                    if (!launchPendingSeatSync("manual_sync_button")) {
                        targetedSyncTripId = null
                        syncDateScope = null
                        syncQueue = accounts.map { it.id }
                        syncCursor = 0
                        syncing = true
                        archiving = false
                        message = "Sincronizando ${accounts.size} conta(s), uma por vez…"
                        onChanged(message.orEmpty())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        manualSeatSyncing -> "Sincronizando lugares…"
                        archiving -> "Baixando MHTMLs…"
                        syncing -> "Sincronizando…"
                        manualSeatStore.peek() != null -> "Tentar vagas pendentes"
                        else -> "Sincronizar todas as contas"
                    },
                )
            }

            OutlinedButton(
                enabled = !syncing && !archiving && !manualSeatSyncing && accounts.isNotEmpty(),
                onClick = {
                    if (!launchPendingSeatSync("manual_sync_today_button")) {
                        val today = LocalDate.now()
                        targetedSyncTripId = null
                        syncDateScope = today
                        syncQueue = accounts.map { it.id }
                        syncCursor = 0
                        syncing = true
                        archiving = false
                        message = "Sincronizando somente o card de hoje (${today.format(DateTimeFormatter.ofPattern("dd/MM"))})…"
                        onChanged(message.orEmpty())
                        UnifiedDebugEventStore.record(
                            "AGENDA_TODAY_ONLY_SYNC_REQUESTED",
                            context.packageName,
                            "accounts=${accounts.size} targetDate=$today authority=normalized_trip_date outerRelativeLabelIgnored=true",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sincronizar só hoje")
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
private fun DynamicAccountRow(
    account: BlaBlaDynamicAccount,
    snapshot: BlaBlaDynamicSessionSnapshot?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val connected = snapshot?.identityVerified == true && !account.profileUuid.isNullOrBlank()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(account.displayLabel)
            Text(account.profileUuid ?: "UUID será descoberto após login/validação")
            Text(
                when {
                    connected -> "Conectado • UUID confirmado ✅"
                    snapshot != null -> "Sessão salva • UUID pendente ⏳"
                    else -> "Ainda não conectado"
                },
            )
            if (snapshot != null) Text("Última leitura local: ${snapshot.trips.size} viagens")
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onOpen) { Text(if (snapshot == null) "Entrar" else "Abrir") }
            TextButton(onClick = onRemove) { Text("Remover") }
        }
    }
}

private const val DYNAMIC_STRATEGY = "authenticated_on_device_batch_first_dynamic_multi_profile"
