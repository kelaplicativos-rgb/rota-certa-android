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
            val startToken = "        val snapshotText ="
            val endToken = "        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->\n"
            val start = text.indexOf(startToken)
            val end = if (start >= 0) text.indexOf(endToken, start) else -1
            if (start < 0 || end < 0) {
                throw org.gradle.api.GradleException("Nao encontrei a regiao de snapshot/merge de leitura para mover ao Core.")
            }
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
            text = text.substring(0, start) + newBlock + text.substring(end)
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
