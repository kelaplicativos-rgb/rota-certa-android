package br.com.mapeiaia.rotacerta

import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Monta um retrato tecnico apenas quando solicitado pelo usuario.
 * Nao inicia OCR, nao percorre a arvore de acessibilidade e nao mantem trilha
 * continua. Le somente configuracoes e o ultimo estado operacional ja salvo.
 */
object ManualTechnicalReportBuilder {
    fun build(
        context: Context,
        settings: AppSettings,
        cardTemplates: List<RideCardTemplate>,
    ): String {
        val appContext = context.applicationContext
        val bubble = appContext.getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE)
        val selectedPackages = SelectedRideAppStore.read(appContext).toList().sorted()
        val now = System.currentTimeMillis()
        val stateUpdatedAt = bubble.getLong(KEY_STATE_UPDATED_AT, 0L)

        return buildString {
            appendLine("ROTA CERTA — RELATORIO TECNICO MANUAL")
            appendLine("Marcador: MANUAL_TECHNICAL_REPORT")
            appendLine("Gerado em: ${formatDate(now)}")
            appendLine("Versao: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Pacote: ${appContext.packageName}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Aparelho: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Logs continuos: DESATIVADOS")
            appendLine("Diagnostico automatico: DESATIVADO")
            appendLine()

            appendLine("--- CONTROLE ---")
            appendLine("Rota Certa ligado: ${settings.appEnabled}")
            appendLine("Leitura ao vivo: ${settings.liveReadingEnabled}")
            appendLine("Acessibilidade autorizada: ${isAccessibilityEnabled(appContext)}")
            appendLine("Somente apps selecionados: true")
            appendLine("Modelo de card obrigatorio: ${settings.requireRegisteredRideCard}")
            appendLine("Alertas de proximidade: ${settings.proximityAlertsEnabled}")
            appendLine()

            appendLine("--- APLICATIVOS E MODELOS ---")
            appendLine("Aplicativos selecionados: ${selectedPackages.size}")
            if (selectedPackages.isEmpty()) appendLine("- nenhum")
            selectedPackages.forEach { appendLine("- $it") }
            appendLine("Modelos cadastrados: ${cardTemplates.size}")
            cardTemplates.forEach { template ->
                appendLine("- ${template.name} | pacote=${template.packageName ?: "nao informado"} | recursos=${template.requiredFeatures.size}")
            }
            appendLine()

            appendLine("--- ESTADO MAIS RECENTE DA BOLINHA ---")
            appendLine("Atualizado em: ${stateUpdatedAt.takeIf { it > 0L }?.let(::formatDate) ?: "nao registrado"}")
            appendLine("Idade do estado: ${stateUpdatedAt.takeIf { it > 0L }?.let { (now - it).coerceAtLeast(0L).toString() + " ms" } ?: "nao registrada"}")
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
            appendLine("Hash pendente: ${bubble.text(KEY_STATE_PENDING_HASH)}")
            appendLine("Texto da acessibilidade: tamanho=${bubble.getInt(KEY_STATE_ACCESSIBILITY_TEXT_LENGTH, 0)} hash=${bubble.text(KEY_STATE_ACCESSIBILITY_TEXT_HASH)}")
            appendLine("Texto do OCR: tamanho=${bubble.getInt(KEY_STATE_OCR_TEXT_LENGTH, 0)} hash=${bubble.text(KEY_STATE_OCR_TEXT_HASH)}")
            appendLine()

            appendLine("--- DESTINOS CONFIGURADOS ---")
            appendLine("Casa ativa: ${settings.homeTargetEnabled}")
            appendLine("Casa: ${settings.homeAddress.ifBlank { "nao informada" }}")
            appendLine("Raio casa: ${settings.homeRadiusKm} km")
            appendLine("Alfinete ativo: ${settings.alternativeTargetEnabled}")
            appendLine("Alfinete: ${settings.alternativeAddress.ifBlank { "nao informado" }}")
            appendLine("Raio alfinete: ${settings.alternativeRadiusKm} km")
            appendLine()

            appendLine("Observacao: este arquivo e um retrato manual. Nenhuma trilha de eventos fica sendo acumulada durante o uso normal.")
        }.trimEnd()
    }

    private fun android.content.SharedPreferences.text(key: String): String =
        getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao registrado"

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val component = "${context.packageName}/${LiveRideAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private const val BUBBLE_PREFS = "rota_certa_bubble"
    private const val KEY_STATE_UPDATED_AT = "state_updated_at"
    private const val KEY_STATE_STAGE = "state_stage"
    private const val KEY_STATE_REASON = "state_reason"
    private const val KEY_STATE_COLOR = "state_color"
    private const val KEY_STATE_DISTANCE_KM = "state_distance_km"
    private const val KEY_STATE_WINDOW_PACKAGE = "state_window_package"
    private const val KEY_STATE_ACTIVE_PACKAGE = "state_active_package"
    private const val KEY_STATE_TEXT_PACKAGE = "state_text_package"
    private const val KEY_STATE_LAST_SNAPSHOT_HASH = "state_last_snapshot_hash"
    private const val KEY_STATE_LAST_ANALYZED_HASH = "state_last_analyzed_hash"
    private const val KEY_STATE_PENDING_HASH = "state_pending_hash"
    private const val KEY_STATE_SERVICE_READY = "state_service_ready"
    private const val KEY_STATE_ANALYZING = "state_analyzing"
    private const val KEY_STATE_ACCESSIBILITY_TEXT_LENGTH = "state_accessibility_text_length"
    private const val KEY_STATE_ACCESSIBILITY_TEXT_HASH = "state_accessibility_text_hash"
    private const val KEY_STATE_OCR_TEXT_LENGTH = "state_ocr_text_length"
    private const val KEY_STATE_OCR_TEXT_HASH = "state_ocr_text_hash"
}
