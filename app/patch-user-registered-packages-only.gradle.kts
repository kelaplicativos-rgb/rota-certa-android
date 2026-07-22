val enforceUserRegisteredPackagesOnly by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchLiveRideAccessibilityService(serviceFile.asFile)
        patchMainActivity(mainFile.asFile)
    }
}

fun patchLiveRideAccessibilityService(file: java.io.File) {
    var text = file.readText()
    val original = text

    fun replaceExact(target: String, replacement: String) {
        text = text.replace(target, replacement)
    }

    replaceExact(
"""            val packageName = currentWindowPackageName() ?: activePackageName
            val text = mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }
""",
"""            val packageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
            val text = mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }
""",
    )

    replaceExact(
"""            val inferredPackage = packageName?.lowercase(Locale.ROOT)
                ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
"""            if (packageName.isNullOrBlank() || packageName == this@LiveRideAccessibilityService.packageName || isPassiveDiagnosticPackage(packageName)) {
                toast("Abra o card dentro do app de corrida e salve novamente.")
                recordDiagnostic(
                    stage = "bubble_save_card_missing_package",
                    color = currentRadarColor,
                    reason = "Card nao salvo: pacote real do app de corrida nao foi identificado.",
                    text = text,
                )
                return@launch
            }
            val inferredPackage = packageName
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
    )

    replaceExact(
"""        return RideCardTemplateMatcher.inferPackageName(text)
            ?.takeIf { inferred -> shouldScanPackage(inferred) }
""",
"""        return RegisteredRidePackagePolicy.packagesFromTemplates(currentCardTemplates)
            .firstOrNull { registeredPackage ->
                RideCardTemplateMatcher.match(text, registeredPackage, currentCardTemplates) != null
            }
""",
    )

    replaceExact(
"""    private fun selectedRidePackages(settings: AppSettings): Set<String> {
        val packages = mutableSetOf<String>()
        if (settings.monitor99) packages += PACKAGE_99_DRIVER
        if (settings.monitorUber) packages += PACKAGE_UBER_DRIVER
        if (settings.monitorInDrive) packages += PACKAGE_INDRIVE_DRIVER
        packages += settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
        return packages
    }
""",
"""    private fun selectedRidePackages(settings: AppSettings): Set<String> =
        RegisteredRidePackagePolicy.packagesFromTemplates(currentCardTemplates)
""",
    )

    replaceExact(
"""        if (normalized !in selectedRidePackages(currentSettings)) {
            return "Pacote fora dos apps monitorados; bolinha em espera: ${'$'}normalized."
        }
""",
"""        if (normalized !in selectedRidePackages(currentSettings)) {
            return "Pacote sem modelo de card cadastrado pelo usuario; bolinha em espera: ${'$'}normalized."
        }
""",
    )

    if (text != original) file.writeText(text)
}

fun patchMainActivity(file: java.io.File) {
    var text = file.readText()
    val original = text

    fun replaceExact(target: String, replacement: String) {
        text = text.replace(target, replacement)
    }

    replaceExact(
"""            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
""",
"""            val inferredPackageName = RegisteredRidePackagePolicy.normalizePackageName(packageName)
            if (inferredPackageName.isNullOrBlank()) {
                Toast.makeText(context, "Modelo nao cadastrado: pacote do app nao identificado. Salve pelo atalho da bolinha dentro do app de corrida.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
""",
    )

    replaceExact(
"""        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)
""",
"""        MonitoredAppsCard(cardTemplates = cardTemplates)
""",
    )

    replaceExact(
"""private fun MonitoredAppsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ExpandableCard(title = "Apps monitorados", initiallyExpanded = false) {
        Text(
            "Farol ao vivo: verde/vermelho somente quando a tela bater com um card cadastrado manualmente. Telas desconhecidas ficam amarelas e viram amostra.",
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSwitchRow(
            label = "Ler somente apps selecionados",
            checked = settings.restrictToSelectedRideApps,
            onCheckedChange = { onChange(settings.copy(restrictToSelectedRideApps = it)) },
        )
        Text(
            if (settings.restrictToSelectedRideApps) {
                "Modo restrito: a bolinha so analisa os apps marcados abaixo. Outros apps voltam para amarelo."
            } else {
                "Modo livre: a bolinha analisa somente cards cadastrados e ignora telas passivas."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSwitchRow("99 Motorista", settings.monitor99) { onChange(settings.copy(monitor99 = it)) }
        SettingsSwitchRow("Uber Driver", settings.monitorUber) { onChange(settings.copy(monitorUber = it)) }
        SettingsSwitchRow("inDrive", settings.monitorInDrive) { onChange(settings.copy(monitorInDrive = it)) }
        OutlinedTextField(
            value = settings.extraMonitoredPackages,
            onValueChange = { onChange(settings.copy(extraMonitoredPackages = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pacote extra permitido") },
        )
        Text("Use este campo se outro app de motorista nao estiver na lista. Separe varios pacotes por virgula.", style = MaterialTheme.typography.bodySmall)
    }
}
""",
"""private fun MonitoredAppsCard(cardTemplates: List<RideCardTemplate>) {
    val packages = RegisteredRidePackagePolicy.packagesFromTemplates(cardTemplates).toList()
    ExpandableCard(title = "Apps ensinados pelo usuario", initiallyExpanded = false) {
        Text(
            "O Rota Certa nasce sem Uber, 99, inDrive ou qualquer outro app predefinido. A bolinha so analisa pacotes salvos junto com modelos de cards cadastrados manualmente.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (packages.isEmpty()) {
            Text("Nenhum app ensinado ainda. Abra o card no app de corrida e use a bolinha para salvar o modelo.", style = MaterialTheme.typography.bodySmall)
        } else {
            packages.forEach { packageName ->
                Text("Pacote salvo: ${'$'}packageName", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
""",
    )

    if (text != original) file.writeText(text)
}

tasks.named("enforceUserRegisteredPackagesOnly").configure {
    mustRunAfter("unmonitoredScreenshotGuard")
    mustRunAfter("patchStrictBubbleLifecycle")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(enforceUserRegisteredPackagesOnly)
}
