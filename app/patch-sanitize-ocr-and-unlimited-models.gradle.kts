val sanitizeOcrAndUnlimitedModels by tasks.registering {
    val repositoryFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val parserFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt")
    val matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt")
    val detectorFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideOfferDetector.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(repositoryFile, mainFile, parserFile, matcherFile, detectorFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        repositoryFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            if ("import java.util.Locale" !in text) {
                text = text.replace("import kotlinx.serialization.json.Json\n", "import kotlinx.serialization.json.Json\nimport java.util.Locale\n")
            }
            text = text.replace("monitor99 = prefs[monitor99] ?: true", "monitor99 = prefs[monitor99] ?: false")
            text = text.replace("monitorUber = prefs[monitorUber] ?: true", "monitorUber = prefs[monitorUber] ?: false")
            text = text.replace("monitorInDrive = prefs[monitorInDrive] ?: true", "monitorInDrive = prefs[monitorInDrive] ?: false")
            text = text.replace("prefs[rideCardTemplates] = json.encodeToString(updated.take(30))", "prefs[rideCardTemplates] = json.encodeToString(updated)")
            text = text.replace("prefs[rideCardTemplates] = json.encodeToString(backup.cardTemplates.take(30))", "prefs[rideCardTemplates] = json.encodeToString(backup.cardTemplates)")

            if ("suspend fun addMonitoredPackage(packageName: String?)" !in text) {
                text = text.replace(
"""    suspend fun saveDiagnostic(diagnostic: LiveDiagnostic) {
        context.dataStore.edit { prefs ->
            prefs[liveDiagnostic] = json.encodeToString(diagnostic)
        }
    }

""",
"""    suspend fun saveDiagnostic(diagnostic: LiveDiagnostic) {
        context.dataStore.edit { prefs ->
            prefs[liveDiagnostic] = json.encodeToString(diagnostic)
        }
    }

    suspend fun addMonitoredPackage(packageName: String?) {
        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return
        context.dataStore.edit { prefs ->
            val current = prefs[extraMonitoredPackages].orEmpty()
            prefs[extraMonitoredPackages] = mergePackageList(current, normalizedPackage)
        }
    }

""",
                )
            }

            if ("private fun mergePackageList(" !in text) {
                text = text.replace(
"""    private fun decodeCoordinate(value: String?): Coordinate? =
        runCatching { json.decodeFromString<Coordinate>(value.orEmpty()) }.getOrNull()
}
""",
"""    private fun mergePackageList(value: String, packageName: String): String =
        (value.split(Regex("[,;\\s]+")) + packageName)
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")

    private fun decodeCoordinate(value: String?): Coordinate? =
        runCatching { json.decodeFromString<Coordinate>(value.orEmpty()) }.getOrNull()
}
""",
                )
            }

            if (text != original) file.writeText(text)
        }

        mainFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace(
"""            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
            repository.addCardTemplate(template)
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
"""            val cleanText = RideTextSanitizer.stripRotaCertaOverlay(text)
            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(cleanText)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, cleanText)
            repository.addCardTemplate(template)
            repository.addMonitoredPackage(inferredPackageName)
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
            )
            text = text.replace(
"""            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
            repository.addCardTemplate(template)
            if (!inferredPackageName.isNullOrBlank()) {
                repository.saveSettings(settings.copy(extraMonitoredPackages = mergePackageIntoList(settings.extraMonitoredPackages, inferredPackageName)))
            }
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
"""            val cleanText = RideTextSanitizer.stripRotaCertaOverlay(text)
            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(cleanText)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, cleanText)
            repository.addCardTemplate(template)
            repository.addMonitoredPackage(inferredPackageName)
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
            )
            text = text.replace(
"""                val extractedText = runCatching { ocrService.extractText(uri) }.getOrDefault("")
                val packageName = RideCardTemplateMatcher.inferPackageName(extractedText)
                if (extractedText.isBlank() || packageName == null) {
""",
"""                val extractedText = RideTextSanitizer.stripRotaCertaOverlay(runCatching { ocrService.extractText(uri) }.getOrDefault(""))
                val packageName = RideCardTemplateMatcher.inferPackageName(extractedText)
                if (extractedText.isBlank() || packageName == null) {
""",
            )
            text = text.replace(
"""                    repository.addCardTemplate(template)
                    repository.saveSettings(settings.copy(extraMonitoredPackages = mergePackageIntoList(settings.extraMonitoredPackages, packageName)))
                    imported += 1
""",
"""                    repository.addCardTemplate(template)
                    repository.addMonitoredPackage(packageName)
                    imported += 1
""",
            )
            text = text.replace(
"""                    repository.addCardTemplate(template)
                    imported += 1
""",
"""                    repository.addCardTemplate(template)
                    repository.addMonitoredPackage(packageName)
                    imported += 1
""",
            )
            if (text != original) file.writeText(text)
        }

        parserFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace(
"""        val rawLines = text
            .lines()
            .map { it.normalizeOcrWhitespace().trim() }
""",
"""        val cleanText = RideTextSanitizer.stripRotaCertaOverlay(text).ifBlank { text }
        val rawLines = cleanText
            .lines()
            .map { it.normalizeOcrWhitespace().trim() }
""",
            )
            if (text != original) file.writeText(text)
        }

        matcherFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace(
"""    fun inferPackageName(text: String): String? {
        val normalized = text.normalizedForCardMatch()
""",
"""    fun inferPackageName(text: String): String? {
        val normalized = RideTextSanitizer.stripRotaCertaOverlay(text).normalizedForCardMatch()
""",
            )
            text = text.replace(
"""        val normalizedPackage = packageName?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val features = featuresFor(text).toList().sorted()
""",
"""        val normalizedPackage = packageName?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val cleanText = RideTextSanitizer.stripRotaCertaOverlay(text).ifBlank { text.trim() }
        val features = featuresFor(cleanText).toList().sorted()
""",
            )
            text = text.replace("id = \"card-${'$'}{System.currentTimeMillis()}-${'$'}{text.stableHash()}\"", "id = \"card-${'$'}{System.currentTimeMillis()}-${'$'}{cleanText.stableHash()}\"")
            text = text.replace("sampleHash = text.stableHash()", "sampleHash = cleanText.stableHash()")
            text = text.replace(
"""        val normalizedPackage = packageName?.lowercase(Locale.ROOT)
        val features = featuresFor(text)
""",
"""        val normalizedPackage = packageName?.lowercase(Locale.ROOT)
        val cleanText = RideTextSanitizer.stripRotaCertaOverlay(text).ifBlank { text.trim() }
        val features = featuresFor(cleanText)
""",
            )
            text = text.replace("text = text,\n            normalizedPackage = normalizedPackage", "text = cleanText,\n            normalizedPackage = normalizedPackage")
            text = text.replace(
"""    fun featuresFor(text: String): Set<String> {
        val normalized = text.normalizedForCardMatch()
""",
"""    fun featuresFor(text: String): Set<String> {
        val cleanText = RideTextSanitizer.stripRotaCertaOverlay(text).ifBlank { text.trim() }
        val normalized = cleanText.normalizedForCardMatch()
""",
            )
            text = text.replace("moneyRegex.containsMatchIn(text)", "moneyRegex.containsMatchIn(cleanText)")
            text = text.replace("distanceRegex.containsMatchIn(text)", "distanceRegex.containsMatchIn(cleanText)")
            text = text.replace("timeRegex.containsMatchIn(text)", "timeRegex.containsMatchIn(cleanText)")
            text = text.replace("addressRegex.containsMatchIn(text)", "addressRegex.containsMatchIn(cleanText)")
            text = text.replace("mapMarkerRegex.containsMatchIn(text)", "mapMarkerRegex.containsMatchIn(cleanText)")
            text = text.replace("AdaptiveCardLearningEngine.adaptiveFeaturesFor(text)", "AdaptiveCardLearningEngine.adaptiveFeaturesFor(cleanText)")
            if (text != original) file.writeText(text)
        }

        detectorFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace(
"""    fun looksLikeRideOffer(text: String, fields: RideFields, packageName: String?): Boolean {
        if (!RideScreenTextClassifier.looksLikeRideCard(text)) return false
        if (containsNonRideScreenNoise(text)) return false
""",
"""    fun looksLikeRideOffer(text: String, fields: RideFields, packageName: String?): Boolean {
        val cleanText = RideTextSanitizer.stripRotaCertaOverlay(text).ifBlank { text }
        if (!RideScreenTextClassifier.looksLikeRideCard(cleanText)) return false
        if (containsNonRideScreenNoise(cleanText)) return false
""",
            )
            text = text.replace("val normalized = text.lowercase(Locale.ROOT)", "val normalized = cleanText.lowercase(Locale.ROOT)")
            text = text.replace("containsMatchIn(text)", "containsMatchIn(cleanText)")
            if ("salvar card desta corrida" !in text) {
                text = text.replace("\"salvar card de corrida\",", "\"salvar card de corrida\",\n        \"salvar card desta corrida\",")
            }
            if ("minha região de corridas" !in text) {
                text = text.replace("\"salvar este local\",", "\"salvar este local\",\n        \"minha região de corridas\",\n        \"minha regiao de corridas\",")
            }
            if (text != original) file.writeText(text)
        }

        serviceFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace(
"""            val text = (cachedText ?: mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }).trim()
""",
"""            val rawText = (cachedText ?: mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }).trim()
            val text = RideTextSanitizer.stripRotaCertaOverlay(rawText).trim()
""",
            )
            text = text.replace(
"""        val trimmed = text.trim()
        if (trimmed.isBlank()) return
""",
"""        val trimmed = RideTextSanitizer.stripRotaCertaOverlay(text).trim()
        if (trimmed.isBlank()) return
""",
            )
            text = text.replace("repository.saveSettings(currentSettings.copy(extraMonitoredPackages = mergePackageIntoList(currentSettings.extraMonitoredPackages, inferredPackage)))", "repository.addMonitoredPackage(inferredPackage)")
            text = text.replace("repository.saveSettings(updatedSettings)", "repository.addMonitoredPackage(inferredPackage)")
            if ("sanitize_ocr_and_unlimited_models.patch_applied" !in text) {
                text = text.replace(
                    "        traceEvent(\"saveable_card_cache.patch_applied=true\")\n",
                    "        traceEvent(\"saveable_card_cache.patch_applied=true\")\n        traceEvent(\"sanitize_ocr_and_unlimited_models.patch_applied=true\")\n",
                )
            }
            if (text != original) file.writeText(text)
        }
    }
}

sanitizeOcrAndUnlimitedModels.configure {
    mustRunAfter(
        "patchBubbleWarningSaveCard",
        "manualCardPackagesOnly",
        "manualCardLearningMode",
        "clearBubbleOnScreenChange",
        "cardLifecycleStrictOverlay",
        "finalKmAndStrictRideCard",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(sanitizeOcrAndUnlimitedModels)
}
