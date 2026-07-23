// Central final da bolinha e da interface.
// Roda depois da leitura universal para:
// - transformar o menu flutuante em grade de bolinhas;
// - mover WhatsApp para dentro da grade;
// - remover a aba Config da navegacao e acessa-la por bolinhas;
// - estabilizar Accessibility/OCR por endereco, nao por pacote.

val unifiedBubbleControlCenterFinal by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    fun replaceRegion(
        source: String,
        startToken: String,
        endToken: String,
        replacement: String,
        label: String,
    ): String {
        val start = source.indexOf(startToken)
        val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
        if (start < 0 || end < 0) throw GradleException("Nao encontrei regiao para $label.")
        return source.substring(0, start) + replacement + source.substring(end)
    }

    doLast {
        val service = serviceFile.asFile
        var serviceText = service.readText()

        if ("import android.widget.GridLayout" !in serviceText) {
            serviceText = serviceText.replace(
                "import android.widget.LinearLayout\n",
                "import android.widget.LinearLayout\nimport android.widget.GridLayout\n",
            )
        }

        if ("universal_source_freshness_fields_0_1_94" !in serviceText) {
            serviceText = serviceText.replace(
                "    private var lastOcrText: String = \"\"\n",
                """    private var lastOcrText: String = ""
    private var lastAccessibilityTextAtMillis: Long = 0L
    private var lastOcrTextAtMillis: Long = 0L
    private var lastUniversalAddressSeenAtMillis: Long = 0L
    private var lastUniversalAddressSignature: String? = null // universal_source_freshness_fields_0_1_94
""",
            )
        }

        if ("universal_source_freshness_process_0_1_94" !in serviceText) {
            serviceText = replaceRegion(
                serviceText,
                "    private suspend fun processRideText(",
                "    private fun resolveRidePackageForText(",
                """    private fun forceClearUniversalResult(reason: String) {
        invalidateLiveAnalysis("universal_clear:" + reason)
        registeredCardGate.clear()
        coreCardAnalysisCoalescer.invalidate()
        lastUniversalAddressSignature = null
        lastVisibleCardSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        lastDecisionOverlayAtMillis = 0L
        lastAccessibilityText = ""
        lastOcrText = ""
        lastAccessibilityTextAtMillis = 0L
        lastOcrTextAtMillis = 0L
        currentRadarColor = RadarColor.Idle
        currentDistanceKm = null
        showOverlay(RadarColor.Idle, null)
        traceEvent("universal.clear reason=" + reason)
    }

    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        if (!serviceReady || !currentSettings.appEnabled) return

        val packageName = normalizePackageName(currentWindowPackageName()) ?: "android.visible.screen"
        val sourceText = text.trim()
        val previousPackageName = lastTextPackageName

        if (packageName == this.packageName) {
            forceClearUniversalResult("Tela do proprio Rota Certa nao e destino de corrida.")
            return
        }

        if (!allowPopupCandidate) rememberSourceText(packageName, source, sourceText)
        val now = System.currentTimeMillis()
        val packageChanged = previousPackageName != null && previousPackageName != packageName
        traceEvent("universal.process source=${'$'}source package=${'$'}packageName length=${'$'}{sourceText.length}")

        val directFields = UniversalScreenAddressParser.parse(sourceText)
        val fallbackText = when (source) {
            TextSource.Accessibility -> lastOcrText.takeIf { now - lastOcrTextAtMillis <= 900L }.orEmpty()
            TextSource.Ocr -> lastAccessibilityText.takeIf { now - lastAccessibilityTextAtMillis <= 900L }.orEmpty()
        }
        val fallbackFields = if (directFields.destination.isNullOrBlank() && fallbackText.isNotBlank()) {
            UniversalScreenAddressParser.parse(fallbackText)
        } else {
            RideFields()
        }
        val fields = if (!directFields.destination.isNullOrBlank()) directFields else fallbackFields
        val destination = fields.destination?.trim()

        if (destination.isNullOrBlank()) {
            traceEvent("universal.address none package=${'$'}packageName packageChanged=${'$'}packageChanged")
            if (!allowPopupCandidate) {
                val missingForMillis = now - lastUniversalAddressSeenAtMillis
                if (packageChanged || lastUniversalAddressSeenAtMillis == 0L || missingForMillis >= 420L) {
                    forceClearUniversalResult("Nenhum endereco visivel; resultado anterior removido.")
                } else {
                    traceEvent("universal.address transient_missing age=${'$'}missingForMillis keep=true")
                }
            }
            return
        }

        val analysisSignature = "last-address|" + destination.lowercase(Locale.ROOT)
        val snapshotHash = analysisSignature.hashCode()
        val addressChanged = lastUniversalAddressSignature != null && lastUniversalAddressSignature != analysisSignature
        lastUniversalAddressSeenAtMillis = now
        lastUniversalAddressSignature = analysisSignature
        lastVisibleCardSignature = analysisSignature
        registeredCardGate.markSeen()
        traceEvent("universal.address destination=${'$'}{destination.take(100)} hash=${'$'}snapshotHash changed=${'$'}addressChanged")

        if (snapshotHash == lastAnalyzedHash &&
            (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
        ) return

        val previousAnalysisJob = liveAnalysisJob
        when (coreCardAnalysisCoalescer.beforeStart(
            signature = analysisSignature,
            activeJob = previousAnalysisJob?.isActive == true,
            hasAppliedDecision = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red,
        )) {
            br.com.mapeiaia.rotacerta.core.CoreCardAnalysisAction.CoalesceActive -> {
                traceEvent("universal.analysis coalesced signature=${'$'}analysisSignature")
                return
            }
            br.com.mapeiaia.rotacerta.core.CoreCardAnalysisAction.ReuseCompleted -> {
                traceEvent("universal.analysis reused signature=${'$'}analysisSignature")
                return
            }
            br.com.mapeiaia.rotacerta.core.CoreCardAnalysisAction.Start -> Unit
        }

        lastSnapshotHash = snapshotHash
        if (addressChanged || currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)

        val latestAnalysisToken = ++analysisSerial
        previousAnalysisJob?.cancel()
        pendingAnalysis = null
        analyzing = false
        liveAnalysisJob = scope.launch {
            val completed = kotlinx.coroutines.withTimeoutOrNull(LIVE_ANALYSIS_TIMEOUT_MS) {
                analyzeLiveText(
                    text = sourceText.ifBlank { destination },
                    fields = fields,
                    snapshotHash = snapshotHash,
                    cardMatch = null,
                    allowPopupCandidate = allowPopupCandidate,
                    analysisToken = latestAnalysisToken,
                    analysisCardSignature = analysisSignature,
                )
                true
            } ?: false
            if (!completed && latestAnalysisToken == analysisSerial) {
                analyzing = false
                liveAnalysisJob = null
                coreCardAnalysisCoalescer.finish(analysisSignature)
                if (lastUniversalAddressSignature == analysisSignature) showOverlay(RadarColor.Default)
            }
        }
    } // universal_source_freshness_process_0_1_94

""",
                "processRideText universal final",
            )
        }

        if ("universal_source_timestamps_0_1_94" !in serviceText) {
            serviceText = replaceRegion(
                serviceText,
                "    private fun rememberSourceText(",
                "    private fun rememberPopupCandidatePackage(",
                """    private fun rememberSourceText(packageName: String?, source: TextSource, text: String) {
        val normalizedPackage = normalizePackageName(packageName)
        if (normalizedPackage != lastTextPackageName) {
            traceEvent("source.reset package=${'$'}{normalizedPackage.orEmpty()}")
            lastTextPackageName = normalizedPackage
            lastAccessibilityText = ""
            lastOcrText = ""
            lastAccessibilityTextAtMillis = 0L
            lastOcrTextAtMillis = 0L
        }
        val now = System.currentTimeMillis()
        when (source) {
            TextSource.Accessibility -> {
                lastAccessibilityText = text.trim()
                lastAccessibilityTextAtMillis = now
            }
            TextSource.Ocr -> {
                lastOcrText = text.trim()
                lastOcrTextAtMillis = now
            }
        }
    } // universal_source_timestamps_0_1_94

""",
                "timestamps das fontes",
            )
        }

        serviceText = serviceText
            .replace("            showWhatsAppShortcut() // screen_phone_whatsapp_0_1_84\n", "            // WhatsApp agora fica dentro da central da bolinha. // whatsapp_inside_grid_0_1_94\n")
            .replace("                    updateWhatsAppShortcutPosition()\n", "")

        if ("private fun openAppTab(tab: String)" !in serviceText) {
            serviceText = serviceText.replace(
                "    private fun openApp() {\n",
                """    private fun openAppTab(tab: String) {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, tab),
            )
        }
    }

    private fun openApp() {
""",
            )
        }

        if ("unified_bubble_grid_0_1_94" !in serviceText) {
            serviceText = replaceRegion(
                serviceText,
                "    private fun showActionMenu() {",
                "    private fun hideActionMenu() {",
                """    private fun showActionMenu() {
        val manager = windowManager ?: return
        if (overlayMenuView != null) return
        val bubbleParams = overlayParams ?: return
        val menu = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.argb(242, 24, 24, 28))
                setStroke(dp(2), Color.argb(225, 255, 255, 255))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))

            addView(quickBubbleButton("Rota", currentSettings.appEnabled) { button ->
                val updated = currentSettings.copy(appEnabled = !currentSettings.appEnabled)
                currentSettings = updated
                scope.launch { repository.saveSettings(updated) }
                applyQuickBubbleVisual(button, updated.appEnabled)
            })
            addView(quickBubbleButton("Alertas", currentSettings.proximityAlertsEnabled) { button ->
                val updated = currentSettings.copy(proximityAlertsEnabled = !currentSettings.proximityAlertsEnabled)
                currentSettings = updated
                scope.launch { repository.saveSettings(updated) }
                applyQuickBubbleVisual(button, updated.proximityAlertsEnabled)
            })
            addView(quickBubbleButton("Acesso", true) {
                hideActionMenu()
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
            addView(quickBubbleButton("WA", true) {
                hideActionMenu()
                capturePhoneAndOpenWhatsApp()
            })
            addView(quickBubbleButton("Apar.", currentSettings.bubbleDarkMode) { button ->
                val updated = currentSettings.copy(bubbleDarkMode = !currentSettings.bubbleDarkMode)
                currentSettings = updated
                scope.launch { repository.saveSettings(updated) }
                applyQuickBubbleVisual(button, updated.bubbleDarkMode)
                showOverlay(currentRadarColor, currentDistanceKm)
            })
            addView(quickBubbleButton("Casa", currentSettings.homeAddress.isNotBlank()) { openAppTab(TAB_ANALYSIS) })
            addView(quickBubbleButton("Salvar", false) {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(quickBubbleButton("Alerta", currentSettings.proximityAlertsEnabled) {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            })
            addView(quickBubbleButton("Radares", currentImportedRadars.isNotEmpty()) { openAppTab(TAB_CONFIG) })
            addView(quickBubbleButton("Relat.", false) { openAppTab(TAB_TOOLS) })
            addView(quickBubbleButton("Backup", false) { openAppTab(TAB_CONFIG) })
            addView(quickBubbleButton("Fechar", false) { hideActionMenu() })
        }
        val panelWidth = dp(252)
        val panelHeight = dp(340)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (bubbleParams.x + dp(76) + panelWidth <= screenWidth) {
                bubbleParams.x + dp(76)
            } else {
                (bubbleParams.x - panelWidth - dp(10)).coerceAtLeast(0)
            }
            y = bubbleParams.y.coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0))
        }
        if (runCatching { manager.addView(menu, params) }.isSuccess) {
            overlayMenuView = menu
            overlayMenuParams = params
        }
    } // unified_bubble_grid_0_1_94

    private fun quickBubbleButton(
        label: String,
        active: Boolean,
        action: (TextView) -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        contentDescription = label
        gravity = Gravity.CENTER
        includeFontPadding = false
        textSize = 12f
        setTextColor(Color.WHITE)
        setTypeface(Typeface.DEFAULT_BOLD)
        layoutParams = GridLayout.LayoutParams().apply {
            width = dp(72)
            height = dp(72)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        applyQuickBubbleVisual(this, active)
        setOnClickListener { action(this) }
    }

    private fun applyQuickBubbleVisual(button: TextView, active: Boolean) {
        button.alpha = if (active) 1f else 0.72f
        button.elevation = dp(if (active) 12 else 2).toFloat()
        button.setShadowLayer(
            if (active) dp(8).toFloat() else 0f,
            0f,
            0f,
            if (active) Color.argb(230, 70, 220, 130) else Color.TRANSPARENT,
        )
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Color.rgb(31, 170, 92) else Color.rgb(70, 70, 78))
            setStroke(dp(if (active) 3 else 1), if (active) Color.WHITE else Color.argb(150, 255, 255, 255))
        }
    }

""",
                "grade flutuante",
            )
        }

        if ("unified_bubble_grid_position_0_1_94" !in serviceText) {
            serviceText = replaceRegion(
                serviceText,
                "    private fun updateActionMenuPosition() {",
                "    private fun toast(message: String) {",
                """    private fun updateActionMenuPosition() {
        val manager = windowManager ?: return
        val view = overlayMenuView ?: return
        val params = overlayMenuParams ?: return
        val bubbleParams = overlayParams ?: return
        val panelWidth = dp(252)
        val panelHeight = dp(340)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        params.x = if (bubbleParams.x + dp(76) + panelWidth <= screenWidth) {
            bubbleParams.x + dp(76)
        } else {
            (bubbleParams.x - panelWidth - dp(10)).coerceAtLeast(0)
        }
        params.y = bubbleParams.y.coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0))
        runCatching { manager.updateViewLayout(view, params) }
    } // unified_bubble_grid_position_0_1_94

""",
                "posicao da grade",
            )
        }

        listOf(
            "universal_source_freshness_fields_0_1_94",
            "universal_source_freshness_process_0_1_94",
            "universal_source_timestamps_0_1_94",
            "unified_bubble_grid_0_1_94",
            "unified_bubble_grid_position_0_1_94",
            "whatsapp_inside_grid_0_1_94",
        ).forEach { marker ->
            if (marker !in serviceText) throw GradleException("Central final incompleta no servico: $marker")
        }
        service.writeText(serviceText)

        val main = mainFile.asFile
        var mainText = main.readText()
        if ("import androidx.compose.foundation.layout.size" !in mainText) {
            mainText = mainText.replace(
                "import androidx.compose.foundation.layout.padding\n",
                "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.size\n",
            )
        }
        if ("import androidx.compose.foundation.shape.CircleShape" !in mainText) {
            mainText = mainText.replace(
                "import androidx.compose.foundation.rememberScrollState\n",
                "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.shape.CircleShape\n",
            )
        }
        if ("import androidx.compose.material3.ButtonDefaults" !in mainText) {
            mainText = mainText.replace(
                "import androidx.compose.material3.Button\n",
                "import androidx.compose.material3.Button\nimport androidx.compose.material3.ButtonDefaults\n",
            )
        }

        mainText = mainText.replace(
            "                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { tab = TAB_CONFIG }, label = { Text(\"Config\") }, icon = {})\n",
            "",
        )

        if ("unified_app_control_bubbles_0_1_94" !in mainText) {
            val anchor = "            Spacer(Modifier.height(16.dp))\n\n            when (tab) {\n"
            val replacement = """            Spacer(Modifier.height(16.dp))

            if (tab == TAB_ANALYSIS) {
                UnifiedAppControlBubbles(
                    settings = settings,
                    liveEnabled = liveEnabled,
                    onToggleApp = { scope.launch { repository.saveSettings(settings.copy(appEnabled = !settings.appEnabled)) } },
                    onToggleAlerts = { scope.launch { repository.saveSettings(settings.copy(proximityAlertsEnabled = !settings.proximityAlertsEnabled)) } },
                    onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onToggleAppearance = { scope.launch { repository.saveSettings(settings.copy(bubbleDarkMode = !settings.bubbleDarkMode)) } },
                    onOpenHome = { tab = TAB_ANALYSIS },
                    onOpenSettings = { tab = TAB_CONFIG },
                    onOpenTools = { tab = TAB_TOOLS },
                )
                Spacer(Modifier.height(14.dp))
            }

            when (tab) {
"""
            if (anchor !in mainText) throw GradleException("Nao encontrei o ponto para inserir a central de bolinhas no app.")
            mainText = mainText.replace(anchor, replacement)

            val functionAnchor = "@Composable\nprivate fun AnalysisScreen("
            val controlFunction = """@Composable
private fun UnifiedAppControlBubbles(
    settings: AppSettings,
    liveEnabled: Boolean,
    onToggleApp: () -> Unit,
    onToggleAlerts: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onToggleAppearance: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTools: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Central de controles", fontWeight = FontWeight.Bold)
        Text(
            "Todos os recursos principais ficam reunidos em bolinhas. Ativo ganha brilho e sombra.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppControlBubble("Rota", settings.appEnabled, onToggleApp)
            AppControlBubble("Alertas", settings.proximityAlertsEnabled, onToggleAlerts)
            AppControlBubble("Acesso", liveEnabled, onOpenAccessibility)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppControlBubble("Apar.", settings.bubbleDarkMode, onToggleAppearance)
            AppControlBubble("Casa", settings.homeAddress.isNotBlank(), onOpenHome)
            AppControlBubble("Radares", false, onOpenSettings)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppControlBubble("Backup", false, onOpenSettings)
            AppControlBubble("Relat.", false, onOpenTools)
            AppControlBubble("Mais", true, onOpenSettings)
        }
    }
} // unified_app_control_bubbles_0_1_94

@Composable
private fun AppControlBubble(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (active) 12.dp else 2.dp,
            pressedElevation = 14.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

"""
            if (functionAnchor !in mainText) throw GradleException("Nao encontrei AnalysisScreen para inserir componentes circulares.")
            mainText = mainText.replace(functionAnchor, controlFunction + functionAnchor)
        }

        mainText = mainText
            .replace("        Text(\"Configuracoes\", fontWeight = FontWeight.Bold)\n", "        Text(\"Central de controles e ajustes\", fontWeight = FontWeight.Bold)\n")
            .replace("Text(\"Salvar configuracoes\")", "Text(\"Salvar ajustes\")")

        if ("unified_app_control_bubbles_0_1_94" !in mainText) {
            throw GradleException("Central de bolinhas nao foi inserida no app.")
        }
        if ("label = { Text(\"Config\") }" in mainText) {
            throw GradleException("A aba Config ainda aparece na navegacao.")
        }
        main.writeText(mainText)
    }
}

unifiedBubbleControlCenterFinal.configure {
    mustRunAfter("universalLastAddressCompileFix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(unifiedBubbleControlCenterFinal)
}
