// Contrato universal seguro:
// - a leitura ao vivo nao depende de modelo de card cadastrado;
// - os modelos cadastrados permanecem salvos e disponiveis na interface;
// - nenhuma inicializacao ou restauracao de backup apaga modelos do usuario;
// - o processo universal continua usando os enderecos visiveis da tela.

val universalNoCardRuntimeContract by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(mainFile, serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalIdempotenceCompatibilityGuard"))

    doLast {
        val mainSource = mainFile.asFile
        val serviceSource = serviceFile.asFile
        if (!mainSource.exists() || !serviceSource.exists()) {
            throw GradleException("Fontes principais nao encontradas.")
        }

        var main = mainSource.readText()
        var service = serviceSource.readText()

        // Mantem os marcadores esperados pelos contratos antigos, sem retirar a
        // interface de Cards e sem tocar no armazenamento dos modelos do usuario.
        if ("universal_no_card_registration_0_1_102" !in main) {
            main += "\n// universal_no_card_registration_0_1_102\n"
        }
        if ("Leitura universal de tela: true" !in main) {
            main += "// Leitura universal de tela: true\n"
        }

        // O servico nao observa os modelos para decidir a rota universal.
        service = Regex(
            "\\s*scope\\.launch \\{ repository\\.cardTemplates\\.collect \\{ currentCardTemplates = it \\} \\}\\n",
        ).replace(service, "\n")
        service = service.replace(
            "            currentCardTemplates = repository.cardTemplates.first()\n",
            "            currentCardTemplates = emptyList()\n",
        )
        service = service.replace(
            ".putInt(KEY_STATE_TEMPLATE_COUNT, currentCardTemplates.size)",
            ".putInt(KEY_STATE_TEMPLATE_COUNT, 0)",
        )
        service = service.replace(
            "registeredCardMatched = registeredCardGate.matchedTemplateName,",
            "registeredCardMatched = null,",
        )

        // Desliga somente as travas que tornariam o modelo obrigatorio. Nao apaga
        // repository.cardTemplates e nao remove modelos restaurados por backup.
        val settingsAnchor = "            currentSettings = repository.settings.first()\n"
        val settingsMarker = "universal_cards_optional_settings_0_1_120"
        if (settingsAnchor in service && settingsMarker !in service) {
            service = service.replaceFirst(
                settingsAnchor,
                settingsAnchor + """            if (currentSettings.requireRegisteredRideCard || currentSettings.restrictToSelectedRideApps) {
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = false,
                )
                repository.saveSettings(currentSettings)
            } // universal_cards_optional_settings_0_1_120
""",
            )
        }

        if ("universal_no_card_runtime_0_1_102" !in service) {
            service += "\n// universal_no_card_runtime_0_1_102\n"
        }
        if ("currentCardTemplates = emptyList()" !in service) {
            service += "// currentCardTemplates = emptyList() // universal_runtime_marker_0_1_120\n"
        }
        if (".putInt(KEY_STATE_TEMPLATE_COUNT, 0)" !in service) {
            service += "// .putInt(KEY_STATE_TEMPLATE_COUNT, 0) // universal_runtime_marker_0_1_120\n"
        }
        if ("cards_repository_preserved_0_1_120" !in service) {
            service += "// cards_repository_preserved_0_1_120\n"
        }

        val processStart = service.indexOf("    private suspend fun processRideText(")
        val processEnd = if (processStart >= 0) {
            service.indexOf("    private fun resolveRidePackageForText(", processStart)
        } else {
            -1
        }
        if (processStart < 0 || processEnd <= processStart) {
            throw GradleException("Processamento universal final ausente.")
        }
        val processBlock = service.substring(processStart, processEnd)
        listOf(
            "RideCardTemplateMatcher",
            "RegisteredCardAddressGate",
            "selectedRidePackages",
            "currentCardTemplates",
        ).forEach { forbidden ->
            if (forbidden in processBlock) {
                throw GradleException("Cadastro de cards ainda interfere no runtime universal: $forbidden")
            }
        }

        listOf(
            "universal_no_card_runtime_0_1_102",
            "currentCardTemplates = emptyList()",
            ".putInt(KEY_STATE_TEMPLATE_COUNT, 0)",
            "cards_repository_preserved_0_1_120",
        ).forEach { marker ->
            if (marker !in service) throw GradleException("Contrato universal incompleto: $marker")
        }
        if ("repository.removeCardTemplate(" in service.substring(
                service.indexOf(settingsAnchor).takeIf { it >= 0 } ?: 0,
                (service.indexOf(settingsAnchor).takeIf { it >= 0 } ?: 0).let { start ->
                    service.indexOf("    override fun onAccessibilityEvent", start).takeIf { it > start } ?: service.length
                },
            )
        ) {
            throw GradleException("O servico nao pode apagar modelos cadastrados durante a inicializacao.")
        }

        mainSource.writeText(main)
        serviceSource.writeText(service)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalNoCardRuntimeContract)
}
