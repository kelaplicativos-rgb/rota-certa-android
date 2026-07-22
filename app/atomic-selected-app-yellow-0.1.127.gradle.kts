// Rota Certa 0.1.127
// Limpeza atomica do card:
// - dentro do aplicativo selecionado, limpa dados e pinta amarelo uma unica vez;
// - fora do aplicativo selecionado, limpa dados e pinta cinza uma unica vez;
// - elimina o quadro intermediario cinza -> amarelo que causava piscada.

fun replaceAtomicKotlinFunction127(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao nao encontrada para limpeza atomica 0.1.127: $signature")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo da funcao nao encontrado para limpeza atomica 0.1.127: $signature")
    var depth = 0
    var index = braceStart
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(0, start) + replacement + source.substring(index + 1)
                }
            }
        }
        index += 1
    }
    throw GradleException("Fim da funcao nao encontrado para limpeza atomica 0.1.127: $signature")
}

fun patchAtomicSelectedAppYellow127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para limpeza atomica 0.1.127.")

    var service = serviceFile.readText()

    if ("atomic_selected_app_clear_color_0_1_127" !in service) {
        val oldInactiveBlock = """            hardClearUniversalTwoAddress(clearReason)
            if (shouldScanCurrentWindow()) {
                rememberBubbleReason(
                    "manual_waiting",
                    "Aplicativo selecionado ativo; aguardando um card cadastrado correspondente.",
                )
                showOverlay(RadarColor.Default, distanceKm = null)
            } // selected_app_clear_to_yellow_0_1_127
            return // global_inactive_clear_now_0_1_124
"""
        if (oldInactiveBlock !in service) {
            throw GradleException("Transicao cinza-amarelo nao encontrada para conversao atomica.")
        }
        val atomicInactiveBlock = """            val keepWaitingYellow127 = shouldScanCurrentWindow()
            hardClearUniversalTwoAddress(
                reason = clearReason,
                keepWaitingYellow = keepWaitingYellow127,
            ) // selected_app_clear_to_yellow_0_1_127 atomic_selected_app_clear_color_0_1_127
            return // global_inactive_clear_now_0_1_124
"""
        service = service.replaceFirst(oldInactiveBlock, atomicInactiveBlock)
    }

    if ("atomic_hard_clear_single_paint_0_1_127" !in service) {
        val replacement = """    private fun hardClearUniversalTwoAddress(
        reason: String,
        keepWaitingYellow: Boolean = false,
    ) {
        val targetColor127 = if (keepWaitingYellow) RadarColor.Default else RadarColor.Idle
        val targetStage127 = if (keepWaitingYellow) "manual_waiting" else "universal_idle"
        val targetReason127 = if (keepWaitingYellow) {
            "Aplicativo selecionado ativo; aguardando um card cadastrado correspondente."
        } else {
            reason
        }
        val hadData = currentRadarColor != RadarColor.Idle ||
            currentDistanceKm != null ||
            lastSnapshotHash != null ||
            universalActiveAddressSignature != null
        if (
            !hadData &&
            currentRadarColor == targetColor127 &&
            lastBubbleStateStage == targetStage127 &&
            lastBubbleStateReason == targetReason127
        ) return
        val stateChanged = hadData ||
            currentRadarColor != targetColor127 ||
            lastBubbleStateStage != targetStage127 ||
            lastBubbleStateReason != targetReason127
        LiveFailureTraceStore.recordStep(
            stage = "session.clear",
            details = "reason=$reason; target=${targetColor127.diagnosticLabel}; had_data=$hadData; generation_before=$universalScreenGeneration; color=${currentRadarColor.diagnosticLabel}; km=${currentDistanceKm?.toString() ?: "none"}",
            packageName = currentWindowPackageName(),
            generation = universalScreenGeneration,
            screenHash = lastSnapshotHash,
        ) // session_diagnostic_clear_v2
        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        universalActiveAddressSignature = null
        manualActiveCardTemplateId127 = null // manual_active_card_clear_0_1_127
        lastSnapshotHash = null
        lastAnalyzedHash = null
        pendingAnalysis = null
        analyzing = false
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        universalAccessibilityOwnsCard = false
        universalLastActiveReadAtMillis = 0L
        universalActiveRidePackageName = null
        universalLiveReadGate.reset()
        registeredCardGate.clear()
        if (stateChanged) {
            clearRuntimeValidationTrigger()
            rememberBubbleReason(targetStage127, targetReason127)
            showOverlay(targetColor127, distanceKm = null) // atomic_hard_clear_single_paint_0_1_127
            currentRadarColor = targetColor127
            currentDistanceKm = null
            if (BuildConfig.DEBUG) {
                bubblePrefs.edit()
                    .putString(
                        "runtime_validation_state",
                        (if (keepWaitingYellow) "amarelo" else "cinza") + "|",
                    )
                    .putLong("runtime_validation_state_at", System.currentTimeMillis())
                    .apply()
            }
        }
        if (hadData) traceEvent(
            "universal.clear immediate=true target=${targetColor127.diagnosticLabel} reason=$reason",
        )
    } // universal_stable_clear_0_1_101
"""
        service = replaceAtomicKotlinFunction127(
            service,
            "    private fun hardClearUniversalTwoAddress(reason: String)",
            replacement,
        )
    }

    val processStart = service.indexOf("    private suspend fun processRideText(")
    val processEnd = if (processStart >= 0) service.indexOf("    private suspend fun analyzeUniversalTwoAddress(", processStart) else -1
    if (processStart < 0 || processEnd < 0) throw GradleException("Fluxo de leitura nao encontrado para validar limpeza atomica.")
    val processRegion = service.substring(processStart, processEnd)
    val inactiveStart = processRegion.indexOf("        if (!activeTrigger) {")
    val inactiveEnd = if (inactiveStart >= 0) processRegion.indexOf("        universalLastActiveReadAtMillis", inactiveStart) else -1
    if (inactiveStart < 0 || inactiveEnd < 0) throw GradleException("Regiao de card inativo nao encontrada para validar limpeza atomica.")
    val inactiveRegion = processRegion.substring(inactiveStart, inactiveEnd)

    listOf(
        "atomic_selected_app_clear_color_0_1_127",
        "keepWaitingYellow = keepWaitingYellow127",
        "atomic_hard_clear_single_paint_0_1_127",
        "manual_selected_apps_gate_0_1_127",
        "manual_registered_card_gate_0_1_127",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Marcador ausente na limpeza atomica 0.1.127: $marker")
    }
    if ("showOverlay(RadarColor.Idle" in inactiveRegion || "showOverlay(RadarColor.Default" in inactiveRegion) {
        throw GradleException("Card inativo ainda pinta uma cor intermediaria fora da limpeza atomica.")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchAtomicSelectedAppYellow127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
