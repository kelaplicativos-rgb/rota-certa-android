package br.com.mapeiaia.rotacerta.trips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.AppBuildInfo
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.ContextualDebugReport0507
import br.com.mapeiaia.rotacerta.ContextualDiagnosticFilter0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import br.com.mapeiaia.rotacerta.ManualTechnicalReportExporter
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DebugPeriod0507(val label: String, val durationMillis: Long?) {
    MINUTES_15("15 min", 15L * 60_000L),
    HOUR_1("1 h", 60L * 60_000L),
    HOURS_24("24 h", 24L * 60L * 60_000L),
    ALL("Tudo", null),
}

private enum class DebugSeverityFilter0507(val label: String) {
    ALL("Todos"),
    WARNINGS("Warnings"),
    ERRORS("Erros"),
}

@Composable
internal fun ContextualDebugReportScreen0507(module: DiagnosticModule0507) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember(module) { mutableStateOf(UnifiedDebugEventStore.snapshot()) }
    var period by remember(module) { mutableStateOf(DebugPeriod0507.HOUR_1) }
    var severity by remember(module) { mutableStateOf(DebugSeverityFilter0507.ALL) }
    var operationQuery by remember(module) { mutableStateOf("") }
    var technicalIdQuery by remember(module) { mutableStateOf("") }
    var expandedKey by remember(module) { mutableStateOf<String?>(null) }
    var status by remember(module) { mutableStateOf<String?>(null) }

    val filter = remember(snapshot, period, severity, operationQuery, technicalIdQuery) {
        ContextualDiagnosticFilter0507(
            sinceMillis = period.durationMillis?.let { System.currentTimeMillis() - it },
            severities = when (severity) {
                DebugSeverityFilter0507.ALL -> DiagnosticSeverity0507.entries.toSet()
                DebugSeverityFilter0507.WARNINGS -> setOf(DiagnosticSeverity0507.WARNING, DiagnosticSeverity0507.ERROR)
                DebugSeverityFilter0507.ERRORS -> setOf(DiagnosticSeverity0507.ERROR)
            },
            operationQuery = operationQuery,
            technicalIdQuery = technicalIdQuery,
        )
    }
    val events = remember(snapshot, module, filter) {
        ContextualDebugReport0507.select(snapshot, module, filter)
            .mapNotNull(ContextualDebugReport0507::present)
    }

    fun reportHeader0507(): List<String> = listOf(
        "ROTA CERTA — RELATÓRIO DE DEPURAÇÃO",
        "Gerado em: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date()),
        "VersionName: ${BuildConfig.VERSION_NAME}",
        "VersionCode: ${BuildConfig.VERSION_CODE}",
        "Commit/SHA: ${AppBuildInfo.commit}",
        "Branch: ${AppBuildInfo.branch}",
        "Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
        "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
        "Filtro: periodo=${period.label}; severidade=${severity.label}; operation=${operationQuery.ifBlank { "*" }}; id=${technicalIdQuery.ifBlank { "*" }}",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Relatório de depuração — ${module.label}", style = MaterialTheme.typography.titleLarge)
        Text(
            "Fonte única: flight recorder do Rota Certa. Eventos correlacionados de outros componentes aparecem somente por correlationId/traceId/operationId explícito.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DebugPeriod0507.entries.forEach { choice ->
                OutlinedButton(onClick = { period = choice }) { Text(choice.label) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DebugSeverityFilter0507.entries.forEach { choice ->
                OutlinedButton(onClick = { severity = choice }) { Text(choice.label) }
            }
        }
        OutlinedTextField(
            value = operationQuery,
            onValueChange = { operationQuery = it.take(80) },
            label = { Text("Operação") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = technicalIdQuery,
            onValueChange = { technicalIdQuery = it.take(120) },
            label = { Text("ID técnico / correlationId") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { snapshot = UnifiedDebugEventStore.snapshot(); status = null }) {
                Text("Atualizar")
            }
            Button(onClick = {
                scope.launch {
                    val report = withContext(Dispatchers.Default) {
                        ContextualDebugReport0507.export(module, snapshot, filter, reportHeader0507())
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Rota Certa — ${module.label}", report))
                    status = "Relatório sanitizado copiado."
                }
            }) { Text("Copiar relatório") }
            OutlinedButton(onClick = {
                scope.launch {
                    val report = withContext(Dispatchers.Default) {
                        ContextualDebugReport0507.export(module, snapshot, filter, reportHeader0507())
                    }
                    val saved = withContext(Dispatchers.IO) {
                        ManualTechnicalReportExporter.saveToDownloads(context, report)
                    }
                    status = saved.fold(
                        onSuccess = { "Exportado: ${it.displayName}" },
                        onFailure = { "Falha ao exportar: ${it.javaClass.simpleName}" },
                    )
                }
            }) { Text("Exportar sanitizado") }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Eventos: ${events.size} • buffer global: ${snapshot.events.size}/${snapshot.bufferCapacity} • descartados: ${snapshot.droppedEvents}",
            style = MaterialTheme.typography.bodySmall,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = events.take(500),
                key = { event -> "${event.monotonicNs}:${event.stage}:${event.context.operationId}" },
            ) { event ->
                val key = "${event.monotonicNs}:${event.stage}:${event.context.operationId}"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedKey = if (expandedKey == key) null else key },
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(event.atMillis))
                        Text(
                            "$time  ${event.context.severity.name}  ${event.context.operation.ifBlank { event.stage }}  ${event.context.result}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${event.context.originModule.name} → ${event.context.executorModule.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (expandedKey == key) {
                            Text("Stage: ${event.stage}", style = MaterialTheme.typography.bodySmall)
                            if (event.context.correlationId.isNotBlank()) Text("CorrelationId: ${event.context.correlationId}", style = MaterialTheme.typography.bodySmall)
                            if (event.context.traceId.isNotBlank()) Text("TraceId: ${event.context.traceId}", style = MaterialTheme.typography.bodySmall)
                            if (event.context.operationId.isNotBlank()) Text("OperationId: ${event.context.operationId}", style = MaterialTheme.typography.bodySmall)
                            if (event.context.entityId.isNotBlank()) Text("ID: ${event.context.entityId}", style = MaterialTheme.typography.bodySmall)
                            if (event.context.errorCode.isNotBlank()) Text("Erro: ${event.context.errorCode}", style = MaterialTheme.typography.bodySmall)
                            if (event.context.reason.isNotBlank()) Text("Motivo: ${event.context.reason}", style = MaterialTheme.typography.bodySmall)
                            event.context.durationMs?.let { Text("Duração: $it ms", style = MaterialTheme.typography.bodySmall) }
                            if (event.details.isNotBlank()) Text(event.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
