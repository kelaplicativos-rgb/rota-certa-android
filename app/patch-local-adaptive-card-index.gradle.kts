val localAdaptiveCardIndex by tasks.registering {
    val modelFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
    val repositoryFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt")
    val matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt")
    val adaptiveFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/AdaptiveCardLearningEngine.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(modelFile, repositoryFile, matcherFile, adaptiveFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"

        modelFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace("val monitor99: Boolean = true,", "val monitor99: Boolean = false,")
            text = text.replace("val monitorUber: Boolean = true,", "val monitorUber: Boolean = false,")
            text = text.replace("val monitorInDrive: Boolean = true,", "val monitorInDrive: Boolean = false,")
            if (text != original) file.writeText(text)
        }

        repositoryFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace("monitor99 = prefs[monitor99] ?: true", "monitor99 = prefs[monitor99] ?: false")
            text = text.replace("monitorUber = prefs[monitorUber] ?: true", "monitorUber = prefs[monitorUber] ?: false")
            text = text.replace("monitorInDrive = prefs[monitorInDrive] ?: true", "monitorInDrive = prefs[monitorInDrive] ?: false")
            text = text.replace("prefs[rideCardTemplates] = json.encodeToString(updated.take(30))", "prefs[rideCardTemplates] = json.encodeToString(updated)")
            text = text.replace("prefs[rideCardTemplates] = json.encodeToString(backup.cardTemplates.take(30))", "prefs[rideCardTemplates] = json.encodeToString(backup.cardTemplates)")
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
        val normalized = RideTextSanitizer.stripRotaCertaOverlay(text).ifBlank { text }.normalizedForCardMatch()
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
            text = text.replace("id = \"card-${dollar}{System.currentTimeMillis()}-${dollar}{text.stableHash()}\"", "id = \"card-${dollar}{System.currentTimeMillis()}-${dollar}{cleanText.stableHash()}\"")
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

        adaptiveFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace(
"""                if (adaptiveScore < MIN_ADAPTIVE_SCORE) return@mapNotNull null
                if (semanticRequired.isNotEmpty() && semanticMatched == 0) return@mapNotNull null
                match.copy(
""",
"""                if (adaptiveScore < MIN_ADAPTIVE_SCORE) return@mapNotNull null
                val structureMatched = matchedAdaptive.count { feature ->
                    feature.startsWith("adaptive.structure.") ||
                        feature.startsWith("adaptive.role.") ||
                        feature.startsWith("adaptive.pair.") ||
                        feature.startsWith("adaptive.triple.") ||
                        feature.startsWith("adaptive.order.")
                }
                if (semanticRequired.isNotEmpty() && semanticMatched == 0 && structureMatched < MIN_STRUCTURAL_ADAPTIVE_MATCHES) return@mapNotNull null
                if (semanticMatched == 0 && structureMatched < MIN_STRUCTURAL_ADAPTIVE_MATCHES) return@mapNotNull null
                match.copy(
""",
            )
            text = text.replace(
"""    private fun normalizedLines(text: String): List<String> = text
        .lines()
""",
"""    private fun normalizedLines(text: String): List<String> =
        RideTextSanitizer.stripRotaCertaOverlay(text).ifBlank { text }
            .lines()
""",
            )
            text = text.replace("private const val MIN_ADAPTIVE_SCORE = 0.72", "private const val MIN_ADAPTIVE_SCORE = 0.58")
            text = text.replace(
                "private const val ADAPTIVE_MATCH_MARKER = \"adaptive.card_template_match\"",
                "private const val MIN_STRUCTURAL_ADAPTIVE_MATCHES = 2\n    private const val ADAPTIVE_MATCH_MARKER = \"adaptive.local_saved_card_match\"",
            )
            if (text != original) file.writeText(text)
        }

        serviceFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            if ("ocr.overlay_contamination skipped=true" !in text) {
                text = text.replace(
"""        traceEvent("process.start source=${dollar}source package=${dollar}{packageName.orEmpty()} raw_length=${dollar}{text.length}")
        if (!allowPopupCandidate || learningUnmonitoredPackage) {
            rememberSourceText(packageName, source, text)
        } else {
            rememberPopupCandidatePackage(packageName)
        }
""",
"""        traceEvent("process.start source=${dollar}source package=${dollar}{packageName.orEmpty()} raw_length=${dollar}{text.length}")
        if (source == TextSource.Ocr && RideTextSanitizer.containsRotaCertaOverlay(text)) {
            traceEvent("ocr.overlay_contamination skipped=true length=${dollar}{text.length}")
            return
        }
        if (!allowPopupCandidate || learningUnmonitoredPackage) {
            rememberSourceText(packageName, source, text)
        } else {
            rememberPopupCandidatePackage(packageName)
        }
""",
                )
            }
            text = text.replace(
                "matchedFeatures = listOf(\"registered_app_card_match\"),",
                "matchedFeatures = listOf(\"registered_app_card_match\", \"adaptive.local_saved_package_index\"),",
            )
            if ("local_adaptive_card_index.patch_applied" !in text) {
                text = text.replace(
                    "        traceEvent(\"ocr_overlay_contamination_guard.patch_applied=true\")\n",
                    "        traceEvent(\"ocr_overlay_contamination_guard.patch_applied=true\")\n        traceEvent(\"local_adaptive_card_index.patch_applied=true\")\n",
                )
                text = text.replace(
                    "        traceEvent(\"immediate_card_reset_fast_scan.patch_applied=true\")\n",
                    "        traceEvent(\"immediate_card_reset_fast_scan.patch_applied=true\")\n        traceEvent(\"local_adaptive_card_index.patch_applied=true\")\n",
                )
            }
            if (text != original) file.writeText(text)
        }
    }
}

localAdaptiveCardIndex.configure {
    mustRunAfter(
        "sanitizeOcrAndUnlimitedModels",
        "manualCardLearningMode",
        "registeredCardPackageReading",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(localAdaptiveCardIndex)
}
