// Liga a bolinha ao Rota Certa Core.
// O Core classifica a tela antes de liberar match/rota:
// - listagem/feed: nao calcula;
// - leitura parcial: nao derruba decisao boa no mesmo card;
// - card individual aberto: segue para assinatura e rota.

val rotaCertaCoreGate by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("rota_certa_core_gate_0_1_86" !in text) {
            val anchor = """        if (!RideOfferDetector.looksLikeRideOffer(snapshotText, fields, packageName)) {
"""
            val gate = """        val coreCardDecision = br.com.mapeiaia.rotacerta.core.RotaCertaCore.matchRegisteredOpenCard(
            packageName = packageName,
            text = snapshotText,
            fields = fields,
            templates = currentCardTemplates,
        )
        traceEvent("core.screen kind=${dollar}{coreCardDecision.classification.kind} canAnalyze=${dollar}{coreCardDecision.canAnalyzeRoute} reason=${dollar}{coreCardDecision.reason}") // rota_certa_core_gate_0_1_86
        when (coreCardDecision.classification.kind) {
            br.com.mapeiaia.rotacerta.core.RideScreenKind.RideListing -> {
                registeredCardGate.clear()
                saveCapturedReadToHistory(snapshotText, fields, snapshotHash, coreCardDecision.reason)
                resetToDefault(reason = coreCardDecision.reason, text = snapshotText, fields = fields)
                return
            }
            br.com.mapeiaia.rotacerta.core.RideScreenKind.PartialRideCard,
            br.com.mapeiaia.rotacerta.core.RideScreenKind.UnknownRideScreen -> {
                if (hasActiveRegisteredDecision() && shouldScanCurrentWindow()) {
                    traceEvent("core.partial keep_active_decision kind=${dollar}{coreCardDecision.classification.kind}")
                    recordDiagnostic(
                        stage = "core_partial_keep_decision",
                        reason = "Leitura parcial dentro do app monitorado; mantive a decisao atual ate confirmar novo card real.",
                        text = snapshotText,
                        fields = fields,
                    )
                    return
                }
                if (packageName == RideCardTemplateMatcher.INDRIVE_PACKAGE) {
                    saveCapturedReadToHistory(snapshotText, fields, snapshotHash, coreCardDecision.reason)
                    resetToDefault(reason = coreCardDecision.reason, text = snapshotText, fields = fields)
                    return
                }
            }
            else -> Unit
        }
"""
            if (anchor !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o ponto de entrada para ligar o Rota Certa Core.")
            }
            text = text.replace(anchor, gate + anchor)
        }

        if ("rota_certa_core_gate_0_1_86" !in text) {
            throw org.gradle.api.GradleException("Rota Certa Core Gate nao foi instalado.")
        }

        if (text != original) file.writeText(text)
    }
}

rotaCertaCoreGate.configure {
    mustRunAfter(
        "modularLiveBubbleCore",
        "noStickyDecisionCleanup",
        "patchBubbleRenderStability",
        "liveResultFreshnessGuard",
        "inDriveCardContractMatch"
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(rotaCertaCoreGate)
}
