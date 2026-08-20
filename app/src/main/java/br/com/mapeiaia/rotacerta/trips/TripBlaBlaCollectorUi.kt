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
) {
    val context = LocalContext.current
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val sessionStore = remember(context) { BlaBlaDynamicSessionStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var syncQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var syncCursor by remember { mutableIntStateOf(0) }
    var handledAutoSyncToken by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAddAccount by remember { mutableStateOf(false) }
    var newAccountLabel by remember { mutableStateOf("") }

    fun refresh() {
        revision++
    }

    fun publishCombined(messagePrefix: String) {
        val accounts = registry.list()
        val response = sessionStore.combinedResponse(accounts)
        stateStore.saveResponse(response)
        onResult(response)
        refresh()
        message = "$messagePrefix • ${response.coverage.validated_queries}/${accounts.size} contas UUID-confirmadas • ${response.trips.size} viagens."
        onChanged(message.orEmpty())
    }

    val sessionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val accountId = result.data?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID)
        refresh()
        if (syncing) {
            if (result.resultCode == Activity.RESULT_OK) {
                if (syncCursor + 1 < syncQueue.size) {
                    syncCursor++
                } else {
                    syncing = false
                    publishCombined("Sincronização concluída")
                }
            } else {
                syncing = false
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

    @Suppress("UNUSED_VARIABLE") val refreshKey = revision
    val accounts = registry.list()

    // Discard snapshots from the old hard-coded two-account candidate. The
    // dynamic registry is authoritative from this version onward and starts empty.
    LaunchedEffect(Unit) {
        if (currentResponse != null && currentResponse.strategy != DYNAMIC_STRATEGY) {
            val clean = sessionStore.combinedResponse(registry.list())
            stateStore.saveResponse(clean)
            onResult(clean)
        }
    }

    LaunchedEffect(autoSyncToken, autoSyncProfileUuid, syncing, accounts.size) {
        if (autoSyncToken <= handledAutoSyncToken || syncing) return@LaunchedEffect
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
        syncQueue = selectedAccounts.map { it.id }
        syncCursor = 0
        syncing = true
        message = "Vagas internas atualizadas • conferindo BlaBlaCar…"
        onChanged(message.orEmpty())
        UnifiedDebugEventStore.record(
            "AUTO_SYNC_REQUESTED",
            context.packageName,
            "reason=occupancy_change accounts=${selectedAccounts.size} targeted=${requestedProfile != null} token=$autoSyncToken",
        )
    }

    LaunchedEffect(syncing, syncCursor, syncQueue) {
        if (!syncing) return@LaunchedEffect
        val id = syncQueue.getOrNull(syncCursor)
        val account = registry.get(id)
        if (account == null) {
            if (syncCursor + 1 < syncQueue.size) syncCursor++ else {
                syncing = false
                publishCombined("Sincronização concluída")
            }
        } else {
            sessionLauncher.launch(BlaBlaDynamicSessionIntents.sync(context, account))
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
                enabled = !syncing && accounts.isNotEmpty(),
                onClick = {
                    syncQueue = accounts.map { it.id }
                    syncCursor = 0
                    syncing = true
                    message = "Sincronizando ${accounts.size} conta(s), uma por vez…"
                    onChanged(message.orEmpty())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (syncing) "Sincronizando…" else "Sincronizar todas as contas")
            }

            Text("A leitura usa somente a interface oficial logada. Senha não é capturada nem enviada ao Railway.")
            Text("Durante a sincronização o Rota Certa também guarda localmente um HTML sanitizado de diagnóstico de Suas viagens e dos detalhes, sem scripts nem valores de campos de login.")
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

private const val DYNAMIC_STRATEGY = "authenticated_on_device_webview_dynamic_multi_profile"
