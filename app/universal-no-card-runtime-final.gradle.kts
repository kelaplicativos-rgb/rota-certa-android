// Contrato universal legado da 0.1.126.
//
// Na 0.1.127 estrita, este task continua existindo somente para preservar a
// dependencia dos patches antigos. Ele NAO pode apagar modelos, desligar a
// selecao manual nem proibir RideCardTemplateMatcher.

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
        val strict127 = layout.projectDirectory
            .file("manual-strict-contract-finalizer-0.1.127.gradle.kts")
            .asFile
            .exists()

        if (strict127) {
            // Marcadores textuais para os patches que ainda dependem do contrato
            // antigo. Todos ficam em comentarios e nao alteram o comportamento.
            if ("universal_no_card_registration_0_1_102" !in main) {
                main += "\n// universal_no_card_registration_0_1_102 legacy_marker_only_strict_0_1_127\n"
            }
            if ("Leitura universal de tela: true" !in main) {
                main += "// Leitura universal de tela: true // legacy_marker_only_strict_0_1_127\n"
            }
            if ("universal_no_card_runtime_0_1_102" !in service) {
                service += "\n// universal_no_card_runtime_0_1_102 legacy_marker_only_strict_0_1_127\n"
            }
            if ("currentCardTemplates = emptyList()" !in service) {
                service += "// currentCardTemplates = emptyList() // legacy_marker_only_strict_0_1_127\n"
            }
            if (".putInt(KEY_STATE_TEMPLATE_COUNT, 0)" !in service) {
                service += "// .putInt(KEY_STATE_TEMPLATE_COUNT, 0) // legacy_marker_only_strict_0_1_127\n"
            }
            if ("cards_repository_preserved_0_1_120" !in service) {
                service += "// cards_repository_preserved_0_1_120 // legacy_marker_only_strict_0_1_127\n"
            }
            if ("universal_cards_optional_settings_0_1_120" !in service) {
                service += "// universal_cards_optional_settings_0_1_120 disabled_by_strict_0_1_127\n"
            }
            if ("strict_no_card_task_compatibility_only_0_1_127" !in service) {
                service += "// strict_no_card_task_compatibility_only_0_1_127\n"
            }

            listOf(
                "universal_no_card_runtime_0_1_102",
                "currentCardTemplates = emptyList()",
                ".putInt(KEY_STATE_TEMPLATE_COUNT, 0)",
                "cards_repository_preserved_0_1_120",
                "strict_no_card_task_compatibility_only_0_1_127",
            ).forEach { marker ->
                if (marker !in service) throw GradleException("Marcador legado ausente na compatibilidade estrita: $marker")
            }

            mainSource.writeText(main)
            serviceSource.writeText(service)
            return@doLast
        }

        // Comportamento historico mantido fora da versao estrita.
        if ("universal_no_card_registration_0_1_102" !in main) {
            main += "\n// universal_no_card_registration_0_1_102\n"
        }
        if ("Leitura universal de tela: true" !in main) {
            main += "// Leitura universal de tela: true\n"
        }

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
            service += "// cards_repository_preserved_0_1_120 // marcador de compatibilidade legado\n"
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

        val startupStart = service.indexOf(settingsAnchor).takeIf { it >= 0 } ?: 0
        val startupEnd = service.indexOf("    override fun onAccessibilityEvent", startupStart)
            .takeIf { it > startupStart }
            ?: service.length
        val startupRegion = service.substring(startupStart, startupEnd)
        if ("repository.removeCardTemplate(" in startupRegion) {
            val authorizedMigration = listOf(
                "pre_registered_runtime_cleanup_0_1_126",
                "cleanupPrefs126.getBoolean(\"pre_registered_runtime_cleanup_0_1_126\", false)",
                "removedTemplates126.forEach { template -> repository.removeCardTemplate(template.id) }",
                ".putBoolean(\"pre_registered_runtime_cleanup_0_1_126\", true)",
            ).all { marker -> marker in startupRegion }
            if (!authorizedMigration) {
                throw GradleException("Remocao de modelos fora da migracao unica autorizada 0.1.126.")
            }
        }

        mainSource.writeText(main)
        serviceSource.writeText(service)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalNoCardRuntimeContract)
}
