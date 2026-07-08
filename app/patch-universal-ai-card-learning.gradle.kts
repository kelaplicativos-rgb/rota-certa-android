val universalAiCardLearning by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchUniversalLearningService(serviceFile.asFile)
        patchUniversalLearningDiagnostics(mainFile.asFile)
    }
}

fun patchUniversalLearningService(file: java.io.File) {
    var text = file.readText()
    val original = text

    val directSaveFunction = """    private fun saveLongPressCapturedCardDirectly() {
        scope.launch {
            traceEvent("diagnostic.contract bubble_long_press step=direct_save_start ok=true")
            val candidate = bestCardSaveCandidate(null, "")
            val sourcePackageName = candidate?.first ?: lastCardSaveCandidatePackageName
            val capturedText = candidate?.second ?: lastCardSaveCandidateText
            val learnedPackage = RideCardTemplateMatcher.packageNameForLearning(sourcePackageName, capturedText)
            traceEvent("diagnostic.contract bubble_long_press step=post_ocr_candidate ok=${'$'}{capturedText.isNotBlank()} source_package=${'$'}{sourcePackageName.orEmpty()} learned_package=${'$'}{learnedPackage.orEmpty()} text_len=${'$'}{capturedText.length}")

            if (capturedText.isBlank()) {
                traceEvent("diagnostic.contract bubble_long_press step=direct_save_fail reason=text_blank")
                toast("Nao consegui ler o card neste print.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "Toque longo capturou a tela, mas nao havia texto suficiente para salvar o card.",
                    text = capturedText,
                )
                return@launch
            }

            if (learnedPackage == null) {
                traceEvent("diagnostic.contract bubble_long_press step=direct_save_fail reason=not_ride_card source_package=${'$'}{sourcePackageName.orEmpty()} text_len=${'$'}{capturedText.length}")
                toast("Li o print, mas nao parece card de corrida.")
                recordDiagnostic(
                    stage = "bubble_save_card_not_ride_card",
                    color = currentRadarColor,
                    reason = "A captura foi lida, mas a validacao local nao encontrou dados suficientes de card de corrida.",
                    text = capturedText,
                )
                return@launch
            }

            val universalModel = RideCardTemplateMatcher.isUniversalLearnedPackage(learnedPackage)
            traceEvent("diagnostic.contract bubble_long_press step=ai_validation ok=true source_package=${'$'}{sourcePackageName.orEmpty()} learned_package=${'$'}learnedPackage universal=${'$'}universalModel")
            traceEvent("diagnostic.contract save_card step=started ok=true source=long_press_direct")
            traceEvent("bubble.save_card_start source=long_press_direct")

            val templateName = if (universalModel) "Card universal por print" else null
            val template = RideCardTemplateMatcher.createTemplate(learnedPackage, capturedText, templateName)
            repository.addCardTemplate(template)
            rememberCardSaveCandidate(learnedPackage, capturedText, "long_press_card_saved")

            val packageForParsing = RideCardTemplateMatcher.inferPackageName(capturedText)
                ?: learnedPackage.takeUnless { RideCardTemplateMatcher.isUniversalLearnedPackage(it) }
            val parseResult = parser.parseWithMetadata(capturedText, packageForParsing)
            repository.addCapturedScreen(
                CapturedRideScreen(
                    createdAtMillis = System.currentTimeMillis(),
                    packageName = learnedPackage,
                    textHash = capturedText.snapshotHash(),
                    textPreview = capturedText.trim().take(DIAGNOSTIC_TEXT_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = parseResult.fields.pickup,
                    destination = parseResult.fields.destination,
                    fare = parseResult.fields.fare,
                ),
            )
            traceEvent("diagnostic.contract save_card result=success source=long_press_direct learned_package=${'$'}learnedPackage source_package=${'$'}{sourcePackageName.orEmpty()} universal=${'$'}universalModel text_len=${'$'}{capturedText.length}")
            toast(if (universalModel) "Card salvo como modelo universal." else "Card de corrida salvo.")
            recordDiagnostic(
                stage = "bubble_save_card",
                color = currentRadarColor,
                reason = if (universalModel) {
                    "Card de corrida salvo pelo toque longo como modelo universal validado pelo conteudo: ${'$'}{template.name}."
                } else {
                    "Card de corrida salvo pelo toque longo: ${'$'}{template.name}."
                },
                text = capturedText,
                fields = parseResult.fields,
            )
        }
    }

"""

    val directSaveRegex = Regex("(?s)    private fun saveLongPressCapturedCardDirectly\\(\\) \\{.*?    private fun captureAndSaveCardFromBubbleLongPress\\(\\) \\{")
    text = directSaveRegex.replace(text) { directSaveFunction + "    private fun captureAndSaveCardFromBubbleLongPress() {" }

    val shouldScanReplacement = """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (normalized == this.packageName) return false
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        if (isCaptureOnlyLearningPackage(normalized)) return false
        val settings = currentSettings
        if (!settings.appEnabled) return false
        if (normalized in selectedRidePackages(settings)) return true
        return RegisteredRidePackagePolicy.hasUniversalTemplate(currentCardTemplates)
    }

    private fun isCaptureOnlyLearningPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        return normalized.contains("documentsui") ||
            normalized.contains("android.apps.nbu.files") ||
            normalized.contains("sec.android.app.myfiles") ||
            normalized.contains("android.apps.photos") ||
            normalized.contains("android.apps.docs") ||
            normalized.contains("chrome")
    }

    private fun selectedRidePackages"""

    text = Regex("(?s)    private fun shouldScanPackage\\(packageName: String\\?\\): Boolean \\{.*?    private fun selectedRidePackages").replace(text) {
        shouldScanReplacement
    }

    val scanBlockReplacement = """    private fun scanBlockReason(packageName: String?): String {
        val normalized = normalizePackageName(packageName)
        if (normalized.isNullOrBlank()) return "Pacote ativo nao informado pelo Android."
        if (normalized == this.packageName) return "Rota Certa esta em primeiro plano; leitura pausada."
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return "Pacote passivo ignorado sem apagar a ultima decisao: ${'$'}normalized."
        if (normalized in IGNORED_PACKAGES) return "Pacote ignorado para evitar leitura fora do card: ${'$'}normalized."
        if (isCaptureOnlyLearningPackage(normalized)) return "Pacote usado apenas para ensinar por print; leitura ao vivo pausada: ${'$'}normalized."
        if (normalized !in selectedRidePackages(currentSettings)) {
            return if (RegisteredRidePackagePolicy.hasUniversalTemplate(currentCardTemplates)) {
                "Pacote permitido por modelo universal aprendido: ${'$'}normalized."
            } else {
                "Pacote sem modelo de card cadastrado pelo usuario; bolinha em espera: ${'$'}normalized."
            }
        }
        return "Pacote permitido: ${'$'}normalized."
    }

    private fun recordDiagnostic"""

    text = Regex("(?s)    private fun scanBlockReason\\(packageName: String\\?\\): String \\{.*?    private fun recordDiagnostic").replace(text) {
        scanBlockReplacement
    }

    if (text != original) file.writeText(text)
}

fun patchUniversalLearningDiagnostics(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""    val longPressDirectBlockedPackage = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=blocked_package")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered
""",
"""    val longPressDirectBlockedPackage = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=blocked_package")
    val longPressDirectNotRideCard = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=not_ride_card") || stage == "bubble_save_card_not_ride_card"
    val longPressUniversalModelSaved = has("diagnostic.contract save_card result=success source=long_press_direct") && has("universal=true")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered
""",
    )

    text = text.replace(
"""        longPressDirectTextBlank && !saveSuccess -> "A bolinha tirou print no toque longo, mas o OCR nao entregou texto suficiente para salvar."
        longPressDirectBlockedPackage && !saveSuccess -> "A bolinha leu a tela, mas recusou salvar porque o pacote atual nao e app de corrida. Abra o card no app de corrida e segure novamente."
""",
"""        longPressUniversalModelSaved -> "Card salvo com sucesso como modelo universal aprendido por print."
        longPressDirectTextBlank && !saveSuccess -> "A bolinha tirou print no toque longo, mas o OCR nao entregou texto suficiente para salvar."
        longPressDirectNotRideCard && !saveSuccess -> "A bolinha leu a tela ou print, mas a validacao local nao confirmou que aquilo e um card de corrida."
        longPressDirectBlockedPackage && !saveSuccess -> "A bolinha leu a tela, mas recusou salvar porque o pacote atual nao e app de corrida. Abra o card no app de corrida e segure novamente."
""",
    )

    text = text.replace(
"""        put("falhaToqueLongoPacoteBloqueado", longPressDirectBlockedPackage)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
"""        put("falhaToqueLongoPacoteBloqueado", longPressDirectBlockedPackage)
        put("falhaToqueLongoNaoPareceCard", longPressDirectNotRideCard)
        put("modeloUniversalToqueLongo", longPressUniversalModelSaved)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
    )

    if (text != original) file.writeText(text)
}

universalAiCardLearning.configure {
    mustRunAfter("bubbleLongPressDirectSaveAfterOcr")
    mustRunAfter("bubbleLongPressCaptureSave")
    mustRunAfter("bubbleActionDiagnosticHardening")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(universalAiCardLearning)
}
