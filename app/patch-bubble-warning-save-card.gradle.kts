val patchBubbleWarningSaveCard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private var lastBubbleSaveCardText" !in text) {
            text = text.replace(
                "    private var currentDistanceKm: Double? = null\n",
                "    private var currentDistanceKm: Double? = null\n" +
                    "    private var currentBubbleLabel: String? = null\n" +
                    "    private var lastBubbleSaveCardText: String = \"\"\n" +
                    "    private var lastBubbleSaveCardPackage: String? = null\n",
            )
        }

        if ("rememberRideCardForBubble(snapshotText, packageName)\n        val cardMatch" !in text) {
            text = text.replace(
"""        val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)
""",
"""        rememberRideCardForBubble(snapshotText, packageName)
        val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)
""",
            )
        }

        text = text.replace(
"""            resetToDefault(reason = reason, text = snapshotText, fields = fields)
            return
        }
""",
"""            resetToSetupWarning(reason = reason, text = snapshotText, fields = fields)
            return
        }
""",
        )

        text = text.replace(
"""            val radarColor = when (result.recommendation) {
                Recommendation.GoodRide -> RadarColor.Green
                Recommendation.OutsideRadius -> RadarColor.Red
                Recommendation.InsufficientData -> RadarColor.Default
            }
            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
""",
"""            val radarColor = when (result.recommendation) {
                Recommendation.GoodRide -> RadarColor.Green
                Recommendation.OutsideRadius -> RadarColor.Red
                Recommendation.InsufficientData -> RadarColor.Default
            }
            val bubbleLabel = if (result.recommendation == Recommendation.InsufficientData) WARNING_BUBBLE_LABEL else null
            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} label=${dollar}{bubbleLabel.orEmpty()} distance=${dollar}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm(), labelText = bubbleLabel)
""",
        )

        if ("private fun rememberRideCardForBubble(" !in text) {
            text = text.replace(
"""    private fun clearRememberedRideText() {
        pendingAnalysis = null
        lastTextPackageName = null
        lastAccessibilityText = ""
        lastOcrText = ""
    }
""",
"""    private fun rememberRideCardForBubble(text: String, packageName: String?) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        lastBubbleSaveCardText = normalizedText
        lastBubbleSaveCardPackage = normalizePackageName(packageName)
        traceEvent("bubble.card_cache updated package=${dollar}{lastBubbleSaveCardPackage.orEmpty()} length=${dollar}{normalizedText.length}")
    }

    private fun clearRememberedRideText() {
        pendingAnalysis = null
        lastTextPackageName = null
        lastAccessibilityText = ""
        lastOcrText = ""
    }
""",
            )
        }

        if ("private fun resetToSetupWarning(" !in text) {
            text = text.replace(
"""    private fun resetToDefaultForNonRideScreen(reason: String, record: Boolean = false) {
        resetToIdle(reason = reason, record = record)
    }
""",
"""    private fun resetToDefaultForNonRideScreen(reason: String, record: Boolean = false) {
        resetToIdle(reason = reason, record = record)
    }

    private fun resetToSetupWarning(
        reason: String,
        text: String? = null,
        fields: RideFields? = null,
    ) {
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        clearRememberedRideText()
        showOverlay(RadarColor.Default, labelText = WARNING_BUBBLE_LABEL)
        recordDiagnostic(stage = "needs_setup", color = RadarColor.Default, reason = reason, text = text, fields = fields)
    }
""",
            )
        }

        text = text.replace(
"""    private fun saveCurrentRideCardFromBubble() {
        scope.launch {
            val packageName = currentWindowPackageName() ?: activePackageName
            val text = mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }
            if (text.isBlank()) {
                toast("Abra o card de corrida e tente salvar novamente.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "Nao havia texto lido suficiente para salvar card de corrida.",
                )
                return@launch
            }

            val inferredPackage = packageName?.lowercase(Locale.ROOT)
                ?: RideCardTemplateMatcher.inferPackageName(text)
""",
"""    private fun saveCurrentRideCardFromBubble() {
        scope.launch {
            val livePackageName = currentWindowPackageName()?.takeIf { shouldScanPackage(it) } ?: activePackageName?.takeIf { shouldScanPackage(it) }
            val liveText = if (shouldScanPackage(livePackageName)) {
                mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank { collectVisibleTextForAction() }
            } else {
                ""
            }
            val text = liveText.ifBlank { lastBubbleSaveCardText }.trim()
            if (text.isBlank()) {
                toast("Abra o card de corrida e toque duas vezes na bolinha.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "Nao havia texto lido suficiente para salvar card de corrida pela bolinha.",
                )
                return@launch
            }

            val inferredPackage = normalizePackageName(livePackageName)
                ?: lastBubbleSaveCardPackage
                ?: RideCardTemplateMatcher.inferPackageName(text)
""",
        )

        text = text.replace(
"""            toast("Card de corrida salvo.")
""",
"""            rememberRideCardForBubble(text, inferredPackage)
            showOverlay(RadarColor.Default, labelText = WARNING_BUBBLE_LABEL)
            toast("Card salvo. Aguarde o proximo card para validar.")
""",
        )

        text = text.replace(
"""            addView(actionMenuItem("💾  Salvar card de corrida") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
"""            addView(actionMenuItem("💾  Salvar card desta corrida") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
        )

        if ("private fun showOverlay(color: RadarColor, distanceKm: Double? = null, labelText: String? = null)" !in text) {
            text = text.replace(
                "    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {",
                "    private fun showOverlay(color: RadarColor, distanceKm: Double? = null, labelText: String? = null) {",
            )
        }

        if ("currentBubbleLabel = labelText" !in text) {
            text = text.replace(
                "        currentDistanceKm = distanceKm\n",
                "        currentDistanceKm = distanceKm\n        currentBubbleLabel = labelText\n",
            )
        }

        text = text.replace(
"""        view.text = formatBubbleDistanceKm(currentDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
""",
"""        view.text = currentBubbleLabel ?: formatBubbleDistanceKm(currentDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
""",
        )

        if ("private var lastTapUpMillis" !in text) {
            text = text.replace(
"""    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
""",
"""    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var lastTapUpMillis = 0L
""",
            )
        }

        text = text.replace(
"""                MotionEvent.ACTION_UP -> {
                    bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    if (!moved) view.performClick()
                    return true
                }
""",
"""                MotionEvent.ACTION_UP -> {
                    bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapUpMillis <= BUBBLE_DOUBLE_TAP_MS) {
                            lastTapUpMillis = 0L
                            hideActionMenu()
                            saveCurrentRideCardFromBubble()
                        } else {
                            lastTapUpMillis = now
                            view.performClick()
                        }
                    }
                    return true
                }
""",
        )

        if ("const val WARNING_BUBBLE_LABEL" !in text) {
            text = text.replace(
"""        const val BUBBLE_PREFS = "rota_certa_bubble"
""",
"""        const val WARNING_BUBBLE_LABEL = "⚠"
        const val BUBBLE_DOUBLE_TAP_MS = 450L
        const val BUBBLE_PREFS = "rota_certa_bubble"
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchBubbleWarningSaveCard)
}
