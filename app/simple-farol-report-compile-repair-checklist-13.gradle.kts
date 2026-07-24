// Gera o relatório técnico final com valores pré-calculados e sem templates aninhados frágeis.

fun writeStableSimpleFarolReport13(file: java.io.File) {
    if (!file.exists()) throw GradleException("ManualTechnicalReportBuilder.kt ausente no reparo 13.")
    val source = """package br.com.mapeiaia.rotacerta

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
        val elapsedText = if (elapsedValue >= 0L) "@@D@@elapsedValue ms" else "nao registrado"
        val decisionPath = bubble.getString(KEY_FAST_FAROL_PATH, null)?.takeIf { it.isNotBlank() }
            ?: "nao registrado"
        val lastDestination = bubble.getString(KEY_FAST_FAROL_DESTINATION, null)?.takeIf { it.isNotBlank() }
            ?: "nao registrado"

        return buildString {
            appendLine("ROTA CERTA — RELATORIO TECNICO MANUAL")
            appendLine("Marcador: MANUAL_TECHNICAL_REPORT")
            appendLine("Gerado em: @@D@@{formatDate(now)}")
            appendLine("Versao: @@D@@{BuildConfig.VERSION_NAME} (@@D@@{BuildConfig.VERSION_CODE})")
            appendLine("Pacote: @@D@@{appContext.packageName}")
            appendLine("Android: @@D@@{android.os.Build.VERSION.RELEASE} / API @@D@@{android.os.Build.VERSION.SDK_INT}")
            appendLine("Aparelho: @@D@@{android.os.Build.MANUFACTURER} @@D@@{android.os.Build.MODEL}")
            appendLine("Logs continuos: DESATIVADOS")
            appendLine("Diagnostico automatico: DESATIVADO")
            appendLine()

            appendLine("--- CONTROLE ---")
            appendLine("Rota Certa ligado: @@D@@{settings.appEnabled}")
            appendLine("Leitura ao vivo: @@D@@{settings.liveReadingEnabled}")
            appendLine("Acessibilidade autorizada: @@D@@{isAccessibilityEnabled(appContext)}")
            appendLine("Somente apps salvos: true")
            appendLine("Modelo visual bloqueia o farol: false; modelos são apenas apoio")
            appendLine("Politica do farol: aplicativo salvo + dois ou mais enderecos; o ultimo e o destino")
            appendLine("Alertas de proximidade: @@D@@{settings.proximityAlertsEnabled}")
            appendLine()

            appendLine("--- APLICATIVOS E MODELOS DE APOIO ---")
            appendLine("Aplicativos salvos: @@D@@{selectedPackages.size}")
            if (selectedPackages.isEmpty()) appendLine("- nenhum")
            selectedPackages.forEach { appendLine("- @@D@@it") }
            appendLine("Modelos visuais de apoio: @@D@@{cardTemplates.size}")
            cardTemplates.forEach { template ->
                val packageLabel = template.packageName ?: "nao informado"
                appendLine("- @@D@@{template.name} | pacote=@@D@@packageLabel | recursos=@@D@@{template.requiredFeatures.size}")
            }
            appendLine()

            appendLine("--- ESTADO MAIS RECENTE DA BOLINHA ---")
            val updatedText = if (stateUpdatedAt > 0L) formatDate(stateUpdatedAt) else "nao registrado"
            val ageText = if (stateUpdatedAt > 0L) "@@D@@{(now - stateUpdatedAt).coerceAtLeast(0L)} ms" else "nao registrada"
            appendLine("Atualizado em: @@D@@updatedText")
            appendLine("Idade do estado: @@D@@ageText")
            appendLine("Etapa: @@D@@{bubble.text(KEY_STATE_STAGE)}")
            appendLine("Cor: @@D@@{bubble.text(KEY_STATE_COLOR)}")
            appendLine("Distancia: @@D@@{bubble.text(KEY_STATE_DISTANCE_KM)}")
            appendLine("Motivo: @@D@@{bubble.text(KEY_STATE_REASON)}")
            appendLine("Janela: @@D@@{bubble.text(KEY_STATE_WINDOW_PACKAGE)}")
            appendLine("Pacote ativo: @@D@@{bubble.text(KEY_STATE_ACTIVE_PACKAGE)}")
            appendLine("Pacote do texto: @@D@@{bubble.text(KEY_STATE_TEXT_PACKAGE)}")
            appendLine("Servico pronto: @@D@@{bubble.getBoolean(KEY_STATE_SERVICE_READY, false)}")
            appendLine("Analise em andamento: @@D@@{bubble.getBoolean(KEY_STATE_ANALYZING, false)}")
            appendLine("Hash da tela: @@D@@{bubble.text(KEY_STATE_LAST_SNAPSHOT_HASH)}")
            appendLine("Hash analisado: @@D@@{bubble.text(KEY_STATE_LAST_ANALYZED_HASH)}")
            appendLine("Hash pendente: @@D@@{bubble.text(KEY_STATE_PENDING_HASH)}")
            appendLine("Texto da acessibilidade: tamanho=@@D@@{bubble.getInt(KEY_STATE_ACCESSIBILITY_TEXT_LENGTH, 0)} hash=@@D@@{bubble.text(KEY_STATE_ACCESSIBILITY_TEXT_HASH)}")
            appendLine("Texto do OCR: tamanho=@@D@@{bubble.getInt(KEY_STATE_OCR_TEXT_LENGTH, 0)} hash=@@D@@{bubble.text(KEY_STATE_OCR_TEXT_HASH)}")
            appendLine("Tempo da ultima decisao: @@D@@elapsedText")
            appendLine("Caminho da ultima decisao: @@D@@decisionPath")
            appendLine("Ultimo destino calculado: @@D@@lastDestination")
            appendLine()

            appendLine("--- REGIAO DE TRABALHO ---")
            appendLine("Casa ativa: @@D@@{settings.homeTargetEnabled}")
            appendLine("Casa: @@D@@{settings.homeAddress.ifBlank { \"nao informada\" }}")
            appendLine("Raio Casa: @@D@@{settings.homeRadiusKm} km")
            appendLine("Grupo de alfinetes ativo: @@D@@{settings.alternativeTargetEnabled}")
            appendLine("Raio compartilhado dos alfinetes: @@D@@{settings.alternativeRadiusKm} km")
            appendLine("Alfinetes cadastrados: @@D@@{workPins.size}")
            if (workPins.isEmpty()) appendLine("- nenhum")
            workPins.forEach { pin ->
                val coordinate = pin.coordinate?.let { "@@D@@{it.latitude},@@D@@{it.longitude}" } ?: "nao validada"
                val status = if (pin.enabled) "ON" else "OFF"
                appendLine("- @@D@@status | @@D@@{pin.address} | @@D@@coordinate")
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
""".replace("@@D@@", "$")
    file.writeText(source)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        writeStableSimpleFarolReport13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        writeStableSimpleFarolReport13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").asFile,
        )
    }
}
