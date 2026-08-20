package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
// Step7 replaces the blocked Railway-origin read with authenticated on-device sessions.

@Composable
fun BlaBlaCollectorPanel(
    trips: List<Trip>,
    stateStore: BlaBlaCollectorStateStore,
    currentResponse: BlaBlaCollectorMonthResponse?,
    onResult: (BlaBlaCollectorMonthResponse) -> Unit,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val sessionStore = remember(context) { BlaBlaLocalSessionStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        revision++
    }

    fun publishCombined(messagePrefix: String) {
        val response = sessionStore.combinedResponse()
        stateStore.saveResponse(response)
        onResult(response)
        refresh()
        val verified = response.coverage.validated_queries
        val count = response.trips.size
        message = "$messagePrefix • $verified/2 perfis UUID-confirmados • $count viagens normalizadas."
        onChanged(message.orEmpty())
    }

    val barbosaSyncLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        syncing = false
        if (result.resultCode == Activity.RESULT_OK) {
            publishCombined("Sincronização concluída")
        } else {
            refresh()
            message = "Sincronização de Barbosa não foi concluída. A sessão permaneceu isolada para continuar o login/validação."
            onChanged(message.orEmpty())
        }
    }

    val ezequielSyncLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            barbosaSyncLauncher.launch(BlaBlaSessionIntents.sync(context, BlaBlaAccounts.BARBOSA))
        } else {
            syncing = false
            refresh()
            message = "Sincronização de Ezequiel não foi concluída. A sessão permaneceu isolada para continuar o login/validação."
            onChanged(message.orEmpty())
        }
    }

    val ezequielLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        val state = sessionStore.read(BlaBlaAccounts.EZEQUIEL)
        message = if (state?.identityVerified == true) "Ezequiel S: UUID confirmado ✅" else "Ezequiel S: sessão salva; UUID ainda precisa ser confirmado no perfil/detalhe."
        onChanged(message.orEmpty())
    }

    val barbosaLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        val state = sessionStore.read(BlaBlaAccounts.BARBOSA)
        message = if (state?.identityVerified == true) "Barbosa: UUID confirmado ✅" else "Barbosa: sessão salva; UUID ainda precisa ser confirmado no perfil/detalhe."
        onChanged(message.orEmpty())
    }

    @Suppress("UNUSED_VARIABLE") val refreshKey = revision
    val ezequiel = sessionStore.read(BlaBlaAccounts.EZEQUIEL)
    val barbosa = sessionStore.read(BlaBlaAccounts.BARBOSA)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contas BlaBlaCar")
            Text("Cada conta abre em um processo WebView próprio. Cookies/login não são compartilhados entre Ezequiel e Barbosa.")
            AccountRow(
                account = BlaBlaAccounts.EZEQUIEL,
                snapshot = ezequiel,
                onLogin = { ezequielLoginLauncher.launch(BlaBlaSessionIntents.login(context, BlaBlaAccounts.EZEQUIEL)) },
            )
            AccountRow(
                account = BlaBlaAccounts.BARBOSA,
                snapshot = barbosa,
                onLogin = { barbosaLoginLauncher.launch(BlaBlaSessionIntents.login(context, BlaBlaAccounts.BARBOSA)) },
            )
            Spacer(Modifier.height(2.dp))
            Button(
                enabled = !syncing,
                onClick = {
                    syncing = true
                    message = "Sincronizando primeiro Ezequiel e depois Barbosa…"
                    onChanged(message.orEmpty())
                    ezequielSyncLauncher.launch(BlaBlaSessionIntents.sync(context, BlaBlaAccounts.EZEQUIEL))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (syncing) "Sincronizando…" else "Sincronizar BlaBlaCar")
            }
            Text("A leitura usa apenas a interface oficial logada. Senha não é capturada nem enviada ao Railway.")
            message?.let { Text(it) }
            if (currentResponse != null) {
                Text("Último resultado: ${currentResponse.status} • ${currentResponse.trips.size} viagens • UUIDs validados ${currentResponse.coverage.validated_queries}/${currentResponse.coverage.requested_queries}")
                currentResponse.collected_at?.let { collected ->
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
}

@Composable
private fun AccountRow(
    account: BlaBlaAccountDefinition,
    snapshot: BlaBlaLocalSessionSnapshot?,
    onLogin: () -> Unit,
) {
    val connected = snapshot?.identityVerified == true
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(account.label)
            Text(account.uuid)
            Text(
                when {
                    connected -> "Conectado • UUID confirmado ✅"
                    snapshot != null -> "Sessão salva • UUID pendente ⏳"
                    else -> "Ainda não conectado"
                },
            )
            if (snapshot != null) Text("Última leitura local: ${snapshot.trips.size} viagens")
        }
        OutlinedButton(onClick = onLogin) { Text(if (snapshot == null) "Entrar" else "Abrir") }
    }
}
