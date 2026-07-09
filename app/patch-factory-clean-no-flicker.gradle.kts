val patchFactoryCleanNoFlicker by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(mainFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val main = mainFile.asFile
        if (main.exists()) {
            var text = main.readText()
            val original = text

            text = text.replace(
                """                val packageName = RideCardTemplateMatcher.inferPackageName(extractedText)
                if (extractedText.isBlank() || packageName == null) {
                    failures += 1
                } else {
                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText)
                    repository.addCardTemplate(template)
                    imported += 1
                }
""",
                """                val packageName = RideCardTemplateMatcher.packageNameForLearning(null, extractedText)
                if (extractedText.isBlank() || packageName == null) {
                    failures += 1
                } else {
                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText)
                    repository.addCardTemplate(template)
                    imported += 1
                }
""",
            )
            text = text.replace(
                """                failures == 0 -> "Leitura concluida: $imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Confira se os prints sao cards de corrida."
                else -> "Leitura concluida: $imported modelo(s) importado(s), $failures print(s) sem leitura."
""",
                """                failures == 0 -> "Leitura concluida: $imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Use recorte do bloco da corrida com tempo, km e enderecos."
                else -> "Leitura concluida: $imported modelo(s) importado(s), $failures print(s) sem leitura."
""",
            )

            text = text.replace(
                Regex("""\n\s*LaunchedEffect\(Unit\) \{\s*locationPermissionLauncher\.launch\(\s*arrayOf\([\s\S]*?\n\s*\}\n"""),
                "\n",
            )

            if ("factory_clean_0_1_72" !in text) {
                text = text.replace(
                    """    LaunchedEffect(cardTemplates.size) {
        if (!templateStatus.startsWith("Lendo ")) {
            templateStatus = "Modelos cadastrados: ${cardTemplates.size}"
        }
    }
""",
                    """    LaunchedEffect(cardTemplates.size) {
        if (!templateStatus.startsWith("Lendo ")) {
            templateStatus = "Modelos cadastrados: ${cardTemplates.size}"
        }
    }

    LaunchedEffect(settings, cardTemplates.size, savedPlaces.size, radarImportSummary.count) {
        val factoryPrefs = context.getSharedPreferences("rota_certa_factory_guard", Context.MODE_PRIVATE)
        val guardKey = "factory_clean_0_1_72"
        val hasStoredRegionOrUserLocation = settings.homeAddress.isNotBlank() ||
            settings.homeCoordinate != null ||
            settings.alternativeAddress.isNotBlank() ||
            settings.alternativeCoordinate != null
        val hasNoUserCollections = cardTemplates.isEmpty() && savedPlaces.isEmpty() && radarImportSummary.count == 0
        if (!factoryPrefs.getBoolean(guardKey, false) && hasNoUserCollections && hasStoredRegionOrUserLocation) {
            repository.saveSettings(AppSettings())
            region = DeviceRegion()
            templateStatus = "Dados antigos removidos. App zerado para cadastro correto dos cards."
        }
        factoryPrefs.edit().putBoolean(guardKey, true).apply()
    }
""",
                )
            }

            text = text.replace(
                """private fun deviceRegionLabel(region: DeviceRegion): String =
    listOf(region.city, region.country).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Cidade e pais serao detectados pela localizacao." }
""",
                """private fun deviceRegionLabel(region: DeviceRegion): String =
    listOf(region.city, region.country).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Regiao de trabalho nao definida. Use GPS somente quando quiser preencher um endereco." }
""",
            )

            if (text != original) main.writeText(text)
        }

        val service = serviceFile.asFile
        if (service.exists()) {
            var text = service.readText()
            val original = text

            if ("private var lastOverlayVisualAtMillis" !in text) {
                text = text.replace(
                    """    private var currentRadarColor = RadarColor.Idle
    private var currentDistanceKm: Double? = null
""",
                    """    private var currentRadarColor = RadarColor.Idle
    private var currentDistanceKm: Double? = null
    private var lastOverlayVisualAtMillis: Long = 0L
    private var lastOverlayVisualColor: RadarColor? = null
    private var lastOverlayVisualText: String = ""
""",
                )
            }

            text = text.replace(
                Regex("""    private fun showOverlay\(color: RadarColor, distanceKm: Double\? = null\) \{[\s\S]*?\n    private fun formatBubbleDistanceKm"""),
                """    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        val visibleDistanceKm = if (color == RadarColor.Green || color == RadarColor.Red) distanceKm else null
        val visibleText = formatBubbleDistanceKm(visibleDistanceKm)
        val now = System.currentTimeMillis()
        val sameVisual = color == lastOverlayVisualColor && visibleText == lastOverlayVisualText
        if (sameVisual && now - lastOverlayVisualAtMillis < OVERLAY_VISUAL_MIN_INTERVAL_MS) {
            currentRadarColor = color
            currentDistanceKm = visibleDistanceKm
            persistBubbleState()
            return
        }
        val isTransientWaiting = color == RadarColor.Default && lastOverlayVisualColor == RadarColor.Default
        if (isTransientWaiting && now - lastOverlayVisualAtMillis < OVERLAY_WAITING_MIN_INTERVAL_MS) {
            currentRadarColor = color
            currentDistanceKm = visibleDistanceKm
            persistBubbleState()
            return
        }
        currentRadarColor = color
        currentDistanceKm = visibleDistanceKm
        persistBubbleState()
        lastOverlayVisualAtMillis = now
        lastOverlayVisualColor = color
        lastOverlayVisualText = visibleText
        val view = overlayView ?: TextView(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
            newView.includeFontPadding = false
            newView.setTextColor(Color.BLACK)
            newView.setTypeface(Typeface.DEFAULT_BOLD)
            newView.setOnClickListener { openApp() }
            newView.setOnTouchListener(BubbleTouchListener())
            if (!runCatching { manager.addView(newView, params) }.isSuccess) return
            overlayView = newView
            overlayParams = params
        }
        view.text = visibleText
        view.textSize = bubbleTextSizeSp(view.text.toString())
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    }

    private fun formatBubbleDistanceKm""",
            )

            text = text.replace(
                """        const val DECISION_OVERLAY_STICKY_MS = 0L
""",
                """        const val DECISION_OVERLAY_STICKY_MS = 0L
        const val OVERLAY_VISUAL_MIN_INTERVAL_MS = 180L
        const val OVERLAY_WAITING_MIN_INTERVAL_MS = 450L
""",
            )

            if (text != original) service.writeText(text)
        }
    }
}

patchFactoryCleanNoFlicker.configure {
    mustRunAfter("patch-bubble-state-report-compile-fix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchFactoryCleanNoFlicker)
}
