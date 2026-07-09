val patchFactoryCleanNoFlicker by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"
        val main = mainFile.asFile
        if (!main.exists()) return@doLast
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
            """                failures == 0 -> "Leitura concluida: ${dollar}imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Confira se os prints sao cards de corrida."
                else -> "Leitura concluida: ${dollar}imported modelo(s) importado(s), ${dollar}failures print(s) sem leitura."
""",
            """                failures == 0 -> "Leitura concluida: ${dollar}imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Use recorte do bloco da corrida com tempo, km e enderecos."
                else -> "Leitura concluida: ${dollar}imported modelo(s) importado(s), ${dollar}failures print(s) sem leitura."
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
            templateStatus = "Modelos cadastrados: ${dollar}{cardTemplates.size}"
        }
    }
""",
                """    LaunchedEffect(cardTemplates.size) {
        if (!templateStatus.startsWith("Lendo ")) {
            templateStatus = "Modelos cadastrados: ${dollar}{cardTemplates.size}"
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
}

patchFactoryCleanNoFlicker.configure {
    mustRunAfter("patchBubbleStateReportCompileFix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchFactoryCleanNoFlicker)
}
