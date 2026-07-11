// Liga o CoreFreshnessGuard antes da aplicacao final de cor/km.
// Resultado atrasado de rota/decisao nao pode pintar a bolinha se pacote/hash/card mudaram.

val coreFreshnessGuardPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("core_freshness_guard_0_1_97" !in text) {
            val visualTraceStart = text.indexOf("            traceEvent(\"core.pipeline.visual")
            if (visualTraceStart < 0) {
                throw org.gradle.api.GradleException("Nao encontrei visual do pipeline para instalar guardiao de frescor.")
            }
            val freshnessBlock = """            val coreFreshnessDecision = br.com.mapeiaia.rotacerta.core.CoreFreshnessGuard.evaluate(
                transaction = coreLivePipeline.currentTransaction(),
                currentPackageName = packageName,
                currentSnapshotHash = snapshotHash,
                currentVisibleCardSignature = lastVisibleCardSignature,
            )
            if (!coreFreshnessDecision.fresh) {
                traceEvent("core.freshness stale reason=${dollar}{coreFreshnessDecision.reason}") // core_freshness_guard_0_1_97
                recordDiagnostic(
                    stage = "stale_result",
                    reason = coreFreshnessDecision.reason,
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }
            traceEvent("core.freshness fresh reason=${dollar}{coreFreshnessDecision.reason}") // core_freshness_guard_0_1_97
"""
            text = text.substring(0, visualTraceStart) + freshnessBlock + text.substring(visualTraceStart)
        }

        if ("core_freshness_guard_0_1_97" !in text) {
            throw org.gradle.api.GradleException("CoreFreshnessGuard nao foi instalado antes do visual.")
        }

        if (text != original) file.writeText(text)
    }
}

coreFreshnessGuardPatch.configure {
    mustRunAfter("coreLiveAnalysisPipelinePatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreFreshnessGuardPatch)
}
