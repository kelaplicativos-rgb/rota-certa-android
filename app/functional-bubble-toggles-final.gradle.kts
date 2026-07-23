// Garante que as bolinhas de funcao sejam controles reais e persistentes.
// Acoes pontuais continuam como acoes, sem exibir um estado ON falso.

val functionalBubbleTogglesFinal by tasks.registering {
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

        if ("functional_bubble_reading_gate_0_1_95" !in serviceText) {
            val anchor = "        if (!serviceReady || !currentSettings.appEnabled) return\n\n        val packageName"
            val replacement = """        if (!serviceReady || !currentSettings.appEnabled) return
        if (!currentSettings.liveReadingEnabled) {
            if (!allowPopupCandidate) forceClearUniversalResult("Leitura ao vivo desligada pela bolinha.")
            return
        } // functional_bubble_reading_gate_0_1_95

        val packageName"""
            if (anchor !in serviceText) throw GradleException("Nao encontrei o gate da leitura universal.")
            serviceText = serviceText.replaceFirst(anchor, replacement)
        }

        if ("functional_bubble_target_gate_0_1_95" !in serviceText) {
            val homeLine = "            val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)\n"
            val alternativeLine = "            val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)\n"
            if (homeLine !in serviceText || alternativeLine !in serviceText) {
                throw GradleException("Nao encontrei coordenadas Casa/Alfinete para ligar os controles.")
            }
            serviceText = serviceText.replaceFirst(
                homeLine,
                "            val homeCoordinate = if (settings.homeTargetEnabled) settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings) else null // functional_bubble_target_gate_0_1_95\n",
            )
            serviceText = serviceText.replaceFirst(
                alternativeLine,
                "            val alternativeCoordinate = if (settings.alternativeTargetEnabled) settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings) else null\n",
            )
        }

        if ("functional_bubble_grid_0_1_95" !in serviceText) {
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
                setColor(Color.argb(244, 24, 24, 28))
                setStroke(dp(2), Color.argb(230, 255, 255, 255))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))

            addView(quickToggleBubbleButton("Rota", QuickBubbleToggle.Rota, currentSettings.appEnabled))
            addView(quickToggleBubbleButton("Leitura", QuickBubbleToggle.LiveReading, currentSettings.liveReadingEnabled))
            addView(quickToggleBubbleButton("Alertas", QuickBubbleToggle.Alerts, currentSettings.proximityAlertsEnabled))
            addView(quickToggleBubbleButton("Escuro", QuickBubbleToggle.Appearance, currentSettings.bubbleDarkMode))
            addView(quickToggleBubbleButton("Casa", QuickBubbleToggle.HomeTarget, currentSettings.homeTargetEnabled))
            addView(quickToggleBubbleButton("Alfinete", QuickBubbleToggle.AlternativeTarget, currentSettings.alternativeTargetEnabled))
            addView(quickActionBubbleButton("WA") {
                hideActionMenu()
                openWhatsAppFromCurrentScreen()
            })
            addView(quickActionBubbleButton("Salvar") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(quickActionBubbleButton("Acesso") {
                hideActionMenu()
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
            addView(quickActionBubbleButton("Relat.") { openControlCenterTab(TAB_TOOLS) })
            addView(quickActionBubbleButton("Backup") { openControlCenterTab(TAB_CONFIG) })
            addView(quickActionBubbleButton("Fechar") { hideActionMenu() })
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
    } // functional_bubble_grid_0_1_95

    private fun quickToggleBubbleButton(
        label: String,
        toggle: QuickBubbleToggle,
        active: Boolean,
    ): TextView = quickBubbleBase(label, active, showState = true).apply {
        setOnClickListener {
            val updated = QuickBubbleToggleReducer.toggle(currentSettings, toggle)
            currentSettings = updated
            scope.launch { repository.saveSettings(updated) }
            val enabled = quickToggleState(updated, toggle)
            text = quickBubbleText(label, enabled)
            applyQuickBubbleVisual(this, enabled)

            when (toggle) {
                QuickBubbleToggle.Rota,
                QuickBubbleToggle.LiveReading -> {
                    if (!updated.appEnabled || !updated.liveReadingEnabled) {
                        forceClearUniversalResult("Funcao desligada pela central da bolinha.")
                    } else {
                        restartUniversalReadFromQuickToggle()
                    }
                }
                QuickBubbleToggle.HomeTarget,
                QuickBubbleToggle.AlternativeTarget -> {
                    if (!updated.homeTargetEnabled && !updated.alternativeTargetEnabled) {
                        forceClearUniversalResult("Casa e Alfinete estao desligados.")
                    } else {
                        restartUniversalReadFromQuickToggle()
                    }
                }
                QuickBubbleToggle.Appearance -> showOverlay(currentRadarColor, currentDistanceKm)
                QuickBubbleToggle.Alerts -> Unit
            }
        }
    }

    private fun quickActionBubbleButton(label: String, action: () -> Unit): TextView =
        quickBubbleBase(label, active = false, showState = false).apply {
            setOnClickListener {
                applyQuickBubbleVisual(this, true)
                postDelayed({ applyQuickBubbleVisual(this, false) }, 180L)
                action()
            }
        }

    private fun quickBubbleBase(label: String, active: Boolean, showState: Boolean): TextView = TextView(this).apply {
        text = if (showState) quickBubbleText(label, active) else label
        contentDescription = text
        gravity = Gravity.CENTER
        includeFontPadding = false
        textSize = 11.5f
        setTextColor(Color.WHITE)
        setTypeface(Typeface.DEFAULT_BOLD)
        layoutParams = GridLayout.LayoutParams().apply {
            width = dp(72)
            height = dp(72)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        applyQuickBubbleVisual(this, active)
    }

    private fun quickBubbleText(label: String, active: Boolean): String =
        label + "\n" + if (active) "ON" else "OFF"

    private fun quickToggleState(settings: AppSettings, toggle: QuickBubbleToggle): Boolean = when (toggle) {
        QuickBubbleToggle.Rota -> settings.appEnabled
        QuickBubbleToggle.LiveReading -> settings.liveReadingEnabled
        QuickBubbleToggle.Alerts -> settings.proximityAlertsEnabled
        QuickBubbleToggle.Appearance -> settings.bubbleDarkMode
        QuickBubbleToggle.HomeTarget -> settings.homeTargetEnabled
        QuickBubbleToggle.AlternativeTarget -> settings.alternativeTargetEnabled
    }

    private fun restartUniversalReadFromQuickToggle() {
        coreCardAnalysisCoalescer.invalidate()
        lastAnalyzedHash = null
        lastSnapshotHash = null
        scheduleVisibleTextAnalysis(delayMs = 0L)
        requestScreenshotAnalysis()
    }

    private fun applyQuickBubbleVisual(button: TextView, active: Boolean) {
        button.alpha = if (active) 1f else 0.72f
        button.elevation = dp(if (active) 14 else 2).toFloat()
        button.setShadowLayer(
            if (active) dp(9).toFloat() else 0f,
            0f,
            0f,
            if (active) Color.argb(240, 70, 220, 130) else Color.TRANSPARENT,
        )
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Color.rgb(31, 170, 92) else Color.rgb(70, 70, 78))
            setStroke(dp(if (active) 3 else 1), if (active) Color.WHITE else Color.argb(150, 255, 255, 255))
        }
    }

""",
                "grade funcional liga/desliga",
            )
        }

        listOf(
            "functional_bubble_reading_gate_0_1_95",
            "functional_bubble_target_gate_0_1_95",
            "functional_bubble_grid_0_1_95",
            "QuickBubbleToggleReducer.toggle(currentSettings, toggle)",
            "label + \"\\n\" + if (active) \"ON\" else \"OFF\"",
        ).forEach { marker ->
            if (marker !in serviceText) throw GradleException("Bolinhas funcionais incompletas no servico: $marker")
        }
        service.writeText(serviceText)

        val main = mainFile.asFile
        var mainText = main.readText()
        if ("functional_app_bubbles_0_1_95" !in mainText) {
            val oldCall = """                UnifiedAppControlBubbles(
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
"""
            val newCall = """                UnifiedAppControlBubbles(
                    settings = settings,
                    liveEnabled = liveEnabled,
                    onToggle = { toggle ->
                        scope.launch { repository.saveSettings(QuickBubbleToggleReducer.toggle(settings, toggle)) }
                    },
                    onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOpenWhatsApp = { openWhatsAppApp(context) },
                    onOpenSettings = { tab = TAB_CONFIG },
                    onOpenTools = { tab = TAB_TOOLS },
                )
"""
            if (oldCall !in mainText) throw GradleException("Nao encontrei a chamada antiga da central no app.")
            mainText = mainText.replaceFirst(oldCall, newCall)

            mainText = replaceRegion(
                mainText,
                "@Composable\nprivate fun UnifiedAppControlBubbles(",
                "@Composable\nprivate fun AnalysisScreen(",
                """@Composable
private fun UnifiedAppControlBubbles(
    settings: AppSettings,
    liveEnabled: Boolean,
    onToggle: (QuickBubbleToggle) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTools: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Central de controles", fontWeight = FontWeight.Bold)
        Text(
            "Bolinhas com ON/OFF alternam a funcao a cada toque. As demais executam uma acao imediata.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppControlBubble("Rota", settings.appEnabled) { onToggle(QuickBubbleToggle.Rota) }
            AppControlBubble("Leitura", settings.liveReadingEnabled) { onToggle(QuickBubbleToggle.LiveReading) }
            AppControlBubble("Alertas", settings.proximityAlertsEnabled) { onToggle(QuickBubbleToggle.Alerts) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppControlBubble("Escuro", settings.bubbleDarkMode) { onToggle(QuickBubbleToggle.Appearance) }
            AppControlBubble("Casa", settings.homeTargetEnabled) { onToggle(QuickBubbleToggle.HomeTarget) }
            AppControlBubble("Alfinete", settings.alternativeTargetEnabled) { onToggle(QuickBubbleToggle.AlternativeTarget) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppControlBubble("WA", null, onOpenWhatsApp)
            AppControlBubble("Acesso", if (liveEnabled) true else null, onOpenAccessibility)
            AppControlBubble("Relat.", null, onOpenTools)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppControlBubble("Backup", null, onOpenSettings)
            AppControlBubble("Mais", null, onOpenSettings)
            AppControlBubble("Ferram.", null, onOpenTools)
        }
    }
} // functional_app_bubbles_0_1_95

@Composable
private fun AppControlBubble(
    label: String,
    active: Boolean?,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (active == true) 12.dp else 2.dp,
            pressedElevation = 14.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (active == true) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            if (active == null) label else label + "\n" + if (active) "ON" else "OFF",
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun openWhatsAppApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
    if (launchIntent != null) {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        Toast.makeText(context, "WhatsApp nao encontrado.", Toast.LENGTH_SHORT).show()
    }
}

""",
                "central funcional no aplicativo",
            )
        }

        listOf(
            "functional_app_bubbles_0_1_95",
            "QuickBubbleToggle.LiveReading",
            "if (active == null) label else label + \"\\n\"",
        ).forEach { marker ->
            if (marker !in mainText) throw GradleException("Bolinhas funcionais incompletas no aplicativo: $marker")
        }
        main.writeText(mainText)
    }
}

functionalBubbleTogglesFinal.configure {
    dependsOn("unifiedBubbleCompileFinal")
    mustRunAfter("unifiedBubbleControlCenterFinal", "unifiedBubbleCompileFinal")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(functionalBubbleTogglesFinal)
}
