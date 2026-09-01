package br.com.mapeiaia.rotacerta.trips

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

internal fun agendaAutomaticSyncIntervalLabel0397(minutes: Long): String = when {
    minutes < 60L -> "$minutes min"
    minutes % 60L == 0L -> {
        val hours = minutes / 60L
        if (hours == 1L) "1 hora" else "$hours horas"
    }
    else -> "$minutes min"
}

@Composable
internal fun AgendaAutomaticSyncScreen0397() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(AgendaBackgroundSyncConfig0392.status(context)) }
    var intervalMenuExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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

        Button(
            onClick = {
                AgendaBackgroundSync0392.enqueueImmediate(context, "manual")
                message = "Sincronização extraordinária solicitada."
                reload()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sincronizar agora")
        }
        OutlinedButton(
            onClick = { reload() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Atualizar status")
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(
            "O Android executa o trabalho periódico com WorkManager. O horário exibido é uma previsão: Doze, App Standby, economia de bateria e restrições do fabricante podem adiar a execução. Após “Forçar parada” nas configurações do Android, a execução automática pode ficar bloqueada até o app ser aberto novamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
