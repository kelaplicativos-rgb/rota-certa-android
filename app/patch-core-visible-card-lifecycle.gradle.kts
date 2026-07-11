// Move o ciclo de vida do card visivel para o CoreVisibleCardLifecycle.
// O Core passa a controlar: entrou, mudou, continuou igual, saiu ou foi substituido.

val coreVisibleCardLifecyclePatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    fun insertAfterFunctionStart(text: String, signature: String, block: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return text
        val bodyStart = text.indexOf("    ) {\n", start).takeIf { it >= 0 }?.let { it + "    ) {\n".length }
            ?: text.indexOf("    )\n", start).takeIf { it >= 0 }?.let { text.indexOf("{\n", it) + 2 }
            ?: return text
        return text.substring(0, bodyStart) + block + text.substring(bodyStart)
    }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        // Outros patches antigos tambem criavam esta variavel. Remove todas e reinsere uma unica fonte de verdade.
        text = text.replace("    private var lastVisibleCardSignature: String? = null\n", "")

        if ("private val coreVisibleCardLifecycle = br.com.mapeiaia.rotacerta.core.CoreVisibleCardLifecycle()" !in text) {
            text = text.replace(
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n",
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n    private val coreVisibleCardLifecycle = br.com.mapeiaia.rotacerta.core.CoreVisibleCardLifecycle()\n    private var lastVisibleCardSignature: String? = null\n",
            )
        } else {
            text = text.replace(
                "    private val coreVisibleCardLifecycle = br.com.mapeiaia.rotacerta.core.CoreVisibleCardLifecycle()\n",
                "    private val coreVisibleCardLifecycle = br.com.mapeiaia.rotacerta.core.CoreVisibleCardLifecycle()\n    private var lastVisibleCardSignature: String? = null\n",
            )
        }

        if ("core_visible_card_lifecycle_0_1_95" !in text) {
            val markerAfter = when {
                "traceEvent(\"core.read.snapshot" in text -> {
                    val start = text.indexOf("        traceEvent(\"core.read.snapshot")
                    val lineEnd = text.indexOf("\n", start)
                    if (start >= 0 && lineEnd >= 0) lineEnd + 1 else -1
                }
                "traceEvent(\"process.snapshot" in text -> {
                    val start = text.indexOf("        traceEvent(\"process.snapshot")
                    val lineEnd = text.indexOf("\n", start)
                    if (start >= 0 && lineEnd >= 0) lineEnd + 1 else -1
                }
                else -> -1
            }
            if (markerAfter < 0) {
                throw org.gradle.api.GradleException("Nao encontrei ponto de snapshot para instalar ciclo de vida do card visivel.")
            }
            val lifecycleBlock = """        val coreVisibleCardEvent = coreVisibleCardLifecycle.observe(
            packageName = packageName,
            snapshotHash = snapshotHash,
            text = snapshotText,
        )
        lastVisibleCardSignature = coreVisibleCardEvent.currentSignature
        traceEvent("core.visible_card action=${dollar}{coreVisibleCardEvent.action} signature=${dollar}{coreVisibleCardEvent.currentSignature ?: "null"} reason=${dollar}{coreVisibleCardEvent.reason}") // core_visible_card_lifecycle_0_1_95
        if (coreVisibleCardEvent.shouldClearPreviousDecision) {
            registeredCardGate.clear()
            lastAnalyzedHash = null
            if (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) {
                showOverlay(RadarColor.Default)
            }
        }
"""
            text = text.substring(0, markerAfter) + lifecycleBlock + text.substring(markerAfter)
        }

        if ("core_visible_card_clear_0_1_95" !in text) {
            val resetTarget = """        registeredCardGate.clear()
        clearRememberedRideText()
"""
            val resetReplacement = """        registeredCardGate.clear()
        val visibleCardClearEvent = coreVisibleCardLifecycle.clear(reason)
        lastVisibleCardSignature = null
        traceEvent("core.visible_card clear action=${dollar}{visibleCardClearEvent.action} previous=${dollar}{visibleCardClearEvent.previousSignature ?: "null"} reason=${dollar}{visibleCardClearEvent.reason}") // core_visible_card_clear_0_1_95
        clearRememberedRideText()
"""
            if (resetTarget in text) {
                text = text.replace(resetTarget, resetReplacement)
            } else {
                val clearBlock = """        val visibleCardClearEvent = coreVisibleCardLifecycle.clear(reason)
        lastVisibleCardSignature = null
        traceEvent("core.visible_card clear action=${dollar}{visibleCardClearEvent.action} previous=${dollar}{visibleCardClearEvent.previousSignature ?: "null"} reason=${dollar}{visibleCardClearEvent.reason}") // core_visible_card_clear_0_1_95
"""
                val before = text
                text = insertAfterFunctionStart(text, "    private fun resetToDefault(\n", clearBlock)
                text = insertAfterFunctionStart(text, "    private fun resetToIdle(\n", clearBlock)
                if (text == before) {
                    throw org.gradle.api.GradleException("Nao encontrei reset da bolinha para ligar clear do ciclo de vida.")
                }
            }
        }

        if ("core_visible_card_lifecycle_0_1_95" !in text) {
            throw org.gradle.api.GradleException("CoreVisibleCardLifecycle nao assumiu observacao de snapshot.")
        }
        if ("core_visible_card_clear_0_1_95" !in text) {
            throw org.gradle.api.GradleException("CoreVisibleCardLifecycle nao assumiu limpeza de card visivel.")
        }
        if ("private var lastVisibleCardSignature: String? = null" !in text) {
            throw org.gradle.api.GradleException("Assinatura visivel do card nao foi instalada no servico.")
        }

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreVisibleCardLifecyclePatch)
}
