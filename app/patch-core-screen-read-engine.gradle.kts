// Liga processRideText ao CoreScreenReadEngine.
// Acessibilidade/OCR continuam sendo fontes Android, mas merge, limpeza, hash e estado vazio ficam no Core.

val coreScreenReadEnginePatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("core_screen_read_engine_0_1_92" !in text) {
            val oldBlock = """        val snapshotText = if (allowPopupCandidate) {
            text.trim()
        } else {
            mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank { text.trim() }
        }
        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=${dollar}source")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = !isPassiveDiagnosticPackage(activePackageName))
            return
        }

        val snapshotHash = snapshotText.snapshotHash()
        traceEvent("process.snapshot length=${dollar}{snapshotText.length} hash=${dollar}snapshotHash")
"""
            val newBlock = """        val coreReadSnapshot = br.com.mapeiaia.rotacerta.core.CoreScreenReadEngine.prepare(
            accessibilityText = lastAccessibilityText,
            ocrText = lastOcrText,
            fallbackText = text,
            allowPopupCandidate = allowPopupCandidate,
        )
        val snapshotText = coreReadSnapshot.text
        if (coreReadSnapshot.kind == br.com.mapeiaia.rotacerta.core.CoreScreenReadKind.Empty) {
            traceEvent("core.read.empty source=${dollar}source summary=${dollar}{coreReadSnapshot.sourceSummary}") // core_screen_read_engine_0_1_92
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = !isPassiveDiagnosticPackage(activePackageName))
            return
        }

        val snapshotHash = coreReadSnapshot.hash
        traceEvent("core.read.snapshot length=${dollar}{snapshotText.length} hash=${dollar}snapshotHash summary=${dollar}{coreReadSnapshot.sourceSummary}") // core_screen_read_engine_0_1_92
"""
            if (oldBlock !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o bloco de snapshot/merge de leitura para mover ao Core.")
            }
            text = text.replace(oldBlock, newBlock)
        }

        if ("core_screen_read_engine_0_1_92" !in text) {
            throw org.gradle.api.GradleException("CoreScreenReadEngine nao assumiu snapshot da leitura.")
        }

        if (text != original) file.writeText(text)
    }
}

coreScreenReadEnginePatch.configure {
    mustRunAfter("coreBubbleStatePatch", "coreBubblePresenterPatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreScreenReadEnginePatch)
}
