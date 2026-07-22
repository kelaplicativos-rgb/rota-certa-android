fun replacePrivateFunctionBlockStrictPackage(
    source: String,
    functionName: String,
    replacement: String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val strictCardPackageLearning by tasks.registering {
    val matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(matcherFile, serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        matcherFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""        return if (normalizedSource == null || isBlockedLearningSourcePackage(normalizedSource)) {
            UNIVERSAL_LEARNED_PACKAGE
        } else {
            normalizedSource
        }
""",
"""        return if (normalizedSource == null || isBlockedLearningSourcePackage(normalizedSource)) {
            null
        } else {
            normalizedSource
        }
""",
            )

            text = text.replace(
"""                template.packageName.isNullOrBlank() ||
                    isUniversalLearnedPackage(template.packageName) ||
                    template.packageName.equals(normalizedPackage, ignoreCase = true)
""",
"""                template.packageName.isNullOrBlank() ||
                    template.packageName.equals(normalizedPackage, ignoreCase = true)
""",
            )

            text = text.replace(
"""                if (universalPackage) {
                    looksLikeLearnableRideCard(text) &&
                        cropOk &&
                        match.score >= UNIVERSAL_MIN_SCORE &&
                        match.matchedFeatures.size >= required.size.coerceAtMost(UNIVERSAL_MIN_FEATURES).coerceAtLeast(MIN_FEATURES)
                } else {
                    samePackage &&
                        cropOk &&
                        (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                        match.score >= MIN_SCORE &&
                        match.matchedFeatures.size >= MIN_FEATURES
                }
""",
"""                samePackage &&
                    !universalPackage &&
                    cropOk &&
                    (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                    match.score >= MIN_SCORE &&
                    match.matchedFeatures.size >= MIN_FEATURES
""",
            )

            if (text != original) file.writeText(text)
        }

        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""            val inferredPackage = packageName?.lowercase(Locale.ROOT)
                ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
"""            val candidatePackage = packageName
                ?.lowercase(Locale.ROOT)
                ?.takeIf { shouldScanPackage(it) }
                ?: activePackageName
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf { shouldScanPackage(it) }
                ?: lastTextPackageName
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf { shouldScanPackage(it) }
            val inferredPackage = RideCardTemplateMatcher.packageNameForLearning(candidatePackage, text)
            if (inferredPackage == null) {
                toast("Abra o card dentro do app de corrida monitorado e tente salvar novamente.")
                traceEvent("card.learning blocked missing_real_ride_package source=${'$'}{candidatePackage.orEmpty()} text_len=${'$'}{text.length}")
                recordDiagnostic(
                    stage = "bubble_save_card_missing_package",
                    color = currentRadarColor,
                    reason = "Card nao salvo: pacote real do app de corrida nao foi identificado.",
                    text = text,
                )
                return@launch
            }
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
            )

            if (text != original) file.writeText(text)
        }

        mainFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
            repository.addCardTemplate(template)
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
"""            val inferredPackageName = RideCardTemplateMatcher.packageNameForLearning(packageName, text)
            if (inferredPackageName == null) {
                Toast.makeText(
                    context,
                    "Nao salvei: abra o card no app de corrida para gravar o pacote real.",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
            repository.addCardTemplate(template)
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
            )

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
                "importado(s), \$failures print(s) sem leitura.",
                "importado(s), \$failures print(s) sem pacote real do app.",
            )
            text = text.replace(
                "Nenhum modelo importado. Confira se os prints sao cards de corrida.",
                "Nenhum modelo importado. Abra/salve pelo app de corrida para gravar o pacote real.",
            )

            if (text != original) file.writeText(text)
        }
    }
}

strictCardPackageLearning.configure {
    mustRunAfter("globalLightDiagnostics")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(strictCardPackageLearning)
}
