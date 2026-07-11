// Move o match/assinatura de card cadastrado para o CoreCardMatchEngine.
// O servico so continua para rota/cor quando o Core aprova card individual cadastrado.

val coreCardMatchEnginePatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        if ("core_card_match_engine_0_1_94" !in text) {
            val startToken = "        val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)\n"
            val endToken = "        registeredCardGate.markSeen()\n"
            val start = text.indexOf(startToken)
            val end = if (start >= 0) text.indexOf(endToken, start) else -1
            if (start < 0 || end < 0) {
                throw org.gradle.api.GradleException("Nao encontrei o bloco de match de card para mover ao Core.")
            }
            val replacement = """        val coreCardMatch = br.com.mapeiaia.rotacerta.core.CoreCardMatchEngine.match(
            text = snapshotText,
            packageName = packageName,
            templates = currentCardTemplates,
        )
        val cardMatch = coreCardMatch.match
        if (!coreCardMatch.accepted || cardMatch == null) {
            val reason = coreCardMatch.reason
            traceEvent("core.card_match reject list=${'$'}{coreCardMatch.isListLike} package=${'$'}{packageName.orEmpty()} templates=${'$'}{currentCardTemplates.size} reason=${'$'}reason") // core_card_match_engine_0_1_94
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
            resetToDefault(reason = reason, text = snapshotText, fields = fields)
            return
        }
        traceEvent("core.card_match accept name=${'$'}{cardMatch.template.name} score=${'$'}{cardMatch.score} reason=${'$'}{coreCardMatch.reason}") // core_card_match_engine_0_1_94
"""
            text = text.substring(0, start) + replacement + text.substring(end)
        }

        if ("core_popup_card_match_0_1_94" !in text) {
            text = text.replace(
                "return RideCardTemplateMatcher.match(text, packageName, currentCardTemplates) != null",
                "return br.com.mapeiaia.rotacerta.core.CoreCardMatchEngine.match(text, packageName, currentCardTemplates).accepted // core_popup_card_match_0_1_94",
            )
        }

        if ("core_card_match_engine_0_1_94" !in text) {
            throw org.gradle.api.GradleException("CoreCardMatchEngine nao assumiu match principal.")
        }
        if ("core_popup_card_match_0_1_94" !in text) {
            throw org.gradle.api.GradleException("CoreCardMatchEngine nao assumiu match de popup.")
        }

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreCardMatchEnginePatch)
}
