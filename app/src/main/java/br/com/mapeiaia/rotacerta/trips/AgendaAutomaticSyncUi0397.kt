package br.com.mapeiaia.rotacerta.trips

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Date

internal val agendaAutomaticSyncIntervals0397 = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1_440L)

internal fun openPublicAgenda0397(
    context: android.content.Context,
    store: TripStore,
): String {
    val url = store.onlineSettings().publicAgendaUrl
    if (url.isNullOrBlank()) {
        return "Configure a integração online para abrir a Agenda Pública."
    }
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.fold(
        onSuccess = { "Abrindo a Agenda Pública do Rota Certa." },
        onFailure = { "Não foi possível abrir a Agenda Pública neste aparelho." },
    )
}

internal fun agendaAutomaticSyncIntervalLabel0397(minutes: Long): String = when {
    minutes < 60L -> "$minutes min"
    minutes % 60L == 0L -> {
        val hours = minutes / 60L
        if (hours == 1L) "1 hora" else "$hours horas"
    }
    else -> "$minutes min"
}

@Composable
internal fun AgendaAutomaticSyncScreen0397(
    trips: List<Trip>,
    store: TripStore,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(AgendaBackgroundSyncConfig0392.status(context)) }
    val collectorRevision0400 by BlaBlaCollectorTimelineEvents0400.revision.collectAsState()
    val collectorState0400 = remember(status, collectorRevision0400) {
        AgendaBackgroundSyncConfig0392.collectorState0400(context)
    }
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    val collectorResponse = remember(collectorRevision0400) {
        collectorStore.lastResponseRecoveringDynamicSessions()
    }
    var intervalMenuExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val onlineSettings = remember(status) { store.onlineSettings() }

    fun reload() {
        status = AgendaBackgroundSyncConfig0392.status(context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sincronização automática", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (status.enabled) "Ligada" else "Desligada",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = status.enabled,
                        onCheckedChange = { enabled ->
                            AgendaBackgroundSyncConfig0392.updateEnabled(context, enabled)
                            reload()
                            message = if (enabled) {
                                "Sincronização automática ligada."
                            } else {
                                "Sincronização periódica desligada. Eventos imediatos continuam protegendo as mutações locais."
                            }
                        },
                    )
                }

                Text("Intervalo: ${agendaAutomaticSyncIntervalLabel0397(status.intervalMinutes)}")
                OutlinedButton(
                    onClick = { intervalMenuExpanded = true },
                    enabled = status.enabled,
                ) {
                    Text("Alterar intervalo")
                }
                DropdownMenu(
                    expanded = intervalMenuExpanded,
                    onDismissRequest = { intervalMenuExpanded = false },
                ) {
                    agendaAutomaticSyncIntervals0397.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(agendaAutomaticSyncIntervalLabel0397(minutes)) },
                            onClick = {
                                intervalMenuExpanded = false
                                AgendaBackgroundSyncConfig0392.updateIntervalMinutes(context, minutes)
                                reload()
                                message = "Intervalo atualizado para ${agendaAutomaticSyncIntervalLabel0397(minutes)}."
                            },
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Estado do scheduler", style = MaterialTheme.typography.titleMedium)
                Text("Última execução: ${agendaAutomaticSyncDateTime0397(context, status.lastFinishedAtMillis)}")
                val next = status.nextExecutionEstimateMillis()
                Text(
                    "Próxima execução prevista: " +
                        if (!status.enabled) "desativada" else agendaAutomaticSyncDateTime0397(context, next),
                )
                Text("Último gatilho: ${status.lastTrigger.ifBlank { "—" }}")
                Text("Último resultado: ${status.lastResult.ifBlank { "—" }}")
                Text(
                    "Coleta BlaBlaCar: " + when {
                        collectorState0400.status == "COMPLETE" -> "COMPLETE"
                        collectorState0400.status == "NO_ACCOUNTS" -> "sem contas configuradas"
                        collectorState0400.status == "PENDING_AUTH" -> "PENDING_AUTH · ação necessária: reconecte ${collectorState0400.pendingAuthAccountIds.size} conta(s)"
                        collectorState0400.pending -> "${collectorState0400.status} · ${collectorState0400.completedAccountIds.size + collectorState0400.failedAccountIds.size + collectorState0400.pendingAuthAccountIds.size}/${collectorState0400.targetAccountIds.size} contas processadas"
                        else -> collectorState0400.status
                    },
                )
                Text(
                    if (status.retryPending) {
                        "Falha/retry: retry pendente · tentativa ${status.retryAttempt + 1}"
                    } else if (status.lastFailures > 0) {
                        "Falha/retry: última execução registrou ${status.lastFailures} falha(s)"
                    } else {
                        "Falha/retry: sem retry pendente"
                    },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Backend Rota Certa", style = MaterialTheme.typography.titleMedium)
                Text(
                    "O servidor é a fonte canônica. Este aparelho coleta dados da BlaBlaCar e envia operações idempotentes ao backend.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = {
                        message = openPublicAgenda0397(context, store)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = onlineSettings.configured,
                ) {
                    Text("ABRIR AGENDA PÚBLICA")
                }
            }
        }

        BlaBlaAccountsAndBrowsersScreen0399()

        BlaBlaCollectorPanel(
            trips = trips,
            stateStore = collectorStore,
            currentResponse = collectorResponse,
            onResult = { /* BlaBlaCollectorPanel already persists through collectorStore. */ },
            onChanged = onChanged,
            showAccountManagement = false,
        )

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(
            "Esta é a central de coleta BlaBlaCar do Android. O resultado confirmado é persistido no Backend Rota Certa; a Agenda Pública somente leitura exibe o mesmo estado canônico. O Android mantém apenas cache, sessão e transporte offline. O WorkManager pode ser adiado por Doze, App Standby, economia de bateria ou restrições do fabricante.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
internal fun AgendaAutomaticSyncTimelineStatus0398(
    status: AgendaBackgroundSyncStatus0397,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (status.enabled) "Sincronização automática ligada" else "Sincronização automática desligada",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Última execução: ${agendaAutomaticSyncDateTime0397(context, status.lastFinishedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Resultado: ${status.lastResult.ifBlank { "Ainda não executada" }}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (status.retryPending || status.lastFailures > 0) {
                Text(
                    "⚠️ Falhas: ${status.lastFailures} · retry ${if (status.retryPending) "pendente" else "não pendente"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun agendaAutomaticSyncDateTime0397(
    context: android.content.Context,
    millis: Long,
): String {
    if (millis <= 0L) return "ainda não registrada"
    val dateFormat = DateFormat.getMediumDateFormat(context)
    val timeFormat = DateFormat.getTimeFormat(context)
    val date = Date(millis)
    return "${dateFormat.format(date)} · ${timeFormat.format(date)}"
}
