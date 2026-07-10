// Guarda contra dessincronizacao da bolinha.
// OCR atrasado nao pode mais aplicar km/cor depois que o card ja saiu ou mudou.
// A guarda de aplicacao da rota fica como melhoria oportunista, sem bloquear build.

val liveResultFreshnessGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast

        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("LIVE_RESULT_MAX_AGE_MS" !in text) {
            text = text.replace(
                "const val SCREENSHOT_INTERVAL_MS = 650L",
                "const val SCREENSHOT_INTERVAL_MS = 650L\n        const val OCR_RESULT_MAX_AGE_MS = 1_400L\n        const val LIVE_RESULT_MAX_AGE_MS = 1_800L",
            )
            text = text.replace(
                "const val SCREENSHOT_INTERVAL_MS = 420L",
                "const val SCREENSHOT_INTERVAL_MS = 420L\n        const val OCR_RESULT_MAX_AGE_MS = 1_400L\n        const val LIVE_RESULT_MAX_AGE_MS = 1_800L",
            )
            text = text.replace(
                "const val SCREENSHOT_INTERVAL_MS = 300L",
                "const val SCREENSHOT_INTERVAL_MS = 300L\n        const val OCR_RESULT_MAX_AGE_MS = 1_400L\n        const val LIVE_RESULT_MAX_AGE_MS = 1_800L",
            )
        }

        if ("screenshot_request_bound_to_window_0_1_84" !in text) {
            text = text.replace(
                """        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
""",
                """        val now = System.currentTimeMillis()
        val screenshotRequestPackage = currentWindowPackageName() // screenshot_request_bound_to_window_0_1_84
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
""",
            )
        }

        if ("screenshot.ocr discard_stale" !in text) {
            text = text.replace(
                """                                if (allowPopupCandidate || shouldScanCurrentWindow()) {
                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                    val ocrText = ocrService.extractText(bitmap)
""",
                """                                if (allowPopupCandidate || shouldScanCurrentWindow()) {
                                    val screenshotAgeMillis = System.currentTimeMillis() - now
                                    val currentPackageAfterScreenshot = currentWindowPackageName()
                                    if (!allowPopupCandidate && (screenshotAgeMillis > OCR_RESULT_MAX_AGE_MS || currentPackageAfterScreenshot != screenshotRequestPackage)) {
                                        traceEvent("screenshot.ocr discard_stale age=${dollar}screenshotAgeMillis request=${dollar}{screenshotRequestPackage.orEmpty()} current=${dollar}{currentPackageAfterScreenshot.orEmpty()}") // stale_ocr_result_guard_0_1_84
                                        return@runCatching
                                    }
                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                    val ocrText = ocrService.extractText(bitmap)
""",
            )
        }

        if ("analysisStartedAtMillis" !in text) {
            text = text.replace(
                """        analyzing = true
        traceEvent("analysis.start hash=${dollar}snapshotHash destination=${dollar}{fields.destination.diagnosticValue()}")
""",
                """        analyzing = true
        val analysisStartedAtMillis = System.currentTimeMillis() // live_analysis_freshness_start_0_1_84
        traceEvent("analysis.start hash=${dollar}snapshotHash destination=${dollar}{fields.destination.diagnosticValue()}")
""",
            )
        }

        // Tentativa extra: se o ponto final de aplicacao ainda estiver no formato conhecido,
        // instala a guarda de idade/visibilidade antes de pintar a bolinha.
        if ("analysis.discard stale_apply" !in text) {
            val freshnessGuard = """            if (!allowPopupCandidate) {
                val applyAgeMillis = System.currentTimeMillis() - analysisStartedAtMillis
                val applyVisibleText = collectVisibleText(allowPopupCandidate = false)
                val applyVisiblePackage = currentWindowPackageName()
                if (applyAgeMillis > LIVE_RESULT_MAX_AGE_MS) {
                    lastAnalyzedHash = snapshotHash
                    traceEvent("analysis.discard stale_apply age=${dollar}applyAgeMillis hash=${dollar}snapshotHash") // stale_apply_result_guard_0_1_84
                    recordDiagnostic(
                        stage = "stale_analysis_discarded",
                        reason = "Resultado chegou tarde demais; ignorei para nao mostrar km depois que o card saiu ou mudou.",
                        text = text,
                        fields = fields,
                        result = result,
                        cardTemplateMatch = cardMatch,
                    )
                    return
                }
                if (applyVisibleText.isNotBlank()) {
                    val applyFields = parser.parseWithMetadata(applyVisibleText, applyVisiblePackage).fields
                    val sameDestination = applyFields.destination.stableSignaturePart() == fields.destination.stableSignaturePart()
                    val sameFare = fields.fare.isNullOrBlank() || applyFields.fare.isNullOrBlank() ||
                        applyFields.fare.stableSignaturePart() == fields.fare.stableSignaturePart()
                    if (!sameDestination || !sameFare) {
                        lastAnalyzedHash = snapshotHash
                        traceEvent("analysis.discard stale_apply visible_changed hash=${dollar}snapshotHash visible_dest=${dollar}{applyFields.destination.diagnosticValue()} analyzed_dest=${dollar}{fields.destination.diagnosticValue()}") // stale_apply_visible_card_guard_0_1_84
                        recordDiagnostic(
                            stage = "stale_analysis_discarded",
                            reason = "O card visivel mudou antes da aplicacao da cor/km; ignorei resultado antigo.",
                            text = text,
                            fields = fields,
                            result = result,
                            cardTemplateMatch = cardMatch,
                        )
                        return
                    }
                }
            }
"""
            val radarAnchor = "            val radarColor = when (result.recommendation) {\n"
            val anchorIndex = text.indexOf(radarAnchor)
            if (anchorIndex >= 0) {
                text = text.substring(0, anchorIndex) + freshnessGuard + text.substring(anchorIndex)
                text = text.replace("            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash\n", "            lastAnalyzedHash = snapshotHash\n")
            }
        }

        if ("OCR_RESULT_MAX_AGE_MS" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar limite de idade do OCR.")
        }
        if ("stale_ocr_result_guard_0_1_84" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar descarte de OCR atrasado.")
        }

        if (text != original) file.writeText(text)
    }
}

liveResultFreshnessGuard.configure {
    mustRunAfter(
        "liveCardAnalysisRaceFix",
        "patchBubbleRenderStability",
        "noStickyDecisionCleanup",
        "globalLightDiagnostics"
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(liveResultFreshnessGuard)
}
