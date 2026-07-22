val patchBubbleStateReport by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"
        val service = serviceFile.asFile
        if (service.exists()) {
            var text = service.readText()
            val original = text

            text = text.replace(
                """        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
""",
                """        if (packageName == this.packageName) {
            rememberBubbleReason("self_app", "Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.")
            resetToIdle("Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.", record = false)
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
""",
            )

            text = text.replace(
                """        val packageName = resolveRidePackageForText(currentWindowPackageName(), text, allowPopupCandidate = true)
            ?: return false
""",
                """        if (normalizePackageName(currentWindowPackageName()) == this.packageName) return false
        if (looksLikeOwnAppUiText(text)) return false
        val packageName = resolveRidePackageForText(currentWindowPackageName(), text, allowPopupCandidate = true)
            ?: return false
""",
            )

            if ("private fun looksLikeOwnAppUiText(text: String): Boolean" !in text) {
                text = text.replace(
                    """    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
""",
                    """    private fun looksLikeOwnAppUiText(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf(
            "rota certa",
            "ferramentas",
            "relatorio manual",
            "gerar relatorio",
            "modelos de cards",
            "card models",
            "configuracoes principais",
            "backup interno",
            "leitura ao vivo",
            "pacotes monitorados",
            "card de corrida salvo",
        ).any { marker -> marker in normalized }
    }

    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
""",
                )
            }

            if ("private var lastBubbleStateReason" !in text) {
                text = text.replace(
                    """    private var currentRadarColor = RadarColor.Idle
    private var currentDistanceKm: Double? = null
""",
                    """    private var currentRadarColor = RadarColor.Idle
    private var currentDistanceKm: Double? = null
    private var lastBubbleStateStage: String = "created"
    private var lastBubbleStateReason: String = "Servico criado; aguardando conexao da acessibilidade."
""",
                )
            }

            if ("private fun rememberBubbleReason(stage: String, reason: String)" !in text) {
                text = text.replace(
                    """    private fun resetToDefault(
""",
                    """    private fun rememberBubbleReason(stage: String, reason: String) {
        lastBubbleStateStage = stage
        lastBubbleStateReason = reason
    }

    private fun persistBubbleState() {
        val now = System.currentTimeMillis()
        bubblePrefs.edit()
            .putLong(KEY_STATE_UPDATED_AT, now)
            .putString(KEY_STATE_STAGE, lastBubbleStateStage)
            .putString(KEY_STATE_REASON, lastBubbleStateReason)
            .putString(KEY_STATE_COLOR, currentRadarColor.diagnosticLabel)
            .putString(KEY_STATE_DISTANCE_KM, currentDistanceKm?.let(::formatDiagnosticKm).orEmpty())
            .putString(KEY_STATE_WINDOW_PACKAGE, currentWindowPackageName().orEmpty())
            .putString(KEY_STATE_ACTIVE_PACKAGE, activePackageName.orEmpty())
            .putString(KEY_STATE_TEXT_PACKAGE, lastTextPackageName.orEmpty())
            .putString(KEY_STATE_LAST_SNAPSHOT_HASH, lastSnapshotHash?.toString().orEmpty())
            .putString(KEY_STATE_LAST_ANALYZED_HASH, lastAnalyzedHash?.toString().orEmpty())
            .putString(KEY_STATE_PENDING_HASH, pendingAnalysis?.snapshotHash?.toString().orEmpty())
            .putBoolean(KEY_STATE_SERVICE_READY, serviceReady)
            .putBoolean(KEY_STATE_ANALYZING, analyzing)
            .putInt(KEY_STATE_ACCESSIBILITY_TEXT_LENGTH, lastAccessibilityText.length)
            .putString(KEY_STATE_ACCESSIBILITY_TEXT_HASH, lastAccessibilityText.takeIf { it.isNotBlank() }?.snapshotHash()?.toString().orEmpty())
            .putInt(KEY_STATE_OCR_TEXT_LENGTH, lastOcrText.length)
            .putString(KEY_STATE_OCR_TEXT_HASH, lastOcrText.takeIf { it.isNotBlank() }?.snapshotHash()?.toString().orEmpty())
            .putInt(KEY_STATE_TEMPLATE_COUNT, currentCardTemplates.size)
            .apply()
    }

    private fun resetToDefault(
""",
                )
            }

            text = text.replace(
                """        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
""",
                """        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            rememberBubbleReason("screen_changed", "Tela mudou; aguardando confirmar card cadastrado sem manter km antigo.")
            showOverlay(RadarColor.Default)
""",
            )

            text = text.replace(
                """            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
""",
                """            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            rememberBubbleReason("analysis_result", result.reason)
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
""",
            )

            text = text.replace(
                """            showOverlay(RadarColor.Default)
            recordDiagnostic(
""",
                """            rememberBubbleReason("analysis_error", "Erro durante analise do destino final; bolinha voltou para amarelo.")
            showOverlay(RadarColor.Default)
            recordDiagnostic(
""",
            )

            text = text.replace(
                """        showOverlay(RadarColor.Default)
        if (record) {
""",
                """        rememberBubbleReason("default", reason)
        showOverlay(RadarColor.Default)
        if (record) {
""",
            )
            text = text.replace(
                """        showOverlay(RadarColor.Idle)
        if (record) {
""",
                """        rememberBubbleReason("idle", reason)
        showOverlay(RadarColor.Idle)
        if (record) {
""",
            )

            text = text.replace(
                """        currentRadarColor = color
        currentDistanceKm = distanceKm
""",
                """        currentRadarColor = color
        currentDistanceKm = distanceKm
        persistBubbleState()
""",
            )

            if ("const val KEY_STATE_UPDATED_AT" !in text) {
                text = text.replace(
                    """        const val KEY_BUBBLE_Y = "bubble_y"
""",
                    """        const val KEY_BUBBLE_Y = "bubble_y"
        const val KEY_STATE_UPDATED_AT = "state_updated_at"
        const val KEY_STATE_STAGE = "state_stage"
        const val KEY_STATE_REASON = "state_reason"
        const val KEY_STATE_COLOR = "state_color"
        const val KEY_STATE_DISTANCE_KM = "state_distance_km"
        const val KEY_STATE_WINDOW_PACKAGE = "state_window_package"
        const val KEY_STATE_ACTIVE_PACKAGE = "state_active_package"
        const val KEY_STATE_TEXT_PACKAGE = "state_text_package"
        const val KEY_STATE_LAST_SNAPSHOT_HASH = "state_last_snapshot_hash"
        const val KEY_STATE_LAST_ANALYZED_HASH = "state_last_analyzed_hash"
        const val KEY_STATE_PENDING_HASH = "state_pending_hash"
        const val KEY_STATE_SERVICE_READY = "state_service_ready"
        const val KEY_STATE_ANALYZING = "state_analyzing"
        const val KEY_STATE_ACCESSIBILITY_TEXT_LENGTH = "state_accessibility_text_length"
        const val KEY_STATE_ACCESSIBILITY_TEXT_HASH = "state_accessibility_text_hash"
        const val KEY_STATE_OCR_TEXT_LENGTH = "state_ocr_text_length"
        const val KEY_STATE_OCR_TEXT_HASH = "state_ocr_text_hash"
        const val KEY_STATE_TEMPLATE_COUNT = "state_template_count"
""",
                )
            }

            if (text != original) service.writeText(text)
        }

        val main = mainFile.asFile
        if (main.exists()) {
            var text = main.readText()
            val original = text

            if ("val bubbleStatePrefs = context.getSharedPreferences(\"rota_certa_bubble\"" !in text) {
                text = text.replace(
                    """    return buildString {
""",
                    """    val bubbleStatePrefs = context.getSharedPreferences("rota_certa_bubble", Context.MODE_PRIVATE)
    val bubbleUpdatedAtMillis = bubbleStatePrefs.getLong("state_updated_at", 0L)
    fun bubbleText(key: String): String = bubbleStatePrefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao informado"
    fun bubbleBool(key: String): String = bubbleStatePrefs.getBoolean(key, false).toString()
    fun bubbleInt(key: String): String = bubbleStatePrefs.getInt(key, -1).takeIf { it >= 0 }?.toString() ?: "nao informado"

    return buildString {
""",
                )
            }

            if ("--- ESTADO INSTANTANEO DA BOLINHA ---" !in text) {
                text = text.replace(
                    """        appendLine("Leitura ao vivo ativa: ${'$'}liveEnabled")
        appendLine()
""",
                    """        appendLine("Leitura ao vivo ativa: ${'$'}liveEnabled")
        appendLine()
        appendLine("--- ESTADO INSTANTANEO DA BOLINHA ---")
        appendLine("Atualizado: ${'$'}{if (bubbleUpdatedAtMillis > 0L) formatDate(bubbleUpdatedAtMillis) else "nunca"}")
        appendLine("Idade estado: ${'$'}{if (bubbleUpdatedAtMillis > 0L) (System.currentTimeMillis() - bubbleUpdatedAtMillis).toString() + " ms" else "nao informado"}")
        appendLine("Etapa: ${'$'}{bubbleText("state_stage")}")
        appendLine("Cor: ${'$'}{bubbleText("state_color")}")
        appendLine("Km exibido: ${'$'}{bubbleText("state_distance_km")}")
        appendLine("Motivo: ${'$'}{bubbleText("state_reason")}")
        appendLine("Pacote janela: ${'$'}{bubbleText("state_window_package")}")
        appendLine("Pacote ativo: ${'$'}{bubbleText("state_active_package")}")
        appendLine("Pacote texto: ${'$'}{bubbleText("state_text_package")}")
        appendLine("Hash tela atual: ${'$'}{bubbleText("state_last_snapshot_hash")}")
        appendLine("Hash analisado: ${'$'}{bubbleText("state_last_analyzed_hash")}")
        appendLine("Hash pendente: ${'$'}{bubbleText("state_pending_hash")}")
        appendLine("Servico pronto: ${'$'}{bubbleBool("state_service_ready")}")
        appendLine("Analisando agora: ${'$'}{bubbleBool("state_analyzing")}")
        appendLine("Texto acessibilidade tamanho: ${'$'}{bubbleInt("state_accessibility_text_length")}")
        appendLine("Texto acessibilidade hash: ${'$'}{bubbleText("state_accessibility_text_hash")}")
        appendLine("Texto OCR tamanho: ${'$'}{bubbleInt("state_ocr_text_length")}")
        appendLine("Texto OCR hash: ${'$'}{bubbleText("state_ocr_text_hash")}")
        appendLine("Modelos carregados na bolinha: ${'$'}{bubbleInt("state_template_count")}")
        appendLine()
""",
                )
            }

            if (text != original) main.writeText(text)
        }
    }
}

patchBubbleStateReport.configure {
    mustRunAfter("patchVideoBubbleHardening")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchBubbleStateReport)
}
