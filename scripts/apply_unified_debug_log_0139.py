from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Substituição insegura em {path}: esperado 1, encontrado {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Porta de diagnóstico: modo contínuo explícito, ainda somente em memória.
gate = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/DiagnosticRuntimeGate.kt"
replace_once(gate,
"    @Volatile\n    private var manualCaptureUntilMillis: Long = 0L\n",
"    @Volatile\n    private var manualCaptureUntilMillis: Long = 0L\n\n    @Volatile\n    private var continuousEnabled: Boolean = false\n")
replace_once(gate,
"    /** Compatibilidade: false encerra a coleta; true nao ativa diagnostico continuo. */\n    fun setEnabled(value: Boolean) {\n        if (!value) manualCaptureUntilMillis = 0L\n    }\n",
"    /** Liga ou desliga a coleta circular contínua solicitada pelo usuário. */\n    fun setEnabled(value: Boolean) {\n        continuousEnabled = value\n        if (!value) manualCaptureUntilMillis = 0L\n    }\n\n    fun isContinuousEnabled(): Boolean = continuousEnabled\n")
replace_once(gate,
"    fun isEnabled(nowMillis: Long = System.currentTimeMillis()): Boolean {\n        val deadline = manualCaptureUntilMillis\n",
"    fun isEnabled(nowMillis: Long = System.currentTimeMillis()): Boolean {\n        if (continuousEnabled) return true\n        val deadline = manualCaptureUntilMillis\n")

# Preferência persistente e trilha circular de eventos leves.
(ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/UnifiedDebugLog.kt").write_text(r'''package br.com.mapeiaia.rotacerta

import android.content.Context
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object DebugLogPreferenceStore {
    private const val PREFS = "rota_certa_debug_log"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        DiagnosticRuntimeGate.setEnabled(enabled)
    }
}

object UnifiedDebugEventStore {
    private const val MAX_EVENTS = 2_000
    private const val MAX_DETAILS = 1_000
    private val lock = Any()
    private val events = ArrayDeque<Event>(MAX_EVENTS)

    fun record(stage: String, packageName: String?, details: String = "", nowMillis: Long = System.currentTimeMillis()) {
        if (!DiagnosticRuntimeGate.isEnabled(nowMillis)) return
        val event = Event(
            atMillis = nowMillis,
            stage = sanitize(stage).ifBlank { "EVENT" },
            packageName = sanitize(packageName.orEmpty()).ifBlank { "nao informado" },
            details = maskSensitive(sanitize(details)).take(MAX_DETAILS),
        )
        synchronized(lock) {
            while (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(event)
        }
    }

    fun clear() = synchronized(lock) { events.clear() }

    fun size(): Int = synchronized(lock) { events.size }

    fun dump(): String = synchronized(lock) {
        if (events.isEmpty()) return@synchronized "sem eventos na trilha unificada"
        events.joinToString("\n") { event ->
            "${format(event.atMillis)} | ${event.stage} | pacote=${event.packageName} | ${event.details}".trimEnd(' ', '|')
        }
    }

    private fun sanitize(value: String): String = value.replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex("\\s{2,}"), " ").trim()

    private fun maskSensitive(value: String): String = value
        .replace(Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[-\\s]?\\d{4}(?!\\d)"), "[telefone mascarado]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email mascarado]")

    private fun format(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))

    private data class Event(
        val atMillis: Long,
        val stage: String,
        val packageName: String,
        val details: String,
    )
}
''', encoding="utf-8")

# Serviço: restaura ON/OFF e registra cada evento relevante antes dos filtros.
service = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(service,
"        repository = SettingsRepository(applicationContext)\n",
"        repository = SettingsRepository(applicationContext)\n        DiagnosticRuntimeGate.setEnabled(DebugLogPreferenceStore.isEnabled(applicationContext))\n        UnifiedDebugEventStore.record(\"SERVICE_CREATE\", packageName, \"serviço de acessibilidade criado\")\n")
replace_once(service,
"    override fun onServiceConnected() {\n        super.onServiceConnected()\n        serviceReady = true\n",
"    override fun onServiceConnected() {\n        super.onServiceConnected()\n        serviceReady = true\n        UnifiedDebugEventStore.record(\"SERVICE_CONNECTED\", packageName, \"serviço pronto=true\")\n")
replace_once(service,
"    override fun onAccessibilityEvent(event: AccessibilityEvent?) {\n        if (!serviceReady || event == null) return\n",
"    override fun onAccessibilityEvent(event: AccessibilityEvent?) {\n        if (event == null) return\n        UnifiedDebugEventStore.record(\n            stage = \"ACCESSIBILITY_EVENT\",\n            packageName = event.packageName?.toString(),\n            details = \"type=${event.eventType}; class=${event.className}; window=${event.windowId}; serviceReady=$serviceReady\",\n        )\n        if (!serviceReady) return\n")
replace_once(service,
"    override fun onInterrupt() = Unit\n",
"    override fun onInterrupt() {\n        UnifiedDebugEventStore.record(\"SERVICE_INTERRUPT\", packageName, \"Android interrompeu o serviço\")\n    }\n")
replace_once(service,
"    override fun onDestroy() {\n",
"    override fun onDestroy() {\n        UnifiedDebugEventStore.record(\"SERVICE_DESTROY\", packageName, \"serviço destruído\")\n")

# Tela: uma chave ON/OFF e um único gerador de relatório.
main = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"
replace_once(main,
"    var supportReportStatus by remember { mutableStateOf(\"\") }\n",
"    var supportReportStatus by remember { mutableStateOf(\"\") }\n    var debugLogEnabled by remember { mutableStateOf(DebugLogPreferenceStore.isEnabled(context)) }\n")
replace_once(main,
"            supportReportStatus = \"Gerando relatorio...\"\n            Unit /* production_log_removed_checklist_4 */\n",
"            supportReportStatus = \"Gerando relatorio...\"\n            UnifiedDebugEventStore.record(\"REPORT_EXPORT\", context.packageName, \"exportação manual solicitada\")\n")
replace_once(main,
"            supportReportFileCreator.launch(\"rota-certa-relatorio-completo.txt\")\n",
"            supportReportFileCreator.launch(\"rota-certa-relatorio-depuracao.txt\")\n")
replace_once(main,
"                TAB_HISTORY -> ReportsGroupScreen(\n                    settings = settings,\n                    diagnostic = null,\n                    history = history,\n                )\n",
"                TAB_HISTORY -> ReportsGroupScreen(\n                    settings = settings,\n                    diagnostic = null,\n                    history = history,\n                    debugLogEnabled = debugLogEnabled,\n                    onDebugLogChange = { enabled ->\n                        debugLogEnabled = enabled\n                        DebugLogPreferenceStore.setEnabled(context, enabled)\n                        if (enabled) {\n                            DiagnosticLogStore.clear()\n                            LiveFailureTraceStore.clear()\n                            UnifiedDebugEventStore.clear()\n                            UnifiedDebugEventStore.record(\"DEBUG_LOG_ON\", context.packageName, \"coleta circular ativada pelo usuário\")\n                        }\n                    },\n                    onCreateReport = { supportReportFileCreator.launch(\"rota-certa-relatorio-depuracao.txt\") },\n                    onClearReport = {\n                        DiagnosticLogStore.clear()\n                        LiveFailureTraceStore.clear()\n                        UnifiedDebugEventStore.clear()\n                        supportReportStatus = \"Registros apagados.\"\n                    },\n                    reportStatus = supportReportStatus,\n                )\n")
start = main.read_text(encoding="utf-8")
old_start = start.index("@Composable\nprivate fun DiagnosticExpander(")
old_end = start.index("\n@Composable\nprivate fun SavedPlacesCard(", old_start)
new_diag = r'''@Composable
private fun DiagnosticExpander(
    debugLogEnabled: Boolean,
    onDebugLogChange: (Boolean) -> Unit,
    onCreateReport: () -> Unit,
    onClearReport: () -> Unit,
    reportStatus: String,
) {
    ExpandableCard(title = "Relatório para depuração", initiallyExpanded = true) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Log de depuração", fontWeight = FontWeight.Bold)
                Text(
                    if (debugLogEnabled) "ON — eventos relevantes ficam em memória circular até a exportação." else "OFF — nenhuma coleta detalhada contínua.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = debugLogEnabled, onCheckedChange = onDebugLogChange)
        }
        Text(
            "A coleta não grava eventos continuamente no armazenamento. Telefones e e-mails são mascarados no arquivo.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onCreateReport, modifier = Modifier.fillMaxWidth()) {
            Text("Gerar relatório para depuração")
        }
        OutlinedButton(onClick = onClearReport, modifier = Modifier.fillMaxWidth()) {
            Text("Apagar registros")
        }
        if (reportStatus.isNotBlank()) Text(reportStatus, style = MaterialTheme.typography.bodySmall)
    }
}
'''
main.write_text(start[:old_start] + new_diag + start[old_end:], encoding="utf-8")

text = main.read_text(encoding="utf-8")
old_reports = '''private fun ReportsGroupScreen(
    settings: AppSettings,
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Relatorios e historico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DiagnosticExpander(
            settings = settings,
            diagnostic = diagnostic,
        )
        Text("Historico de decisoes", fontWeight = FontWeight.Bold)
        HistoryScreen(history)
    }
}'''
new_reports = '''private fun ReportsGroupScreen(
    settings: AppSettings,
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
    debugLogEnabled: Boolean,
    onDebugLogChange: (Boolean) -> Unit,
    onCreateReport: () -> Unit,
    onClearReport: () -> Unit,
    reportStatus: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Relatórios e histórico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DiagnosticExpander(
            debugLogEnabled = debugLogEnabled,
            onDebugLogChange = onDebugLogChange,
            onCreateReport = onCreateReport,
            onClearReport = onClearReport,
            reportStatus = reportStatus,
        )
        Text("Histórico de decisões", fontWeight = FontWeight.Bold)
        HistoryScreen(history)
    }
}'''
if new_reports not in text:
    if text.count(old_reports) != 1:
        raise SystemExit("Bloco ReportsGroupScreen divergente")
    main.write_text(text.replace(old_reports, new_reports, 1), encoding="utf-8")

# Relatório único: agrega os dois relatórios antigos e a trilha nova.
replace_once(main,
"        appendLine(\"Leitura ao vivo ativa: $liveEnabled\")\n",
"        appendLine(\"Leitura ao vivo ativa: $liveEnabled\")\n        appendLine(\"Log de depuração: ${if (DebugLogPreferenceStore.isEnabled(context)) \"ON\" else \"OFF\"}\")\n        appendLine(\"Eventos na trilha unificada: ${UnifiedDebugEventStore.size()}\")\n")
replace_once(main,
"        appendLine(\"--- ULTIMA TENTATIVA REAL DA BOLINHA ---\")\n",
"        appendLine(\"--- RESUMO TÉCNICO UNIFICADO ---\")\n        appendLine(ManualTechnicalReportBuilder.build(context = context, settings = settings))\n        appendLine()\n        appendLine(\"--- ULTIMA TENTATIVA REAL DA BOLINHA ---\")\n")
replace_once(main,
"        appendLine(\"--- LINHA DO TEMPO COMPLETA DA EXECUCAO ---\")\n        appendLine(complementaryEvents.ifBlank { \"sem eventos complementares\" })\n",
"        appendLine(\"--- EVENTOS UNIFICADOS DA EXECUÇÃO ---\")\n        appendLine(UnifiedDebugEventStore.dump())\n        appendLine()\n        appendLine(\"--- EVENTOS TÉCNICOS COMPLEMENTARES ---\")\n        appendLine(complementaryEvents.ifBlank { \"sem eventos complementares\" })\n")

# Versão.
gradle = ROOT / "app/build.gradle.kts"
replace_once(gradle, 'val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 3_000 + it }\nval appVersionCode = ciVersionCode ?: 3_001\n', 'val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 4_000 + it }\nval appVersionCode = ciVersionCode ?: 4_001\n')
replace_once(gradle, 'versionName = "0.1.138"', 'versionName = "0.1.139"')

# Teste contratual sem dependência Android.
(ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/UnifiedDebugReport139Test.kt").write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class UnifiedDebugReport139Test {
    @Test
    fun interface_has_one_report_generator_and_debug_toggle() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").readText()
        assertContains(source, "Log de depuração")
        assertContains(source, "Gerar relatório para depuração")
        assertContains(source, "Eventos unificados")
        assertFalse(source.contains("Gerar e baixar relatorio"))
    }

    @Test
    fun service_records_events_before_filters() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        assertContains(source, "ACCESSIBILITY_EVENT")
        assertContains(source, "DiagnosticRuntimeGate.setEnabled(DebugLogPreferenceStore.isEnabled")
    }
}
''', encoding="utf-8")

print("Diagnóstico unificado 0.1.139 aplicado com sucesso")
