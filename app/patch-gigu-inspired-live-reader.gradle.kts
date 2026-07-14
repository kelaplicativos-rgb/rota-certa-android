// Arquitetura propria inspirada em praticas observadas no GigU:
// - eventos de acessibilidade apenas acordam o leitor;
// - rajadas repetidas sao agrupadas;
// - troca de janela continua imediata;
// - OCR fica no ciclo proprio e em fila unica;
// - nenhuma regra ou codigo proprietario e copiado.

val giguInspiredLiveReaderPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")

        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private val coreLiveReadTriggerGate = br.com.mapeiaia.rotacerta.core.CoreLiveReadTriggerGate()" !in text) {
            val fieldTarget = "    private val registeredCardGate = RegisteredCardDecisionGate()\n"
            if (fieldTarget !in text) throw GradleException("Nao encontrei RegisteredCardDecisionGate para ligar o agrupador de eventos.")
            text = text.replaceFirst(
                fieldTarget,
                fieldTarget + "    private val coreLiveReadTriggerGate = br.com.mapeiaia.rotacerta.core.CoreLiveReadTriggerGate()\n",
            )
        }

        if ("gigu_inspired_event_gate_0_1_89" !in text) {
            val eventTrace = "        traceEvent(\"event package=${dollar}{packageName.orEmpty()} type=${dollar}{event.eventType}\")\n"
            if (eventTrace !in text) throw GradleException("Nao encontrei o ponto de entrada dos eventos de acessibilidade.")
            val gateBlock = """        val rootPackageNameForReadGate = currentRootPackageName()
        val liveReadTriggerDecision = coreLiveReadTriggerGate.decide(
            eventPackageName = packageName,
            rootPackageName = rootPackageNameForReadGate,
            eventType = event.eventType,
            eventPackageIsMonitored = shouldScanPackage(packageName),
            rootPackageIsMonitored = shouldScanPackage(rootPackageNameForReadGate),
            nowMillis = android.os.SystemClock.elapsedRealtime(),
        )
        if (liveReadTriggerDecision.action == br.com.mapeiaia.rotacerta.core.CoreLiveReadTriggerAction.IgnoreDuplicate) {
            traceEvent("event grouped package=${dollar}{packageName.orEmpty()} type=${dollar}{event.eventType} reason=${dollar}{liveReadTriggerDecision.reason}") // gigu_inspired_event_gate_0_1_89
            return
        }
"""
            text = text.replaceFirst(eventTrace, gateBlock + eventTrace)
        }

        if ("gigu_inspired_event_wakeup_0_1_89" !in text) {
            val monitoredReadRegex = Regex(
                """        if \(currentRadarColor == RadarColor\.Idle\) showOverlay\(RadarColor\.Default\)
        scheduleVisibleTextAnalysis\(delayMs = \d+L\)
        requestScreenshotAnalysis\(\)
""",
            )
            val replacement = """        val immediateWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
        scheduleVisibleTextAnalysis(delayMs = if (immediateWindowEvent) 0L else 35L) // gigu_inspired_event_wakeup_0_1_89
        if (immediateWindowEvent) requestScreenshotAnalysis()
"""
            val updated = monitoredReadRegex.replaceFirst(text, replacement)
            if (updated == text) throw GradleException("Nao encontrei o disparo final de leitura do evento monitorado.")
            text = updated
        }

        text = Regex("const val SCAN_LOOP_MS = \\d+L").replace(text, "const val SCAN_LOOP_MS = 180L")
        text = Regex("const val SCREENSHOT_INTERVAL_MS = \\d+L").replace(text, "const val SCREENSHOT_INTERVAL_MS = 320L")

        if ("coreLiveReadTriggerGate.reset() // gigu_inspired_gate_reset_0_1_89" !in text) {
            val destroyTarget = "        screenshotInProgress.set(false)\n"
            if (destroyTarget !in text) throw GradleException("Nao encontrei a limpeza do servico para resetar o agrupador.")
            text = text.replaceFirst(
                destroyTarget,
                destroyTarget + "        coreLiveReadTriggerGate.reset() // gigu_inspired_gate_reset_0_1_89\n",
            )
        }

        listOf(
            "gigu_inspired_event_gate_0_1_89",
            "gigu_inspired_event_wakeup_0_1_89",
            "gigu_inspired_gate_reset_0_1_89",
            "const val SCAN_LOOP_MS = 180L",
            "const val SCREENSHOT_INTERVAL_MS = 320L",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Leitor inspirado no GigU incompleto: $marker")
        }

        if (text != original) file.writeText(text)
    }
}

giguInspiredLiveReaderPatch.configure {
    mustRunAfter(
        "liveRideWindowEventGuard",
        "passiveEventCompileFix",
        "coreScreenReadEngineInlinePatch",
        "coreCardMatchEnginePatch",
        "coreLiveAnalysisPipelinePatch",
        "coreVisibleCardLifecyclePatch",
        "reportStaleLifecycleFix",
        "reportStaleLifecycleCompileFix",
        "patchScreenPhoneWhatsApp",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(giguInspiredLiveReaderPatch)
}
