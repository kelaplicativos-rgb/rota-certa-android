package br.com.mapeiaia.rotacerta.trips

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class AgendaScriptsSection0560(val label: String) {
    EXECUTOR("Executor"),
    ACTIVE("Em andamento"),
    HISTORY("Histórico"),
    BROWSER("Navegador"),
    DIAGNOSTIC("Diagnóstico"),
}

internal fun AgendaTripExecution0558.stateSummary0560(): String {
    if (items.isEmpty()) return "Sem viagens"
    return items
        .groupingBy { it.state }
        .eachCount()
        .toList()
        .sortedBy { it.first.ordinal }
        .joinToString(" • ") { (state, count) -> "$count ${state.name}" }
}

internal fun agendaScriptsSections0560(): List<AgendaScriptsSection0560> = AgendaScriptsSection0560.entries

class AgendaScriptsHubActivity0560 : ComponentActivity() {
    private val refreshGeneration0560 = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AgendaScriptsHubScreen0560(
                    refreshGeneration = refreshGeneration0560.intValue,
                    onBack = { finish() },
                    onOpenExecutor = {
                        startActivity(Intent(this, AgendaTripScriptExecutorActivity0558::class.java))
                    },
                    onOpenBrowserWorkspace = {
                        // O TripsActivity já ficou posicionado em TripScreen.SCRIPTS antes deste hub abrir.
                        // Fechar o hub revela o workspace legado sem duplicar sua lógica aqui.
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGeneration0560.intValue += 1
    }
}

@Composable
private fun AgendaScriptsHubScreen0560(
    refreshGeneration: Int,
    onBack: () -> Unit,
    onOpenExecutor: () -> Unit,
    onOpenBrowserWorkspace: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AgendaTripScriptStore0558(context.applicationContext) }
    var selected by rememberSaveable { mutableStateOf(AgendaScriptsSection0560.EXECUTOR.name) }
    val selectedSection = runCatching { AgendaScriptsSection0560.valueOf(selected) }
        .getOrDefault(AgendaScriptsSection0560.EXECUTOR)
    val active = remember(refreshGeneration, selectedSection) { store.active() }
    val history = remember(refreshGeneration, selectedSection) { store.history() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Voltar") }
                Text(
                    text = "Scripts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    agendaScriptsSections0560().forEach { section ->
                        val isSelected = section == selectedSection
                        if (isSelected) {
                            Button(
                                onClick = { selected = section.name },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(section.label) }
                        } else {
                            OutlinedButton(
                                onClick = { selected = section.name },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(section.label) }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (selectedSection) {
                        AgendaScriptsSection0560.EXECUTOR -> ExecutorPanel0560(active, onOpenExecutor)
                        AgendaScriptsSection0560.ACTIVE -> ActivePanel0560(active, onOpenExecutor)
                        AgendaScriptsSection0560.HISTORY -> HistoryPanel0560(history, onOpenExecutor)
                        AgendaScriptsSection0560.BROWSER -> BrowserPanel0560(onOpenBrowserWorkspace)
                        AgendaScriptsSection0560.DIAGNOSTIC -> DiagnosticPanel0560(active, history, onOpenExecutor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutorPanel0560(
    active: AgendaTripExecution0558?,
    onOpenExecutor: () -> Unit,
) {
    Text("Executor de Script da Agenda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Cole e valide o contrato declarativo gerado pela Agenda. O conteúdo é tratado como dados: não executa JavaScript, shell ou código arbitrário.",
        style = MaterialTheme.typography.bodyMedium,
    )
    active?.let {
        StatusCard0560(
            title = "Execução pendente",
            body = "${safeId0560(it.scriptId)} • ${it.stateSummary0560()}\nAtualizada em ${formatTimestamp0560(it.updatedAt)}",
        )
    }
    Button(onClick = onOpenExecutor, modifier = Modifier.fillMaxWidth()) {
        Text(if (active == null) "Abrir Executor" else "Retomar no Executor")
    }
    Text(
        "Fluxo seguro: validar → pré-visualizar → simular → executar → reconciliar identidade externa → persistir canônico.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ActivePanel0560(
    active: AgendaTripExecution0558?,
    onOpenExecutor: () -> Unit,
) {
    Text("Em andamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (active == null) {
        Text("Não existe execução de script pendente neste tenant.")
        OutlinedButton(onClick = onOpenExecutor, modifier = Modifier.fillMaxWidth()) { Text("Abrir Executor") }
        return
    }
    ExecutionCard0560(active)
    Button(onClick = onOpenExecutor, modifier = Modifier.fillMaxWidth()) { Text("Reconciliar / retomar") }
    Text(
        "Uma publicação ambígua permanece bloqueada para nova tentativa até a reconciliação, evitando duplicidade.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun HistoryPanel0560(
    history: List<AgendaTripExecution0558>,
    onOpenExecutor: () -> Unit,
) {
    Text("Histórico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (history.isEmpty()) {
        Text("Ainda não há execuções arquivadas.")
    } else {
        history.take(20).forEach { ExecutionCard0560(it) }
    }
    OutlinedButton(onClick = onOpenExecutor, modifier = Modifier.fillMaxWidth()) {
        Text("Abrir histórico completo no Executor")
    }
}

@Composable
private fun BrowserPanel0560(onOpenBrowserWorkspace: () -> Unit) {
    Text("Scripts do navegador", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Workspace avançado já existente para scripts internos de navegação BlaBlaCar. Ele permanece separado do Executor declarativo da Agenda para não misturar contratos nem permissões.",
    )
    Button(onClick = onOpenBrowserWorkspace, modifier = Modifier.fillMaxWidth()) {
        Text("Abrir workspace do navegador")
    }
}

@Composable
private fun DiagnosticPanel0560(
    active: AgendaTripExecution0558?,
    history: List<AgendaTripExecution0558>,
    onOpenExecutor: () -> Unit,
) {
    Text("Diagnóstico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    StatusCard0560(
        title = "Estado persistido",
        body = buildString {
            append(if (active == null) "Nenhuma execução ativa" else "1 execução ativa: ${active.stateSummary0560()}")
            append("\n")
            append("${history.size} execução(ões) arquivada(s)")
        },
    )
    Text(
        "CPF, senhas, cookies, tokens e outros dados de verificação humana não fazem parte do contrato do script e não devem ser registrados pelo Executor.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "A confirmação de interface não equivale a publicação confirmada: a identidade externa e o estado canônico precisam ser reconciliados antes de marcar SYNCED.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onOpenExecutor, modifier = Modifier.fillMaxWidth()) {
        Text("Abrir diagnóstico da execução")
    }
}

@Composable
private fun ExecutionCard0560(execution: AgendaTripExecution0558) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(safeId0560(execution.scriptId), fontWeight = FontWeight.Bold)
            Text("Execução ${execution.executionId.take(8)} • hash ${execution.scriptHash.take(12)}", style = MaterialTheme.typography.bodySmall)
            Text(execution.stateSummary0560(), style = MaterialTheme.typography.bodySmall)
            Text("Atualizada ${formatTimestamp0560(execution.updatedAt)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusCard0560(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun safeId0560(value: String): String = value.take(48).ifBlank { "Script sem identificador" }

private fun formatTimestamp0560(value: Long): String = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(value))
}.getOrDefault(value.toString())
