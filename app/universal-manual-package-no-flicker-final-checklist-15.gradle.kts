// Checklist 15 — pacote manual universal, Cards/Aplicativos sincronizados e bolinha idempotente.

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

fun locateKotlinClassChecklist15(packageDir: java.io.File, preferredName: String, classMarker: String): java.io.File {
    val preferred = java.io.File(packageDir, preferredName)
    if (preferred.exists()) return preferred
    return packageDir.listFiles()
        ?.firstOrNull { it.isFile && it.extension == "kt" && classMarker in runCatching { it.readText() }.getOrDefault("") }
        ?: throw GradleException("Classe ausente no checklist 15: $classMarker")
}

fun patchStrictManualPackagePolicy15(file: java.io.File) {
    var text = file.readText()
    text = replaceFunctionChecklist15(
        text,
        "    fun canRead(",
        """    fun canRead(
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
""",
    )
    file.writeText(text)
}

fun patchDisplayStabilityPolicy15(file: java.io.File) {
    var text = file.readText().replace(
        "const val PARTIAL_ABSENCE_CONFIRM_MILLIS = 90L",
        "const val PARTIAL_ABSENCE_CONFIRM_MILLIS = 500L // fixed_absence_window_checklist_15",
    )
    text = replaceFunctionChecklist15(
        text,
        "    fun decide(",
        """    fun decide(
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
        val packageChanged = previousPackageName != null && currentPackageName != null &&
            previousPackageName != currentPackageName
        if (hasTwoAddresses) {
            val destinationChanged = activeAddressSignature != null && currentAddressSignature != null &&
                activeAddressSignature != currentAddressSignature
            val sameDestination = activeAddressSignature != null && currentAddressSignature != null &&
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
""",
    )
    file.writeText(text)
}

fun patchUniversalAddressParser15(file: java.io.File) {
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
        throw GradleException("Bloqueio de falso endereco Via app nao aplicado.")
    }
    file.writeText(text)
}

fun patchSelectedAppStore15(file: java.io.File) {
    var text = file.readText()
    if ("selected_package_add_remove_checklist_15" !in text) {
        val anchor = "    fun legacyPackages(settings: AppSettings): Set<String> = buildSet {\n"
        if (anchor !in text) throw GradleException("Ponto de ciclo de pacotes ausente.")
        text = text.replaceFirst(
            anchor,
            """    fun add(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context) + normalized)
    }

    fun remove(context: Context, packageName: String) {
        val normalized = normalize(packageName) ?: return
        save(context, read(context).filterNot { it == normalized }.toSet())
    } // selected_package_add_remove_checklist_15

$anchor""",
        )
    }
    file.writeText(text)
}

fun patchSettingsRepository15(file: java.io.File) {
    var text = file.readText().replace(
        "requireRegisteredRideCard = prefs[requireRegisteredRideCard] ?: true,",
        "requireRegisteredRideCard = prefs[requireRegisteredRideCard] ?: false, // no_predefined_model_gate_checklist_15",
    )
    text = replaceFunctionChecklist15(
        text,
        "    suspend fun addCardTemplate(",
        """    suspend fun addCardTemplate(template: RideCardTemplate) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<RideCardTemplate>>(prefs[rideCardTemplates].orEmpty()) }
                .getOrDefault(emptyList())
            val updated = listOf(template) + current.filterNot { it.id == template.id || it.sampleHash == template.sampleHash }
            prefs[rideCardTemplates] = json.encodeToString(updated.take(30))
        }
        template.packageName?.let { SelectedRideAppStore.add(context, it) }
    } // card_adds_package_checklist_15
""",
    )
    text = replaceFunctionChecklist15(
        text,
        "    suspend fun removeCardTemplate(",
        """    suspend fun removeCardTemplate(templateId: String) {
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
        val updatedSelection = CardPackageLifecyclePolicy.removePackageIfOrphaned(
            selectedPackages = SelectedRideAppStore.read(context),
            packageName = normalized,
            templates = cardTemplates.first(),
            captures = AutomaticRideCaptureStore(context).list(),
        )
        SelectedRideAppStore.save(context, updatedSelection)
    } // last_card_removes_package_checklist_15
""",
    )
    file.writeText(text)
}

fun patchModelsNoPredefined15(file: java.io.File) {
    file.writeText(
        file.readText().replace(
            "val requireRegisteredRideCard: Boolean = true,",
            "val requireRegisteredRideCard: Boolean = false, // no_predefined_card_contract_checklist_15",
        ),
    )
}

fun patchStableService15(file: java.io.File) {
    var service = file.readText()
    service = replaceFunctionChecklist15(
        service,
        "    private fun schedulePartialReadConfirmationChecklist14(",
        """    private fun schedulePartialReadConfirmationChecklist14(
        packageName: String,
        windowId: Int?,
    ) {
        @Suppress("UNUSED_VARIABLE") val ignoredWindowIdChecklist15 = windowId
        if (partialReadConfirmationJobChecklist14?.isActive == true) return
        partialReadConfirmationJobChecklist14 = scope.launch {
            delay(FarolDisplayStabilityPolicy.PARTIAL_ABSENCE_CONFIRM_MILLIS)
            val savedPackagesChecklist14 = SelectedRideAppStore.read(applicationContext)
            val currentPackageChecklist14 = strictSelectedRootPackageChecklist1()
                ?: normalizePackageName(universalResolvedForegroundPackage())?.takeIf { it in savedPackagesChecklist14 }
                ?: return@launch
            if (currentPackageChecklist14 != packageName) return@launch
            val confirmedTextChecklist14 = collectImmediateVisibleTextChecklist13()
            val confirmedEvaluationChecklist14 = withContext(Dispatchers.Default) {
                SimpleSavedAppFarolPolicy.evaluate(packageName, savedPackagesChecklist14, confirmedTextChecklist14)
            }
            partialReadConfirmationJobChecklist14 = null
            if (confirmedEvaluationChecklist14.active) {
                processRideText(confirmedTextChecklist14, TextSource.Accessibility, allowPopupCandidate = true)
            } else {
                hardClearUniversalTwoAddress(
                    reason = "O card saiu da tela; cor e quilometros removidos.",
                    keepWaitingYellow = true,
                )
                scheduleScreenshotFallback127(packageName)
            }
        }
    } // fixed_absence_confirmation_job_checklist_15
""",
    )

    val processStart = service.indexOf("    private suspend fun processRideText(")
    val processEnd = service.indexOf("    private suspend fun analyzeUniversalTwoAddress(", processStart)
    if (processStart < 0 || processEnd <= processStart) throw GradleException("processRideText final ausente.")
    var process = service.substring(processStart, processEnd)
    if ("valid_read_cancels_absence_checklist_15" !in process) {
        val anchor = "        if (source == TextSource.Accessibility) {\n"
        if (anchor !in process) throw GradleException("Ponto de cancelamento da ausencia ausente.")
        process = process.replaceFirst(
            anchor,
            """        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = null // valid_read_cancels_absence_checklist_15
        if (source == TextSource.Accessibility) {
""",
        )
    }
    service = service.substring(0, processStart) + process + service.substring(processEnd)
    service = service.replace(
        """            FarolDisplayStabilityPolicy.Action.KeepCurrent -> {
                scheduleScreenshotFallback127(resolvedPackageChecklist14)
                return
            }""",
        """            FarolDisplayStabilityPolicy.Action.KeepCurrent -> {
                return // same_destination_no_ocr_no_repaint_checklist_15
            }""",
    )
    service = replaceFunctionChecklist15(
        service,
        "    private fun showOverlay(",
        """    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {
        if (!serviceReady) return
        val manager = windowManager ?: return
        val nextTextChecklist15 = formatBubbleDistanceKm(distanceKm)
        val existingViewChecklist15 = overlayView
        if (existingViewChecklist15 != null && currentRadarColor == color &&
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
""",
    )
    file.writeText(service)
}

fun patchCardDeletionUi15(file: java.io.File) {
    var main = file.readText().replace(
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
    val cardStart = main.indexOf("private fun AutomaticRideCaptureCardChecklist6(")
    val cardEnd = main.indexOf("} // capture_card_component_final_checklist_6", cardStart)
    if (cardStart < 0 || cardEnd <= cardStart) throw GradleException("Componente de captura ausente.")
    var card = main.substring(cardStart, cardEnd)
    if ("captureDeleteScopeChecklist15" !in card) {
        card = card.replaceFirst(
            "    val context129 = LocalContext.current\n",
            "    val context129 = LocalContext.current\n    val captureDeleteScopeChecklist15 = rememberCoroutineScope()\n",
        )
    }
    card = card.replace(
        "onClick = { store.delete(capture.id) },",
        """onClick = {
                    captureDeleteScopeChecklist15.launch {
                        if (store.delete(capture.id)) {
                            SettingsRepository(context129).pruneSelectedPackageIfNoCards(capture.packageName)
                        }
                    }
                }, // delete_capture_prunes_package_checklist_15""",
    )
    main = main.substring(0, cardStart) + card + main.substring(cardEnd)
    file.writeText(main)
}

fun patchUniversalManualPackageNoFlicker15(packageDir: java.io.File) {
    patchStrictManualPackagePolicy15(locateKotlinClassChecklist15(packageDir, "StrictSelectedAppReadPolicy.kt", "object StrictSelectedAppReadPolicy"))
    patchDisplayStabilityPolicy15(locateKotlinClassChecklist15(packageDir, "FarolDisplayStabilityPolicy.kt", "object FarolDisplayStabilityPolicy"))
    patchUniversalAddressParser15(locateKotlinClassChecklist15(packageDir, "UniversalScreenAddressParser.kt", "object UniversalScreenAddressParser"))
    patchSelectedAppStore15(locateKotlinClassChecklist15(packageDir, "SelectedRideAppStore.kt", "object SelectedRideAppStore"))
    patchSettingsRepository15(locateKotlinClassChecklist15(packageDir, "SettingsRepository.kt", "class SettingsRepository"))
    patchModelsNoPredefined15(locateKotlinClassChecklist15(packageDir, "Models.kt", "data class AppSettings"))
    patchStableService15(locateKotlinClassChecklist15(packageDir, "LiveRideAccessibilityService.kt", "class LiveRideAccessibilityService"))
    patchCardDeletionUi15(locateKotlinClassChecklist15(packageDir, "MainActivity.kt", "class MainActivity"))
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
