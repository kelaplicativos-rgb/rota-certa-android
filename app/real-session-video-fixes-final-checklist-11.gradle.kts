// Checklist 11 — correções verificadas no vídeo real da versão 0.1.130.

fun patchRepositoryRestoreChecklist11(file: java.io.File) {
    if (!file.exists()) throw GradleException("Repositories.kt ausente no checklist 11.")
    var text = file.readText()
    if ("backup_key_preservation_checklist_11" in text) return

    val old = """    suspend fun restoreBackupJson(content: String): RotaCertaBackup {
        val backup = json.decodeFromString<RotaCertaBackup>(content)
        saveSettings(backup.settings)
        context.dataStore.edit { prefs ->
"""
    val replacement = """    suspend fun restoreBackupJson(content: String): RotaCertaBackup {
        val backup = json.decodeFromString<RotaCertaBackup>(content)
        val currentKeyChecklist11 = settings.first().googleMapsApiKey
        val restoredSettingsChecklist11 = backup.settings.copy(
            googleMapsApiKey = GoogleMapsApiKeyPolicy.valueAfterRestore(
                currentValue = currentKeyChecklist11,
                restoredValue = backup.settings.googleMapsApiKey,
                bundledValue = BuildConfig.GOOGLE_MAPS_API_KEY,
            ),
        )
        saveSettings(restoredSettingsChecklist11) // backup_key_preservation_checklist_11
        context.dataStore.edit { prefs ->
"""
    if (old !in text) throw GradleException("Fluxo de restauração não localizado no checklist 11.")
    text = text.replaceFirst(old, replacement)
    text = text.replaceFirst("        return backup\n    }\n", "        return backup.copy(settings = restoredSettingsChecklist11)\n    }\n")
    file.writeText(text)
}

fun patchMainApiKeyChecklist11(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no checklist 11.")
    var text = file.readText()
    if ("api_key_in_general_controls_checklist_11" in text) return

    val anchor = """    ExpandableCard(title = "Controles gerais", initiallyExpanded = true) {
        SettingsSwitchRow(
"""
    val replacement = """    ExpandableCard(title = "Controles gerais", initiallyExpanded = true) {
        val mapsKeyConfiguredChecklist11 = GoogleMapsApiKeyPolicy.isConfigured(
            settings.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        Text(
            if (mapsKeyConfiguredChecklist11) {
                "Chave Google Maps API: configurada"
            } else {
                "Chave Google Maps API: obrigatória para o farol verde/vermelho"
            },
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = settings.googleMapsApiKey,
            onValueChange = { value -> onChange(settings.copy(googleMapsApiKey = value.trim())) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Chave Google Maps API") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Text(
            if (mapsKeyConfiguredChecklist11) {
                "A rota real está liberada. A chave digitada tem prioridade sobre a chave incluída no aplicativo."
            } else {
                "Sem a chave, o card pode ser reconhecido e fotografado, mas a bolinha permanece amarela e não inventa distância."
            },
            style = MaterialTheme.typography.bodySmall,
        ) // api_key_in_general_controls_checklist_11
        SettingsSwitchRow(
"""
    if (anchor !in text) throw GradleException("Controles gerais não localizados no checklist 11.")
    text = text.replaceFirst(anchor, replacement)

    text = text.replace(
        "ExpandableCard(title = \"Google Maps e ajustes avancados\", initiallyExpanded = false)",
        "ExpandableCard(title = \"Google Maps e ajustes avancados\", initiallyExpanded = !GoogleMapsApiKeyPolicy.isConfigured(draft.googleMapsApiKey, BuildConfig.GOOGLE_MAPS_API_KEY))",
    )
    text = text.replace(
        "Opcional: Google Maps melhora a precisao por rota real. Sem chave, o app usa distancia aproximada quando houver coordenadas confiaveis.",
        "Obrigatória para verde/vermelho e km por rota real. Sem chave, o farol permanece amarelo.",
    )
    file.writeText(text)
}

fun patchMatcherChecklist11(file: java.io.File) {
    if (!file.exists()) throw GradleException("RideCardTemplateMatcher.kt ausente no checklist 11.")
    var text = file.readText()
    if ("real_world_match_policy_checklist_11" in text) return

    val start = text.indexOf("    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {")
    val end = if (start >= 0) text.indexOf("    fun featuresFor(text: String): Set<String>", start) else -1
    if (start < 0 || end <= start) throw GradleException("Matcher de cards não localizado no checklist 11.")

    val replacement = """    fun match(text: String, packageName: String?, templates: List<RideCardTemplate>): RideCardTemplateMatch? {
        val normalizedPackage = packageName?.lowercase(Locale.ROOT)
        val liveFeatures = deterministicFeaturesFor(text)
        if ("card.crop.route_block" !in liveFeatures) return null

        return templates.asSequence()
            .filter { template ->
                template.packageName.isNullOrBlank() ||
                    isUniversalLearnedPackage(template.packageName) ||
                    template.packageName.equals(normalizedPackage, ignoreCase = true)
            }
            .mapNotNull { template ->
                val universalPackage = isUniversalLearnedPackage(template.packageName)
                val evaluation = RealWorldRideCardMatchPolicy.evaluate(
                    requiredFeatures = template.requiredFeatures.toSet(),
                    liveFeatures = liveFeatures,
                    samePackage = template.packageName?.equals(normalizedPackage, ignoreCase = true) == true,
                    universalPackage = universalPackage,
                    learnableLiveCard = looksLikeLearnableRideCard(text),
                )
                if (!evaluation.accepted) return@mapNotNull null
                RideCardTemplateMatch(
                    template = template,
                    score = evaluation.score,
                    matchedFeatures = evaluation.matchedFeatures.toList().sorted(),
                )
            }
            .maxByOrNull(RideCardTemplateMatch::score)
    } // real_world_match_policy_checklist_11

"""
    text = text.substring(0, start) + replacement + text.substring(end)
    file.writeText(text)
}

fun patchServiceChecklist11(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 11.")
    var text = file.readText()
    if ("real_session_video_fixes_complete_checklist_11" in text) return

    val fieldAnchor = "    private var activePackageName: String? = null\n"
    if (fieldAnchor !in text) throw GradleException("Campo de pacote ativo ausente no checklist 11.")
    text = text.replaceFirst(
        fieldAnchor,
        fieldAnchor +
            "    private var recentSelectedRidePackageChecklist11: String? = null\n" +
            "    private var recentSelectedRidePackageAtMillisChecklist11: Long = 0L\n",
    )

    val resolvedAnchor = "        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return\n"
    if (resolvedAnchor in text) {
        text = text.replaceFirst(
            resolvedAnchor,
            """        var resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        val selectedPackagesChecklist11 = SelectedRideAppStore.read(applicationContext)
        val overlayResolvedChecklist11 = SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = resolvedPackage,
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = selectedPackagesChecklist11,
            nowMillis = System.currentTimeMillis(),
        )
        if (overlayResolvedChecklist11 != null) resolvedPackage = overlayResolvedChecklist11
        if (resolvedPackage in selectedPackagesChecklist11) {
            recentSelectedRidePackageChecklist11 = resolvedPackage
            recentSelectedRidePackageAtMillisChecklist11 = System.currentTimeMillis()
        } // selected_overlay_event_bridge_checklist_11
""",
        )
    } else {
        throw GradleException("Resolução de pacote do evento não localizada no checklist 11.")
    }

    val strictStart = text.indexOf("    private fun strictSelectedRootPackageChecklist1(): String?")
    val strictEnd = if (strictStart >= 0) text.indexOf("    private fun hasStrictSelectedRootChecklist1()", strictStart) else -1
    if (strictStart < 0 || strictEnd <= strictStart) throw GradleException("Portaria estrita não localizada no checklist 11.")
    val strictReplacement = """    private fun strictSelectedRootPackageChecklist1(): String? {
        val nowChecklist11 = System.currentTimeMillis()
        val selectedChecklist11 = SelectedRideAppStore.read(applicationContext)
        val resolvedChecklist11 = SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = currentRootPackageName(),
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = selectedChecklist11,
            nowMillis = nowChecklist11,
        ) ?: return null
        if (currentRootPackageName() == resolvedChecklist11) {
            recentSelectedRidePackageChecklist11 = resolvedChecklist11
            recentSelectedRidePackageAtMillisChecklist11 = nowChecklist11
        }
        return resolvedChecklist11.takeIf { shouldScanPackage(it) }
    } // selected_overlay_root_bridge_checklist_11

"""
    text = text.substring(0, strictStart) + strictReplacement + text.substring(strictEnd)

    val windowStart = text.indexOf("    private fun currentWindowPackageName(): String? {")
    val windowEnd = if (windowStart >= 0) text.indexOf("    private fun currentRootPackageName(): String?", windowStart) else -1
    if (windowStart < 0 || windowEnd <= windowStart) throw GradleException("Resolvedor de janela não localizado no checklist 11.")
    val oldWindow = text.substring(windowStart, windowEnd)
    val resolutionReturn = "        return resolution.effectivePackageName\n"
    if (resolutionReturn !in oldWindow) throw GradleException("Retorno do resolvedor de janela ausente no checklist 11.")
    val newWindow = oldWindow.replaceFirst(
        resolutionReturn,
        """        val overlayFallbackChecklist11 = SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = currentRootPackageName(),
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = SelectedRideAppStore.read(applicationContext),
            nowMillis = System.currentTimeMillis(),
        )
        return overlayFallbackChecklist11 ?: resolution.effectivePackageName
""",
    ) + " // selected_overlay_window_bridge_checklist_11\n"
    text = text.substring(0, windowStart) + newWindow + text.substring(windowEnd)

    text = text.replace(
        "if (rootPackage != null && expectedPackage != null && rootPackage != expectedPackage) return \"\"",
        "if (rootPackage != null && expectedPackage != null && rootPackage != expectedPackage && !SelectedRideOverlayWindowPolicy.isTransient(rootPackage)) return \"\" // selected_overlay_tree_bridge_checklist_11",
    )

    val phoneDirect = """        val directTarget = ScreenPhoneLink.findBest(collectVisibleTextForAction())
            ?: ScreenPhoneLink.findBest(mergeRideTexts(lastAccessibilityText, lastOcrText))
"""
    val phoneReplacement = """        val phoneVisibleTextChecklist11 = collectPhoneVisibleTextChecklist11()
        val directTarget = ScreenPhoneLink.findBest(phoneVisibleTextChecklist11)
            ?: ScreenPhoneLink.findBest(mergeRideTexts(phoneVisibleTextChecklist11, mergeRideTexts(lastAccessibilityText, lastOcrText)))
"""
    if (phoneDirect !in text) throw GradleException("Leitura direta de telefone não localizada no checklist 11.")
    text = text.replaceFirst(phoneDirect, phoneReplacement)

    val phoneHelperAnchor = "    private fun capturePhoneAndOpenWhatsApp118() {\n"
    if (phoneHelperAnchor !in text) throw GradleException("Atalho WhatsApp 118 ausente no checklist 11.")
    text = text.replaceFirst(
        phoneHelperAnchor,
        """    private fun collectPhoneVisibleTextChecklist11(): String {
        val root = rootInActiveWindow ?: return ""
        val linesChecklist11 = mutableListOf<String>()
        collectNodeText(root, linesChecklist11)
        return linesChecklist11.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
    } // manual_phone_tree_read_checklist_11

$phoneHelperAnchor""",
    )

    text = text.replaceFirst(
        "        scope.launch {\n            var acquiredScreenshot = false\n",
        "        toast(\"Lendo o telefone da tela...\")\n        scope.launch {\n            var acquiredScreenshot = false\n",
    )

    val oldPhoneBitmap = """                                val buffer = screenshot.hardwareBuffer
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                buffer.close()
                                if (bitmap == null) return@runCatching null
                                try {
                                    ScreenPhoneLink.findBest(ocrService.extractText(bitmap))
                                } finally {
                                    bitmap.recycle()
                                }
"""
    val newPhoneBitmap = """                                val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching null
                                try {
                                    val ocrPhoneTextChecklist11 = ocrService.extractText(bitmap)
                                    ScreenPhoneLink.findBest(
                                        mergeRideTexts(collectPhoneVisibleTextChecklist11(), ocrPhoneTextChecklist11),
                                    )
                                } finally {
                                    bitmap.recycle()
                                }
"""
    if (oldPhoneBitmap !in text) throw GradleException("Conversão antiga do telefone não localizada no checklist 11.")
    text = text.replaceFirst(oldPhoneBitmap, newPhoneBitmap)

    val analyzeAnchor = """        val settings = currentSettings // instant_farol_cached_settings_0_1_124
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

"""
    if (analyzeAnchor !in text) throw GradleException("Análise de rota não localizada no checklist 11.")
    text = text.replaceFirst(
        analyzeAnchor,
        analyzeAnchor + """        val effectiveGoogleMapsApiKeyChecklist11 = GoogleMapsApiKeyPolicy.effective(
            settings.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        if (effectiveGoogleMapsApiKeyChecklist11.isBlank()) {
            rememberBubbleReason(
                "google_maps_api_required",
                "Card reconhecido. Configure a Chave Google Maps API para calcular a rota real.",
            )
            showOverlay(RadarColor.Default, distanceKm = null)
            releaseMatchedCaptureFinalChecklist6(screenHash, addressSignature, generation)
            return
        } // missing_api_stays_yellow_checklist_11

""",
    )
    text = text.replace(
        "apiKey = settings.googleMapsApiKey,",
        "apiKey = effectiveGoogleMapsApiKeyChecklist11, // effective_api_key_checklist_11",
    )

    val processOcrLine = "                                processRideText(ocrText, TextSource.Ocr, allowPopupCandidate = true)\n"
    if (processOcrLine !in text) throw GradleException("Resultado OCR não localizado no checklist 11.")
    text = text.replaceFirst(
        processOcrLine,
        """                                val fullScreenTriggerChecklist11 = withContext(Dispatchers.Default) {
                                    UniversalAddressTrigger.evaluate(ocrText)
                                }
                                val fullScreenFieldsChecklist11 = RideFields(
                                    pickup = fullScreenTriggerChecklist11.pickup,
                                    destination = fullScreenTriggerChecklist11.destination,
                                )
                                processRideText(ocrText, TextSource.Ocr, allowPopupCandidate = true)
                                if (FullScreenRideCapturePolicy.shouldSaveCandidate(
                                        packageSelected = shouldScanPackage(requestedPackage),
                                        automaticCaptureEnabled = automaticRideCaptureStore129.isEnabled(),
                                        text = ocrText,
                                        fields = fullScreenFieldsChecklist11,
                                    )
                                ) {
                                    automaticRideCaptureStore129.saveCard(
                                        bitmap = bitmap,
                                        packageName = requestedPackage,
                                        text = ocrText,
                                        fields = fullScreenFieldsChecklist11,
                                        kind = AutomaticRideCaptureKind.Candidate,
                                    )
                                }
                                bitmap.recycle() // full_screen_ocr_capture_checklist_11
""",
    )

    text += "\n// real_session_video_fixes_complete_checklist_11\n"
    file.writeText(text)
}

fun patchRealSessionChecklist11(root: java.io.File) {
    patchRepositoryRestoreChecklist11(java.io.File(root, "Repositories.kt"))
    patchMainApiKeyChecklist11(java.io.File(root, "MainActivity.kt"))
    patchMatcherChecklist11(java.io.File(root, "RideCardTemplateMatcher.kt"))
    patchServiceChecklist11(java.io.File(root, "LiveRideAccessibilityService.kt"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchRealSessionChecklist11(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        patchRealSessionChecklist11(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
