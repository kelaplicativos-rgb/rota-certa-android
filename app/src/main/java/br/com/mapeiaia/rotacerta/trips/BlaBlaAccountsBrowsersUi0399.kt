package br.com.mapeiaia.rotacerta.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewFeature

/**
 * Configuration-only projection of the existing BlaBlaCar account/browser authority.
 *
 * This screen intentionally owns no synchronization state. Opening it or returning
 * from an isolated login/profile WebView never enqueues AgendaBackgroundSync0392.
 */
@Composable
internal fun BlaBlaAccountsAndBrowsersScreen0399() {
    val context = LocalContext.current
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val sessionStore = remember(context) { BlaBlaDynamicSessionStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var showAddAccount by remember { mutableStateOf(false) }
    var newAccountLabel by remember { mutableStateOf("") }

    val sessionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        revision++
    }
    val accounts = remember(revision) { registry.list() }
    val multiProfileAvailable = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    Text("Contas e navegadores", style = MaterialTheme.typography.titleLarge)
    Text(
        "Configure as contas externas e os perfis de navegador usados pela integração. " +
            "A sincronização continua sendo controlada exclusivamente em Sincronização automática.",
        style = MaterialTheme.typography.bodyMedium,
    )

    if (!multiProfileAvailable) {
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Os perfis isolados exigem Android System WebView/Chrome com suporte a múltiplos perfis. " +
                    "Atualize o componente do sistema antes de conectar novas contas.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Contas externas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Cada conta usa um perfil WebView isolado. O UUID externo confirmado é a identidade autoritativa; " +
                    "o apelido é apenas visual.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (accounts.isEmpty()) {
                Text("Nenhuma conta adicionada.")
            } else {
                accounts.forEach { account ->
                    DynamicAccountRow(
                        account = account,
                        snapshot = sessionStore.read(account),
                        onOpen = {
                            sessionLauncher.launch(BlaBlaDynamicSessionIntents.login(context, account))
                        },
                        onRemove = {
                            registry.remove(account.id)
                            val reconciled = sessionStore.combinedResponse(registry.list())
                            BlaBlaCollectorStateStore(context).saveResponse(
                                response = reconciled,
                                preserveOnPartial = false,
                            )
                            revision++
                        },
                        showBrowserDetails = true,
                    )
                }
            }

            Button(
                enabled = multiProfileAvailable,
                onClick = {
                    newAccountLabel = ""
                    showAddAccount = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Adicionar conta")
            }
        }
    }

    Text(
        "Abrir uma conta abre somente a sessão isolada para login/configuração. " +
            "Nenhum ciclo de sincronização é iniciado por esta tela.",
        style = MaterialTheme.typography.bodySmall,
    )

    if (showAddAccount) {
        AlertDialog(
            onDismissRequest = { showAddAccount = false },
            title = { Text("Adicionar conta BlaBlaCar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "O nome é apenas um apelido local. Depois do login, o Rota Certa mantém o perfil do navegador " +
                            "isolado e usa o UUID externo quando ele for confirmado.",
                    )
                    OutlinedTextField(
                        value = newAccountLabel,
                        onValueChange = { newAccountLabel = it },
                        label = { Text("Apelido opcional") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = multiProfileAvailable,
                    onClick = {
                        val account = registry.add(newAccountLabel)
                        showAddAccount = false
                        revision++
                        sessionLauncher.launch(BlaBlaDynamicSessionIntents.login(context, account))
                    },
                ) { Text("Adicionar e entrar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccount = false }) { Text("Cancelar") }
            },
        )
    }
}
