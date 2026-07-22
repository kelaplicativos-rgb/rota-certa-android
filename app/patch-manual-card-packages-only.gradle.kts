val manualCardPackagesOnly by tasks.registering {
    val modelsFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(modelsFile, mainFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        modelsFile.asFile.let { file ->
            var text = file.readText()
            val original = text
            text = text.replace("val monitor99: Boolean = true", "val monitor99: Boolean = false")
            text = text.replace("val monitorUber: Boolean = true", "val monitorUber: Boolean = false")
            text = text.replace("val monitorInDrive: Boolean = true", "val monitorInDrive: Boolean = false")
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
"""            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
            repository.addCardTemplate(template)
            if (!inferredPackageName.isNullOrBlank()) {
                repository.saveSettings(settings.copy(extraMonitoredPackages = mergePackageIntoList(settings.extraMonitoredPackages, inferredPackageName)))
            }
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
            )

            text = text.replace(
"""                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText)
                    repository.addCardTemplate(template)
                    imported += 1
""",
"""                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText)
                    repository.addCardTemplate(template)
                    repository.saveSettings(settings.copy(extraMonitoredPackages = mergePackageIntoList(settings.extraMonitoredPackages, packageName)))
                    imported += 1
""",
            )

            text = text.replace(
"""        SettingsSwitchRow("99 Motorista", settings.monitor99) { onChange(settings.copy(monitor99 = it)) }
        SettingsSwitchRow("Uber Driver", settings.monitorUber) { onChange(settings.copy(monitorUber = it)) }
        SettingsSwitchRow("inDrive", settings.monitorInDrive) { onChange(settings.copy(monitorInDrive = it)) }
        OutlinedTextField(
            value = settings.extraMonitoredPackages,
            onValueChange = { onChange(settings.copy(extraMonitoredPackages = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pacote extra permitido") },
        )
        Text("Use este campo se outro app de motorista nao estiver na lista. Separe varios pacotes por virgula.", style = MaterialTheme.typography.bodySmall)
""",
"""        val learnedPackages = settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (learnedPackages.isEmpty()) {
            Text("Nenhum app monitorado ainda. Anexe ou salve um modelo de card para o pacote do app ser aprendido automaticamente.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Pacotes aprendidos pelos modelos:", fontWeight = FontWeight.Bold)
            learnedPackages.forEach { packageName ->
                Text(packageName, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = settings.extraMonitoredPackages,
            onValueChange = { onChange(settings.copy(extraMonitoredPackages = normalizePackageList(it))) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pacotes aprendidos / pacote manual") },
        )
        Text("O app nasce sem 99, Uber ou inDrive marcados. Cada pacote entra aqui quando voce cadastra um modelo de card.", style = MaterialTheme.typography.bodySmall)
""",
            )

            if ("private fun normalizePackageList(" !in text) {
                text = text.replace(
"""
private fun clearClipboard(context: Context) {
""",
"""
private fun normalizePackageList(value: String): String = value
    .split(Regex("[,;\\s]+"))
    .map { it.trim().lowercase(Locale.ROOT) }
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(",")

private fun mergePackageIntoList(value: String, packageName: String?): String {
    val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        ?: return normalizePackageList(value)
    return normalizePackageList(listOf(value, normalizedPackage).joinToString(","))
}

private fun clearClipboard(context: Context) {
""",
                )
            }

            if ("manual_card_packages_only.patch_applied" !in text) {
                text = text.replace(
                    "        Text(\"Toque na bolinha para abrir o Rota Certa. Arraste para mudar a posicao.\", style = MaterialTheme.typography.bodySmall)\n",
                    "        Text(\"Toque na bolinha para abrir o Rota Certa. Arraste para mudar a posicao.\", style = MaterialTheme.typography.bodySmall)\n        Text(\"manual_card_packages_only.patch_applied=true\", style = MaterialTheme.typography.bodySmall)\n",
                )
            }

            if (text != original) file.writeText(text)
        }

        serviceFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
            repository.addCardTemplate(template)
""",
"""            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
            repository.addCardTemplate(template)
            if (!inferredPackage.isNullOrBlank()) {
                repository.saveSettings(currentSettings.copy(extraMonitoredPackages = mergePackageIntoList(currentSettings.extraMonitoredPackages, inferredPackage)))
            }
""",
            )

            text = text.replace(
"""        if (settings.monitor99) packages += PACKAGE_99_DRIVER
        if (settings.monitorUber) packages += PACKAGE_UBER_DRIVER
        if (settings.monitorInDrive) packages += PACKAGE_INDRIVE_DRIVER
        packages += settings.extraMonitoredPackages
""",
"""        packages += settings.extraMonitoredPackages
""",
            )

            if ("private fun normalizePackageList(" !in text) {
                text = text.replace(
"""
    private fun selectedRidePackages(settings: AppSettings): Set<String> {
""",
"""
    private fun normalizePackageList(value: String): String = value
        .split(Regex("[,;\\s]+"))
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")

    private fun mergePackageIntoList(value: String, packageName: String?): String {
        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: return normalizePackageList(value)
        return normalizePackageList(listOf(value, normalizedPackage).joinToString(","))
    }

    private fun selectedRidePackages(settings: AppSettings): Set<String> {
""",
                )
            }

            if ("manual_card_packages_only.patch_applied" !in text) {
                text = text.replace(
                    "        traceEvent(\"final_km_and_strict_ride_card.patch_applied=true\")\n",
                    "        traceEvent(\"final_km_and_strict_ride_card.patch_applied=true\")\n        traceEvent(\"manual_card_packages_only.patch_applied=true\")\n",
                )
            }

            if (text != original) file.writeText(text)
        }
    }
}

manualCardPackagesOnly.configure {
    mustRunAfter(
        "finalKmAndStrictRideCard",
        "cardLifecycleStrictOverlay",
        "bubbleRegionShortcutFlow",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(manualCardPackagesOnly)
}
