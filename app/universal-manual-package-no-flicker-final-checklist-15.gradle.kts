// Checklist 15 — pacote manual realmente universal, ciclo Cards/Aplicativos sincronizado
// e bolinha idempotente para eliminar a repintura do mesmo valor.

fun replaceFunctionChecklist15(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao ausente no checklist 15: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 15: $signature")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return source.substring(0, start) + replacement + source.substring(index + 1)
            }
        }
        index += 1
    }
    throw GradleException("Fim da funcao ausente no checklist 15: $signature")
}

fun patchStrictManualPackagePolicy15(file: java.io.File) {
    if (!file.exists()) throw GradleException("StrictSelectedAppReadPolicy.kt ausente no checklist 15.")
    var text = file.readText()
    val replacement = """    fun canRead(
        packageName: String?,
        ownPackageName: String,
        appEnabled: Boolean,
        liveReadingEnabled: Boolean,
        selectedPackages: Set<String>,
        packageAllowedByPlatformPolicy: Boolean,
    ): Boolean {
        if (!appEnabled || !liveReadingEnabled) return false
        val normalizedPackage = normalize(packageName) ?: return false
        val normalizedOwnPackage = normalize(ownPackageName)
        if (normalizedPackage == normalizedOwnPackage) return false
        @Suppress("UNUSED_VARIABLE")
        val ignoredLegacyPlatformClassification = packageAllowedByPlatformPolicy
        val normalizedSelection = selectedPackages.mapNotNull(::normalize).toSet()
        return normalizedPackage in normalizedSelection
    } // manual_package_overrides_legacy_classification_checklist_15
"""
    text = replaceFunctionChecklist15(text, "    fun canRead(", replacement)
    file.writeText(text)
}

fun patchDisplayStabilityPolicy15(file: java.io.File) {
    if (!file.exists()) throw GradleException("FarolDisplayStabilityPolicy.kt ausente no checklist 15.")
    var text = file.readText()
    text = text.replace(
        "const val PARTIAL_ABSENCE_CONFIRM_MILLIS = 90L",
        "const val PARTIAL_ABSENCE_CONFIRM_MILLIS = 500L // fixed_absence_window_checklist_15",
    )
    val replacement = """    fun decide(
        previousPackageName: String?,
        previousWindowId: Int?,
        activeAddressSignature: String?,
        currentPackageName: String?,
        currentWindowId: Int?,
        currentAddressSignature: String?,
        hasTwoAddresses: Boolean,
        eventType: Int,
    ): Action {
        @Suppress("UNUSED_VARIABLE") val ignoredWindowIds = previousWindowId to currentWindowId
        @Suppress("UNUSED_VARIABLE") val ignoredVariableEvent = eventType
        val packageChanged = previousPackageName != null &&
            currentPackageName != null &&
            previousPackageName != currentPackageName

        if (hasTwoAddresses) {
            val destinationChanged = activeAddressSignature != null &&
                currentAddressSignature != null &&
                activeAddressSignature != currentAddressSignature
            val sameDestination = activeAddressSignature != null &&
                currentAddressSignature != null &&
                activeAddressSignature == currentAddressSignature
            return when {
                packageChanged || destinationChanged -> Action.ClearThenProcess
                sameDestination -> Action.KeepCurrent
                else -> Action.ProcessCurrent
            }
        }

        if (packageChanged) return Action.ClearImmediately
        if (activeAddressSignature != null) return Action.ConfirmAbsence
        return Action.KeepCurrent
    } // destination_only_stability_checklist_15
"""
    text = replaceFunctionChecklist15(text, "    fun decide(", replacement)
    file.writeText(text)
}

fun patchUniversalAddressParser15(file: java.io.File) {
    if (!file.exists()) throw GradleException("UniversalScreenAddressParser.kt ausente no checklist 15.")
    var text = file.readText()
    text = text.replace(
        "private val invalidStreetNameWords = setOf(\"de\", \"da\", \"do\", \"das\", \"dos\", \"n\", \"no\", \"numero\", \"número\", \"sn\")",
        "private val invalidStreetNameWords = setOf(\"de\", \"da\", \"do\", \"das\", \"dos\", \"n\", \"no\", \"numero\", \"número\", \"sn\", \"app\", \"aplicativo\", \"web\", \"google\", \"maps\") // no_via_app_false_address_checklist_15",
    )
    text = text.replace(
        "abrir\\s+rota\\s+certa)",
        "abrir\\s+rota\\s+certa|via\\s+(?:app|aplicativo|web|google\\s*maps?))",
    )
    if ("no_via_app_false_address_checklist_15" !in text) {
        throw GradleException("Bloqueio de falso endereco 'Via app' nao aplicado.")
    }
    file.writeText(text)
}

fun patchSelectedAppStore15(file: java.io.File) {
    if (!file.exists()) throw GradleException("SelectedRideAppStore.kt ausente no checklist 15.")
    var text = file.readText()
    if ("selected_package_add_remove_checklist_15" !in text) {
        val anchor = "    fun legacyPackages(settings: AppSettings): Set<String> = buildSet {\n"
        if (anchor !in text) throw GradleException("Ponto de ciclo de pacotes ausente.")
        val helpers = """    fun add(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context) + normalized)
    }

    fun remove(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context).filterNot { it == normalized }.toSet())
    } // selected_package_add_remove_checklist_15

"""
        text = text.replaceFirst(anchor, helpers + anchor)
    }
    file.writeText(text)
}

fun patchSettingsRepository15(file: java.io.File) {
    if (!file.exists()) throw GradleException("SettingsRepository.kt ausente no checklist 15.")
    var text = file.readText()
    text = text.replace(
        "requireRegisteredRideCard = prefs[requireRegisteredRideCard] ?: true,",
        "requireRegisteredRideCard = prefs[requireRegisteredRideCard] ?: false, // no_predefined_model_gate_checklist_15",
    )
    val addReplacement = """    suspend fun addCardTemplate(template: RideCardTemplate) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<RideCardTemplate>>(prefs[rideCardTemplates].orEmpty()) }
                .getOrDefault(emptyList())
            val updated = listOf(template) + current.filterNot { it.id == template.id || it.sampleHash == template.sampleHash }
            prefs[rideCardTemplates] = json.encodeToString(updated.take(30))
        }
        template.packageName?.let { SelectedRideAppStore.add(context, it) }
    } // card_adds_package_checklist_15
"""
    text = replaceFunctionChecklist15(text, "    suspend fun addCardTemplate(", addReplacement)

    val removeReplacement = """    suspend fun removeCardTemplate(templateId: String) {
        var removedPackageName: String? = null
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<RideCardTemplate>>(prefs[rideCardTemplates].orEmpty()) }
                .getOrDefault(emptyList())
            removedPackageName = current.firstOrNull { it.id == templateId }?.packageName
            prefs[rideCardTemplates] = json.encodeToString(current.filterNot { it.id == templateId })
        }
        removedPackageName?.let { pruneSelectedPackageIfNoCards(it) }
    }

    suspend fun pruneSelectedPackageIfNoCards(packageName: String) {
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return
        val templates = cardTemplates.first()
        val captures = AutomaticRideCaptureStore(context).list()
        val updatedSelection = CardPackageLifecyclePolicy.removePackageIfOrphaned(
            selectedPackages = SelectedRideAppStore.read(context),
            packageName = normalized,
            templates = templates,
            captures = captures,
        )
        SelectedRideAppStore.save(context, updatedSelection)
    } // last_card_removes_package_checklist_15
"""
    text = replaceFunctionChecklist15(text, "    suspend fun removeCardTemplate(", removeReplacement)
    file.writeText(text)
}

fun patchModelsNoPredefined15(file: java.io.File) {
    if (!file.exists()) throw GradleException("Models.kt ausente no checklist 15.")
    val text = file.readText().replace(
        "val requireRegisteredRideCard: Boolean = true,",
        "val requireRegisteredRideCard: Boolean = false, // no_predefined_card_contract_checklist_15",
    )
    file.writeText(text)
}

fun patchStableService15(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 15.")
    var service = file.readText()

    val confirmationReplacement = """    private fun schedulePartialReadConfirmationChecklist14(
        packageName: String,
        windowId: Int?,
    ) {
        if (partialReadConfirmationJobChecklist14?.isActive == true) return
        partialReadConfirmationJobChecklist14 = scope.launch {
            delay(FarolDisplayStabilityPolicy.PARTIAL_ABSENCE_CONFIRM_MILLIS)
            val savedPackagesChecklist14 = SelectedRideAppStore.read(applicationContext)
            val currentPackageChecklist14 = strictSelectedRootPackageChecklist1()
                ?: normalizePackageName(universalResolvedForegroundPackage())
                    ?.takeIf { it in savedPackagesChecklist14 }
                ?: return@launch
            if (currentPackageChecklist14 != packageName) return@launch
            val confirmedTextChecklist14 = collectImmediateVisibleTextChecklist13()
            val confirmedEvaluationChecklist14 = withContext(Dispatchers.Default) {
                SimpleSavedAppFarolPolicy.evaluate(
                    packageName = packageName,
                    savedPackages = savedPackagesChecklist14,
                    text = confirmedTextChecklist14,
                )
            }
            partialReadConfirmationJobChecklist14 = null
            if (confirmedEvaluationChecklist14.active) {
                processRideText(
                    confirmedTextChecklist14,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                )
            } else {
                hardClearUniversalTwoAddress(
                    reason = "O card saiu da tela; cor e quilometros removidos.",
                    keepWaitingYellow = true,
                )
                scheduleScreenshotFallback127(packageName)
            }
        }
    } // fixed_absence_confirmation_job_checklist_15
"""
    service = replaceFunctionChecklist15(
        service,
        "    private fun schedulePartialReadConfirmationChecklist14(",
        confirmationReplacement,
    )

    val processStart = service.indexOf("    private suspend fun processRideText(")
    val processEnd = service.indexOf("    private suspend fun analyzeUniversalTwoAddress(", processStart)
    if (processStart < 0 || processEnd <= processStart) throw GradleException("processRideText final ausente no checklist 15.")
    var processRegion = service.substring(processStart, processEnd)
    if ("valid_read_cancels_absence_checklist_15" !in processRegion) {
        val anchor = "        if (source == TextSource.Accessibility) {\n"
        if (anchor !in processRegion) throw GradleException("Ponto de cancelamento da ausencia ausente.")
        processRegion = processRegion.replaceFirst(
            anchor,
            """        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = null // valid_read_cancels_absence_checklist_15
        if (source == TextSource.Accessibility) {
""",
        )
    }
    service = service.substring(0, processStart) + processRegion + service.substring(processEnd)

    service = service.replace(
        """            FarolDisplayStabilityPolicy.Action.KeepCurrent -> {
                scheduleScreenshotFallback127(resolvedPackageChecklist14)
                return
            }""",
        """            FarolDisplayStabilityPolicy.Action.KeepCurrent -> {
                return // same_destination_no_ocr_no_repaint_checklist_15
            }""",
    )

    val overlayReplacement = """    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        val nextTextChecklist15 = formatBubbleDistanceKm(distanceKm)
        val existingViewChecklist15 = overlayView
        if (existingViewChecklist15 != null &&
            currentRadarColor == color &&
            existingViewChecklist15.text.toString() == nextTextChecklist15
        ) {
            currentDistanceKm = distanceKm
            return // overlay_idempotent_same_value_checklist_15
        }
        currentRadarColor = color
        currentDistanceKm = distanceKm
        val view = existingViewChecklist15 ?: TextView(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
            newView.includeFontPadding = false
            newView.setTextColor(Color.BLACK)
            newView.setTypeface(Typeface.DEFAULT_BOLD)
            newView.setOnClickListener { toggleActionMenu() }
            newView.setOnTouchListener(BubbleTouchListener())
            if (!runCatching { manager.addView(newView, params) }.isSuccess) return
            overlayView = newView
            overlayParams = params
        }
        view.text = nextTextChecklist15
        view.textSize = bubbleTextSizeSp(nextTextChecklist15)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    } // no_duplicate_overlay_render_checklist_15
"""
    service = replaceFunctionChecklist15(service, "    private fun showOverlay(", overlayReplacement)
    file.writeText(service)
}

fun patchCardDeletionUi15(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no checklist 15.")
    var main = file.readText()
    main = main.replace(
        "onClick = { store129.clearAll() },",
        """onClick = {
                    val affectedPackagesChecklist15 = captures129.map { it.packageName }.toSet()
                    store129.clearAll()
                    scope129.launch {
                        val repositoryChecklist15 = SettingsRepository(context129)
                        affectedPackagesChecklist15.forEach { repositoryChecklist15.pruneSelectedPackageIfNoCards(it) }
                    }
                }, // clear_captures_prunes_packages_checklist_15""",
    )
    val cardAnchor = "    val context129 = LocalContext.current\n"
    val cardStart = main.indexOf("private fun AutomaticRideCaptureCardChecklist6(")
    if (cardStart < 0) throw GradleException("Componente de captura ausente no checklist 15.")
    val cardEnd = main.indexOf("} // capture_card_component_final_checklist_6", cardStart)
    if (cardEnd < 0) throw GradleException("Fim do componente de captura ausente.")
    var cardRegion = main.substring(cardStart, cardEnd)
    if ("captureDeleteScopeChecklist15" !in cardRegion) {
        cardRegion = cardRegion.replaceFirst(
            cardAnchor,
            cardAnchor + "    val captureDeleteScopeChecklist15 = rememberCoroutineScope()\n",
        )
    }
    cardRegion = cardRegion.replace(
        "onClick = { store.delete(capture.id) },",
        """onClick = {
                    captureDeleteScopeChecklist15.launch {
                        if (store.delete(capture.id)) {
                            SettingsRepository(context129).pruneSelectedPackageIfNoCards(capture.packageName)
                        }
                    }
                }, // delete_capture_prunes_package_checklist_15""",
    )
    main = main.substring(0, cardStart) + cardRegion + main.substring(cardEnd)
    file.writeText(main)
}

fun patchUniversalManualPackageNoFlicker15(root: java.io.File) {
    patchStrictManualPackagePolicy15(java.io.File(root, "StrictSelectedAppReadPolicy.kt"))
    patchDisplayStabilityPolicy15(java.io.File(root, "FarolDisplayStabilityPolicy.kt"))
    patchUniversalAddressParser15(java.io.File(root, "UniversalScreenAddressParser.kt"))
    patchSelectedAppStore15(java.io.File(root, "SelectedRideAppStore.kt"))
    patchSettingsRepository15(java.io.File(root, "SettingsRepository.kt"))
    patchModelsNoPredefined15(java.io.File(root, "Models.kt"))
    patchStableService15(java.io.File(root, "LiveRideAccessibilityService.kt"))
    patchCardDeletionUi15(java.io.File(root, "MainActivity.kt"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchUniversalManualPackageNoFlicker15(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        patchUniversalManualPackageNoFlicker15(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
