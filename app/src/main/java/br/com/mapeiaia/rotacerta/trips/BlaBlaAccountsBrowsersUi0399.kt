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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch

/**
 * Configuration-only projection of the existing BlaBlaCar account/browser authority.
 *
 * This screen intentionally owns no synchronization state. Opening it or returning
 * from an isolated login/profile WebView never enqueues background synchronization work.
 * The forensic rides snapshot command is read-only and writes only app-private evidence.
 */
@Composable
internal fun BlaBlaAccountsAndBrowsersScreen0399() {
    val context = LocalContext.current
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val sessionStore = remember(context) { BlaBlaDynamicSessionStore(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var showAddAccount by remember { mutableStateOf(false) }
    var newAccountLabel by remember { mutableStateOf("") }
    var ridesSnapshotRunning0526 by remember { mutableStateOf(false) }
    var ridesSnapshotDownloadRunning0527 by remember { mutableStateOf(false) }
    var ridesSnapshotJsonDownloadRunning0531 by remember { mutableStateOf(false) }
    var ridesSnapshotProgress0526 by remember { mutableStateOf("") }
    var ridesSnapshotSummary0526 by remember { mutableStateOf("") }
    var lastRidesSnapshot0526 by remember { mutableStateOf<BlaBlaRidesSnapshotManifest0526?>(null) }

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

            if (accounts.isNotEmpty()) {
                Button(
                    enabled = multiProfileAvailable &&
                        !ridesSnapshotRunning0526 &&
                        !ridesSnapshotDownloadRunning0527 &&
                        !ridesSnapshotJsonDownloadRunning0531,
                    onClick = {
                        ridesSnapshotRunning0526 = true
                        ridesSnapshotSummary0526 = ""
                        lastRidesSnapshot0526 = null
                        ridesSnapshotProgress0526 = "Preparando captura privada…"
                        scope.launch {
                            val manifest = BlaBlaRidesSnapshotCoordinator0526.captureAll(context) { progress ->
                                ridesSnapshotProgress0526 = progress
                            }
                            lastRidesSnapshot0526 = manifest
                            ridesSnapshotSummary0526 = buildString {
                                append("Captura ").append(manifest.captureId)
                                append(" • ").append(manifest.result)
                                manifest.profiles.forEach { profile ->
                                    append("\n")
                                    append(profile.displayName.ifBlank { profile.expectedProfileUuid })
                                    append(": ").append(profile.status)
                                    append(" • ").append(profile.cardCountFinal).append(" viagens")
                                    if (profile.errorCode.isNotBlank()) {
                                        append(" • ").append(profile.errorCode)
                                    }
                                }
                            }
                            ridesSnapshotProgress0526 = "Captura finalizada • ${manifest.result}"
                            ridesSnapshotRunning0526 = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (ridesSnapshotRunning0526) {
                            "📥 Capturando Suas viagens…"
                        } else {
                            "📥 Capturar Suas viagens de todos os perfis"
                        },
                    )
                }
            }

            if (ridesSnapshotProgress0526.isNotBlank()) {
                Text(ridesSnapshotProgress0526, style = MaterialTheme.typography.bodySmall)
            }
            if (ridesSnapshotSummary0526.isNotBlank()) {
                Text(ridesSnapshotSummary0526, style = MaterialTheme.typography.bodySmall)
            }
            lastRidesSnapshot0526?.let { manifest ->
                OutlinedButton(
                    enabled = !ridesSnapshotRunning0526 &&
                        !ridesSnapshotDownloadRunning0527 &&
                        !ridesSnapshotJsonDownloadRunning0531,
                    onClick = {
                        ridesSnapshotDownloadRunning0527 = true
                        ridesSnapshotProgress0526 = "Preparando download da captura…"
                        scope.launch {
                            try {
                                val result = BlaBlaRidesSnapshotDownload0527.download(context, manifest)
                                ridesSnapshotProgress0526 =
                                    "Download concluído • ${result.displayName} • Downloads/Rota Certa"
                            } catch (error: Throwable) {
                                ridesSnapshotProgress0526 =
                                    "Não foi possível baixar a captura: " +
                                        (error.message ?: error.javaClass.simpleName)
                            } finally {
                                ridesSnapshotDownloadRunning0527 = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (ridesSnapshotDownloadRunning0527) {
                            "⬇️ Baixando captura…"
                        } else {
                            "⬇️ Baixar última captura (.zip)"
                        },
                    )
                }

                OutlinedButton(
                    enabled = !ridesSnapshotRunning0526 &&
                        !ridesSnapshotDownloadRunning0527 &&
                        !ridesSnapshotJsonDownloadRunning0531,
                    onClick = {
                        ridesSnapshotJsonDownloadRunning0531 = true
                        ridesSnapshotProgress0526 = "Preparando JSON estruturado para o Rota Certa…"
                        scope.launch {
                            try {
                                val result = BlaBlaRidesPortableJsonDownload0531.download(context, manifest)
                                ridesSnapshotProgress0526 =
                                    "JSON do Rota Certa concluído • ${result.displayName} • Downloads/Rota Certa"
                            } catch (error: Throwable) {
                                ridesSnapshotProgress0526 =
                                    "Não foi possível gerar o JSON do Rota Certa: " +
                                        (error.message ?: error.javaClass.simpleName)
                            } finally {
                                ridesSnapshotJsonDownloadRunning0531 = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (ridesSnapshotJsonDownloadRunning0531) {
                            "📄 Gerando JSON do Rota Certa…"
                        } else {
                            "📄 Baixar dados para Rota Certa (.json)"
                        },
                    )
                }
            }

            Button(
                enabled = multiProfileAvailable && !ridesSnapshotRunning0526,
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
            "A captura de Suas viagens é somente leitura e salva HTML/MHTML em armazenamento privado. " +
            "O ZIP preserva a evidência forense completa; o JSON reúne os dois perfis em um único arquivo " +
            "estruturado que o próprio Rota Certa valida e consegue reler. Ambos são salvos em Downloads/Rota Certa; " +
            "nenhum ciclo de sincronização pública é iniciado por esta tela.",
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
