fun replacePrivateFunctionBlockManualCardLearningMode(
    source: String,
    functionName: String,
    transform: (String) -> String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    val block = if (next < 0) source.substring(start) else source.substring(start, next + 1)
    val replacement = transform(block)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val manualCardLearningMode by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"

        serviceFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""            if (shouldScanPackage(eventPackageName)) lastRidePackageName = eventPackageName
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) activePackageName else eventPackageName
""",
"""            if (shouldScanPackage(eventPackageName) || shouldLearnFromUnmonitoredPackage(eventPackageName)) lastRidePackageName = eventPackageName
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) activePackageName else eventPackageName
""",
            )

            text = text.replace(
"""        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            traceEvent("event blocked package=${dollar}packageName reason=${dollar}reason")
            scheduleVisibleTextAnalysis(delayMs = 20L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            if (isPassiveDiagnosticPackage(packageName)) {
                resetToDefaultForNonRideScreen(reason)
                return
            }
            resetToIdle(reason = reason, record = true)
            return
        }
""",
"""        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            traceEvent("event blocked package=${dollar}packageName reason=${dollar}reason")
            if (shouldLearnFromUnmonitoredPackage(packageName)) {
                traceEvent("learning.unmonitored_package package=${dollar}packageName")
                if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
                recordDiagnostic(
                    stage = "learning_unmonitored_package",
                    color = RadarColor.Default,
                    reason = "Pacote ainda nao monitorado; lendo apenas para cadastrar modelo de card.",
                )
                scheduleVisibleTextAnalysis(delayMs = 20L, allowPopupCandidate = true)
                requestScreenshotAnalysis(allowPopupCandidate = true)
                return
            }
            scheduleVisibleTextAnalysis(delayMs = 20L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            if (isPassiveDiagnosticPackage(packageName)) {
                resetToDefaultForNonRideScreen(reason)
                return
            }
            resetToIdle(reason = reason, record = true)
            return
        }
""",
            )

            text = text.replace(
"""                if (shouldScanPackage(packageName)) {
                    if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
                    scheduleVisibleTextAnalysis(delayMs = 0L)
                    requestScreenshotAnalysis()
                } else if (isPassiveDiagnosticPackage(packageName)) {
""",
"""                if (shouldScanPackage(packageName)) {
                    if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
                    scheduleVisibleTextAnalysis(delayMs = 0L)
                    requestScreenshotAnalysis()
                } else if (shouldLearnFromUnmonitoredPackage(packageName)) {
                    if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
                    val visibleText = collectVisibleText(allowPopupCandidate = true)
                    if (visibleText.isNotBlank()) {
                        traceEvent("learning.loop package=${dollar}{packageName.orEmpty()} length=${dollar}{visibleText.length}")
                        processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                    }
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                } else if (isPassiveDiagnosticPackage(packageName)) {
""",
            )

            text = text.replace(
"""        val packageName = resolveRidePackageForText(windowPackageName, text, allowPopupCandidate)
        if (!shouldScanPackage(packageName)) {
            if (allowPopupCandidate && text.isNotBlank()) {
                traceEvent("popup.candidate ignored reason=package_not_identified raw_length=${dollar}{text.length}")
            }
            return
        }
        traceEvent("process.start source=${dollar}source package=${dollar}{packageName.orEmpty()} raw_length=${dollar}{text.length}")
        if (!allowPopupCandidate) {
            rememberSourceText(packageName, source, text)
        } else {
            rememberPopupCandidatePackage(packageName)
        }
""",
"""        val packageName = resolveRidePackageForText(windowPackageName, text, allowPopupCandidate)
        val learningUnmonitoredPackage = shouldLearnFromUnmonitoredPackage(packageName)
        if (!shouldScanPackage(packageName) && !learningUnmonitoredPackage) {
            if (allowPopupCandidate && text.isNotBlank()) {
                traceEvent("popup.candidate ignored reason=package_not_identified raw_length=${dollar}{text.length}")
            }
            return
        }
        if (learningUnmonitoredPackage) {
            traceEvent("learning.unmonitored_package process package=${dollar}{packageName.orEmpty()} raw_length=${dollar}{text.length}")
        }
        traceEvent("process.start source=${dollar}source package=${dollar}{packageName.orEmpty()} raw_length=${dollar}{text.length}")
        if (!allowPopupCandidate || learningUnmonitoredPackage) {
            rememberSourceText(packageName, source, text)
        } else {
            rememberPopupCandidatePackage(packageName)
        }
""",
            )

            text = text.replace(
"""            traceEvent("card_model.missing package=${dollar}{packageName.orEmpty()} templates=${dollar}{currentCardTemplates.size}")
            if (allowPopupCandidate) return
""",
"""            traceEvent("card_model.missing package=${dollar}{packageName.orEmpty()} templates=${dollar}{currentCardTemplates.size}")
            if (allowPopupCandidate && !learningUnmonitoredPackage) return
            if (learningUnmonitoredPackage) {
                traceEvent("learning.card_model_missing captured package=${dollar}{packageName.orEmpty()}")
            }
""",
            )

            text = text.replace(
"""        if (allowPopupCandidate && !shouldScanPackage(requestWindowPackageName) && !isPassiveDiagnosticPackage(requestWindowPackageName)) {
            traceEvent("screenshot.request skipped unmonitored_popup_package=${dollar}{requestWindowPackageName.orEmpty()}")
            return
        }
""",
"""        if (allowPopupCandidate && !shouldScanPackage(requestWindowPackageName) && !shouldLearnFromUnmonitoredPackage(requestWindowPackageName) && !isPassiveDiagnosticPackage(requestWindowPackageName)) {
            traceEvent("screenshot.request skipped unmonitored_popup_package=${dollar}{requestWindowPackageName.orEmpty()}")
            return
        }
""",
            )

            text = replacePrivateFunctionBlockManualCardLearningMode(text, "resolveRidePackageForText") {
"""    private fun resolveRidePackageForText(
        windowPackageName: String?,
        text: String,
        allowPopupCandidate: Boolean,
    ): String? {
        val normalizedWindowPackage = normalizePackageName(windowPackageName)
        if (shouldScanPackage(normalizedWindowPackage)) return normalizedWindowPackage
        if (allowPopupCandidate && shouldLearnFromUnmonitoredPackage(normalizedWindowPackage)) return normalizedWindowPackage
        if (!allowPopupCandidate) return normalizedWindowPackage
        val inferredPackage = RideCardTemplateMatcher.inferPackageName(text)?.let(::normalizePackageName)
        if (inferredPackage != null && (shouldScanPackage(inferredPackage) || shouldLearnFromUnmonitoredPackage(inferredPackage))) return inferredPackage
        return lastRidePackageName?.takeIf { lastPackage ->
            text.isNotBlank() && (shouldScanPackage(lastPackage) || shouldLearnFromUnmonitoredPackage(lastPackage))
        }
    }

"""
            }

            text = replacePrivateFunctionBlockManualCardLearningMode(text, "saveCurrentRideCardFromBubble") {
"""    private fun saveCurrentRideCardFromBubble() {
        scope.launch {
            val packageName = listOf(lastTextPackageName, lastRidePackageName, currentWindowPackageName(), activePackageName)
                .mapNotNull { normalizePackageName(it) }
                .firstOrNull { candidate ->
                    candidate != this@LiveRideAccessibilityService.packageName &&
                        (shouldScanPackage(candidate) || shouldLearnFromUnmonitoredPackage(candidate))
                }
            val text = mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }.trim()
            if (text.isBlank()) {
                toast("Abra o card de corrida e tente salvar novamente.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "Nao havia texto lido suficiente para salvar card de corrida.",
                )
                return@launch
            }

            val inferredPackage = packageName ?: RideCardTemplateMatcher.inferPackageName(text)?.let(::normalizePackageName)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
            repository.addCardTemplate(template)
            if (!inferredPackage.isNullOrBlank()) {
                val updatedPackages = mergePackageIntoList(currentSettings.extraMonitoredPackages, inferredPackage)
                val updatedSettings = currentSettings.copy(extraMonitoredPackages = updatedPackages)
                repository.saveSettings(updatedSettings)
                currentSettings = updatedSettings
                lastRidePackageName = inferredPackage
                activePackageName = inferredPackage
            }
            val parseResult = parser.parseWithMetadata(text, inferredPackage)
            repository.addCapturedScreen(
                CapturedRideScreen(
                    createdAtMillis = System.currentTimeMillis(),
                    packageName = inferredPackage,
                    textHash = text.snapshotHash(),
                    textPreview = text.trim().take(DIAGNOSTIC_TEXT_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = parseResult.fields.pickup,
                    destination = parseResult.fields.destination,
                    fare = parseResult.fields.fare,
                ),
            )
            toast("Card de corrida salvo.")
            recordDiagnostic(
                stage = "bubble_save_card",
                color = currentRadarColor,
                reason = "Card de corrida salvo pela bolinha: ${dollar}{template.name}.",
                text = text,
                fields = parseResult.fields,
            )
        }
    }

"""
            }

            text = replacePrivateFunctionBlockManualCardLearningMode(text, "shouldLearnFromUnmonitoredPackage") {
"""    private fun shouldLearnFromUnmonitoredPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (normalized == this.packageName) return false
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        val settings = currentSettings
        if (!settings.appEnabled) return false
        return normalized !in selectedRidePackages(settings)
    }

"""
            }

            if ("private fun shouldLearnFromUnmonitoredPackage(" !in text) {
                text = text.replace(
"""
    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())
""",
"""
    private fun shouldLearnFromUnmonitoredPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (normalized == this.packageName) return false
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        val settings = currentSettings
        if (!settings.appEnabled) return false
        return normalized !in selectedRidePackages(settings)
    }

    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())
""",
                )
            }

            if ("manual_card_learning_mode.patch_applied" !in text) {
                text = text.replace(
                    "        traceEvent(\"manual_card_packages_only.patch_applied=true\")\n",
                    "        traceEvent(\"manual_card_packages_only.patch_applied=true\")\n        traceEvent(\"manual_card_learning_mode.patch_applied=true\")\n",
                )
            }

            if (text != original) file.writeText(text)
        }
    }
}

manualCardLearningMode.configure {
    mustRunAfter("manualCardPackagesOnly")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(manualCardLearningMode)
}
