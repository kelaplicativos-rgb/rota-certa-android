package br.com.mapeiaia.rotacerta

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Monta um retrato técnico somente quando solicitado pelo usuário.
 * Não inicia OCR, não percorre acessibilidade e não grava eventos continuamente.
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
        val workPins = WorkRegionTargetPolicy.editablePins(settings)
        val now = System.currentTimeMillis()
        val stateUpdatedAt = bubble.getLong(KEY_STATE_UPDATED_AT, 0L)
        val elapsedValue = bubble.getLong(KEY_FAST_FAROL_ELAPSED, -1L)
        val elapsedText = if (elapsedValue >= 0L) "$elapsedValue ms" else "nao registrado"
        val decisionPath = bubble.getString(KEY_FAST_FAROL_PATH, null)?.takeIf { it.isNotBlank() }
            ?: "nao registrado"
        val lastDestination = bubble.getString(KEY_FAST_FAROL_DESTINATION, null)?.takeIf { it.isNotBlank() }
            ?: "nao registrado"

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
            appendLine("Somente apps salvos: true")
            appendLine("Modelo visual bloqueia o farol: false; modelos são apenas apoio")
            appendLine("Politica do farol: aplicativo salvo + dois ou mais enderecos; o ultimo e o destino")
            appendLine("Alertas de proximidade: ${settings.proximityAlertsEnabled}")
            appendLine()

            appendLine("--- APLICATIVOS E MODELOS DE APOIO ---")
            appendLine("Aplicativos salvos: ${selectedPackages.size}")
            if (selectedPackages.isEmpty()) appendLine("- nenhum")
            selectedPackages.forEach { appendLine("- $it") }
            appendLine("Modelos visuais de apoio: ${cardTemplates.size}")
            cardTemplates.forEach { template ->
                val packageLabel = template.packageName ?: "nao informado"
                appendLine("- ${template.name} | pacote=$packageLabel | recursos=${template.requiredFeatures.size}")
            }
            appendLine()

            appendLine("--- ESTADO MAIS RECENTE DA BOLINHA ---")
            val updatedText = if (stateUpdatedAt > 0L) formatDate(stateUpdatedAt) else "nao registrado"
            val ageText = if (stateUpdatedAt > 0L) "${(now - stateUpdatedAt).coerceAtLeast(0L)} ms" else "nao registrada"
            appendLine("Atualizado em: $updatedText")
            appendLine("Idade do estado: $ageText")
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
            appendLine("Tempo da ultima decisao: $elapsedText")
            appendLine("Caminho da ultima decisao: $decisionPath")
            appendLine("Ultimo destino calculado: $lastDestination")
            appendLine()

            appendLine("--- REGIAO DE TRABALHO ---")
            appendLine("Casa ativa: ${settings.homeTargetEnabled}")
            val homeAddressText = settings.homeAddress.ifBlank { "nao informada" }
            appendLine("Casa: $homeAddressText")
            appendLine("Raio Casa: ${settings.homeRadiusKm} km")
            appendLine("Grupo de alfinetes ativo: ${settings.alternativeTargetEnabled}")
            appendLine("Raio compartilhado dos alfinetes: ${settings.alternativeRadiusKm} km")
            appendLine("Alfinetes cadastrados: ${workPins.size}")
            if (workPins.isEmpty()) appendLine("- nenhum")
            workPins.forEach { pin ->
                val coordinate = pin.coordinate?.let { "${it.latitude},${it.longitude}" } ?: "nao validada"
                val status = if (pin.enabled) "ON" else "OFF"
                appendLine("- $status | ${pin.address} | $coordinate")
            }
            appendLine()

            appendLine("Observacao: o tempo acima mede da primeira leitura do novo destino ate a pintura verde/vermelha. Cache exato deve retornar em milissegundos; rota nova depende da rede.")
        }.trimEnd()
    }

    private fun android.content.SharedPreferences.text(key: String): String =
        getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao registrado"

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, LiveRideAccessibilityService::class.java).flattenToString()
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
    private const val KEY_FAST_FAROL_ELAPSED = "fast_farol_last_elapsed_ms"
    private const val KEY_FAST_FAROL_PATH = "fast_farol_last_path"
    private const val KEY_FAST_FAROL_DESTINATION = "fast_farol_last_destination"
}
