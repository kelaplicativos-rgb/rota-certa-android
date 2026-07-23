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

        if ("private val coreLiveReadTriggerGate = br.com.mapeiaia.rotacerta.core.CoreLiveReadTriggerGate" !in text) {
            val fieldTarget = "    private val registeredCardGate = RegisteredCardDecisionGate()\n"
            if (fieldTarget !in text) throw GradleException("Nao encontrei RegisteredCardDecisionGate para ligar o agrupador de eventos.")
            text = text.replaceFirst(
                fieldTarget,
                fieldTarget + "    private val coreLiveReadTriggerGate = br.com.mapeiaia.rotacerta.core.CoreLiveReadTriggerGate(duplicateWindowMs = 180L)\n",
            )
        }

        if ("gigu_inspired_event_gate_0_1_89" !in text) {
            val eventStart = text.indexOf("    override fun onAccessibilityEvent(event: AccessibilityEvent?) {")
            val eventEnd = if (eventStart >= 0) text.indexOf("    override fun onInterrupt()", eventStart) else -1
            if (eventStart < 0 || eventEnd < 0) {
                throw GradleException("Nao encontrei os limites de onAccessibilityEvent.")
            }

            val insertionCandidates = listOf(
                "        if (packageName == this.packageName) {",
                "        if (packageName == null) {",
                "        if (!shouldScanPackage(packageName)) {",
            ).map { token -> text.indexOf(token, eventStart) }
                .filter { index -> index in (eventStart + 1) until eventEnd }

            val insertionPoint = insertionCandidates.minOrNull()
                ?: throw GradleException("Nao encontrei ponto seguro apos a resolucao do pacote no evento.")

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
        // Eventos aceitos apenas acordam a fila unica ja existente; o ciclo continuo executa as releituras. // gigu_inspired_event_wakeup_0_1_89
"""
            text = text.substring(0, insertionPoint) + gateBlock + text.substring(insertionPoint)
        }

        text = Regex("const val SCAN_LOOP_MS = \\d+L").replace(text, "const val SCAN_LOOP_MS = 180L")
        text = Regex("const val SCREENSHOT_INTERVAL_MS = \\d+L").replace(text, "const val SCREENSHOT_INTERVAL_MS = 320L")

        if ("coreLiveReadTriggerGate.reset() // gigu_inspired_gate_reset_0_1_89" !in text) {
            val destroyStart = text.indexOf("    override fun onDestroy() {")
            val destroyEnd = if (destroyStart >= 0) text.indexOf("    private fun startContinuousScan()", destroyStart) else -1
            val resetPoint = if (destroyStart >= 0 && destroyEnd > destroyStart) {
                text.indexOf("        screenshotInProgress.set(false)\n", destroyStart)
                    .takeIf { it in destroyStart until destroyEnd }
            } else null
            if (resetPoint == null) throw GradleException("Nao encontrei a limpeza de screenshot dentro de onDestroy.")
            val target = "        screenshotInProgress.set(false)\n"
            text = text.substring(0, resetPoint) +
                target + "        coreLiveReadTriggerGate.reset() // gigu_inspired_gate_reset_0_1_89\n" +
                text.substring(resetPoint + target.length)
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
