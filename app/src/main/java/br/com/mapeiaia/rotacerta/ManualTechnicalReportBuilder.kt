package br.com.mapeiaia.rotacerta

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds a technical snapshot only after an explicit user action. */
object ManualTechnicalReportBuilder {
    fun build(context: Context, settings: AppSettings): String {
        ForensicIncidentMonitor0193.markManualReport()

        val appContext = context.applicationContext
        val bubble = appContext.getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)
        val selectedPackages = SelectedRideAppStore.read(appContext).toList().sorted()
        val workPins = WorkRegionTargetPolicy.editablePins(settings)
        val now = System.currentTimeMillis()
        return buildString {
            appendLine("--- FAROL_LATENCY_STAGE9_SNAPSHOT ---")
            val farolLatencyStage9SnapshotStage12 = FarolLatencyProbeStage9.dump()
            if (farolLatencyStage9SnapshotStage12.isBlank()) {
                appendLine("(buffer Stage 9 vazio)")
            } else {
                appendLine(farolLatencyStage9SnapshotStage12)
            }
            appendLine()
            appendLine(FarolMaximumForensicsStage38.exportReport())
            appendLine()
            appendLine(FarolManualOffVisualCommitStage43.exportReport())
            appendLine()
            appendLine(FarolForensicTraceStage20.exportReport())
            appendLine()
            appendLine(FarolVisualIdentityStage23.Metrics.exportReport())
            appendLine()
            appendLine(FarolReadingActivationStage26.Metrics.exportReport())
            appendLine()
            appendLine(FarolCausalLatencyStage28.Metrics.exportReport())
            appendLine()
            appendLine(FarolPresenceAuthorityStage30.Diagnostics.export())
            appendLine()
            appendLine(FarolCardLeaseStage34.Metrics.exportReport())
            appendLine()
            appendLine(FarolRuntimeAuthorityStage36.Metrics.exportReport(null))
            appendLine()
            appendLine(FarolSemanticCardStage32.Metrics.exportReport())
            appendLine()
            appendLine(FarolForensicCardBlackBoxStage32.exportReport())
            appendLine()
            appendLine(FarolForensicCaseStoreStage32.export(appContext))
            appendLine()
            appendLine("ROTA CERTA — RELATORIO TECNICO MANUAL")
            appendLine("Gerado em: ${formatDate(now)}")
            appendLine("Versao: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Pacote: ${appContext.packageName}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Gravador de voo do farol: ATIVO (circular e de baixo impacto)")
            appendLine("Logs legados continuos: ${if (DiagnosticRuntimeGate.isContinuousEnabled()) "ATIVOS" else "DESATIVADOS"}")
            appendLine()
            appendLine("--- CONTROLE ---")
            appendLine("Rota Certa ligado: ${settings.appEnabled}")
            appendLine("Leitura ao vivo: ${settings.liveReadingEnabled}")
            appendLine("Acessibilidade autorizada: ${isAccessibilityEnabled(appContext)}")
            appendLine("Acesso ao uso autorizado: ${SelectedAppUsageStateStage26(appContext).hasUsageAccess()}")
            appendLine("Selecao manual para ativar infraestrutura: true (Stage26)")
            appendLine("Politica Stage19+: conteudo visual atual + dois ou mais enderecos coerentes no mesmo bloco; ultimo endereco e o destino")
            appendLine("Alertas de proximidade: ${settings.proximityAlertsEnabled}")
            appendLine()
            appendLine("--- APLICATIVOS SELECIONADOS ---")
            appendLine("Quantidade: ${selectedPackages.size}")
            if (selectedPackages.isEmpty()) appendLine("- nenhum") else selectedPackages.forEach { appendLine("- $it") }
            appendLine()
            appendLine("--- ESTADO MAIS RECENTE DA BOLINHA ---")
            appendLine("Etapa: ${bubble.text(KEY_STATE_STAGE)}")
            appendLine("Cor: ${bubble.text(KEY_STATE_COLOR)}")
            appendLine("Distancia: ${bubble.text(KEY_STATE_DISTANCE_KM)}")
            appendLine("Motivo: ${bubble.text(KEY_STATE_REASON)}")
            appendLine("Janela: ${bubble.text(KEY_STATE_WINDOW_PACKAGE)}")
            appendLine("Pacote ativo: ${bubble.text(KEY_STATE_ACTIVE_PACKAGE)}")
            appendLine("Pacote do texto: ${bubble.text(KEY_STATE_TEXT_PACKAGE)}")
            appendLine("Servico pronto: ${bubble.getBoolean(KEY_STATE_SERVICE_READY, false)}")
            appendLine("Analise em andamento: ${bubble.getBoolean(KEY_STATE_ANALYZING, false)}")
            appendLine("Hash da tela: ${bubble.text(KEY_STATE_LAST_SNAPSHOT_HASH)}")
            appendLine("Hash analisado: ${bubble.text(KEY_STATE_LAST_ANALYZED_HASH)}")
            appendLine("Texto da acessibilidade: tamanho=${bubble.getInt(KEY_STATE_ACCESSIBILITY_TEXT_LENGTH, 0)}")
            appendLine("Texto do OCR: tamanho=${bubble.getInt(KEY_STATE_OCR_TEXT_LENGTH, 0)}")
            appendLine("Tempo da ultima decisao: ${bubble.getLong(KEY_FAST_FAROL_ELAPSED, -1L).takeIf { it >= 0 }?.toString()?.plus(" ms") ?: "nao registrado"}")
            appendLine("Caminho da ultima decisao: ${bubble.text(KEY_FAST_FAROL_PATH)}")
            appendLine("Ultimo destino calculado: ${bubble.text(KEY_FAST_FAROL_DESTINATION)}")
            appendLine()
            appendLine("--- REGIAO DE TRABALHO ---")
            appendLine("Casa ativa: ${settings.homeTargetEnabled}")
            appendLine("Casa: ${settings.homeAddress.ifBlank { "nao informada" }}")
            appendLine("Raio Casa: ${settings.homeRadiusKm} km")
            appendLine("Alfinetes ativos: ${settings.alternativeTargetEnabled}")
            appendLine("Raio dos alfinetes: ${settings.alternativeRadiusKm} km")
            appendLine("Alfinetes cadastrados: ${workPins.size}")
            workPins.forEach { pin ->
                val coordinate = pin.coordinate?.let { "${it.latitude},${it.longitude}" } ?: "nao validada"
                appendLine("- ${if (pin.enabled) "ON" else "OFF"} | ${pin.address} | $coordinate")
            }
            appendLine()
            appendLine("--- RESUMO LEGADO 0.1.165 — NAO AUTORITATIVO PARA STAGE19+ ---")
            appendLine(FarolDiagnosticSummary0165.withSummary(settings, FarolFlightRecorder0163.exportReport()))
        }.trimEnd()
    }

    private fun android.content.SharedPreferences.text(key: String): String =
        getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao registrado"

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, LiveRideAccessibilityService::class.java).flattenToString()
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.equals(component, ignoreCase = true) }
    }

    private const val BUBBLE_PREFS = "rota_certa_bubble"
    private const val KEY_STATE_STAGE = "state_stage"
    private const val KEY_STATE_REASON = "state_reason"
    private const val KEY_STATE_COLOR = "state_color"
    private const val KEY_STATE_DISTANCE_KM = "state_distance_km"
    private const val KEY_STATE_WINDOW_PACKAGE = "state_window_package"
    private const val KEY_STATE_ACTIVE_PACKAGE = "state_active_package"
    private const val KEY_STATE_TEXT_PACKAGE = "state_text_package"
    private const val KEY_STATE_LAST_SNAPSHOT_HASH = "state_last_snapshot_hash"
    private const val KEY_STATE_LAST_ANALYZED_HASH = "state_last_analyzed_hash"
    private const val KEY_STATE_SERVICE_READY = "state_service_ready"
    private const val KEY_STATE_ANALYZING = "state_analyzing"
    private const val KEY_STATE_ACCESSIBILITY_TEXT_LENGTH = "state_accessibility_text_length"
    private const val KEY_STATE_OCR_TEXT_LENGTH = "state_ocr_text_length"
    private const val KEY_FAST_FAROL_ELAPSED = "fast_farol_last_elapsed_ms"
    private const val KEY_FAST_FAROL_PATH = "fast_farol_last_path"
    private const val KEY_FAST_FAROL_DESTINATION = "fast_farol_last_destination"
}
