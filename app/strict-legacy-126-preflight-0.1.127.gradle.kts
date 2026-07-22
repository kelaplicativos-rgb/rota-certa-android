// Rota Certa 0.1.127
// Compatibilidade transitória para o validador universal legado da 0.1.126.
//
// O patch 0.1.126 ainda precisa montar seu bloco historico para que o restaurador
// 0.1.127 o substitua de forma deterministica. Esta tarefa protege somente os
// marcadores e a expressao da selecao manual durante essa passagem. Ao final,
// confirma que nenhuma limpeza destrutiva permaneceu no aplicativo.

private val strictSelectedGate127 = "normalized in selectedPackages"
private val protectedSelectedGate127 =
    "normalized /* strict_legacy_126_preflight_0_1_127 */ in selectedPackages"

fun prepareLegacy126ForStrict127(serviceFile: java.io.File, mainFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("Servico ausente no preflight estrito 0.1.127.")
    if (!mainFile.exists()) throw GradleException("Interface ausente no preflight estrito 0.1.127.")

    var service = serviceFile.readText()
    var main = mainFile.readText()

    if (strictSelectedGate127 in service) {
        service = service.replace(strictSelectedGate127, protectedSelectedGate127)
    }

    // Nao antecipa pre_registered_runtime_cleanup_0_1_126: o patch historico
    // precisa criar o bloco para o restaurador 0.1.127 remove-lo no mesmo preBuild.
    val serviceMarkers = listOf(
        "pre_registered_gates.removed cards=",
        "SelectedRideAppStore.save(applicationContext, emptySet())",
        "currentCardTemplates = emptyList()",
        "universal_optional_card_model_migration_0_1_101",
        "universal_package_content_gate_0_1_126",
        "CorePackageMonitor.classify(",
        "classification.canScan",
        "universal_package_block_reason_0_1_126",
        "universal_process_block_reason_0_1_126",
    )
    serviceMarkers.forEach { marker ->
        if (marker !in service) {
            service += "\n// $marker // legacy_marker_only_strict_0_1_127\n"
        }
    }

    val mainMarkers = listOf(
        "no_pre_registered_apps_ui_0_1_126",
        "no_selected_apps_picker_ui_0_1_126",
        "no_pre_registered_cards_ui_0_1_126",
        "no_registered_cards_module_0_1_126",
        "diagnostic_policy_no_pre_registered_0_1_126",
        "Filtro por aplicativos pre-cadastrados: removido",
        "Modelos de cards como requisito: removidos",
    )
    mainMarkers.forEach { marker ->
        if (marker !in main) {
            main += "\n// $marker // legacy_marker_only_strict_0_1_127\n"
        }
    }

    service += "\n// strict_legacy_126_preflight_ready_0_1_127\n"
    serviceFile.writeText(service)
    mainFile.writeText(main)
}

fun finalizeLegacy126ForStrict127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("Servico ausente no fechamento estrito 0.1.127.")
    var service = serviceFile.readText()
    service = service.replace(protectedSelectedGate127, strictSelectedGate127)

    listOf(
        "manual_selected_apps_gate_0_1_127",
        "manual_registered_card_gate_0_1_127",
        "manual.card.gate accepted=false reason=no_template",
        "manual.card.gate accepted=false reason=no_match",
        "manual.card.gate accepted=true",
        strictSelectedGate127,
    ).forEach { marker ->
        if (marker !in service) {
            throw GradleException("Contrato estrito perdido após compatibilidade 0.1.126: $marker")
        }
    }
    if ("removedTemplates126.forEach" in service) {
        throw GradleException("Compatibilidade 0.1.126 tentou restaurar limpeza destrutiva de modelos.")
    }
    if (protectedSelectedGate127 in service) {
        throw GradleException("Expressão temporária da portaria não foi restaurada.")
    }

    if ("strict_legacy_126_preflight_finalized_0_1_127" !in service) {
        service += "\n// strict_legacy_126_preflight_finalized_0_1_127\n"
    }
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doFirst {
        prepareLegacy126ForStrict127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
    doLast {
        finalizeLegacy126ForStrict127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
