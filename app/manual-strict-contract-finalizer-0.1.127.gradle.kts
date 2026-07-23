// Rota Certa 0.1.127 — compatibilidade do antigo finalizador estrito.
//
// Este arquivo continua existindo porque tarefas antigas podem aplica-lo pelo nome,
// mas ele NAO transforma modelos de cards em obrigatorios e NAO reduz a frequencia
// da leitura. O contrato vigente e:
// - nenhum aplicativo nasce selecionado;
// - o usuario escolhe manualmente os aplicativos permitidos;
// - nenhum modelo de card nasce cadastrado;
// - modelos de cards sao opcionais;
// - o fallback permanece em 120 ms;
// - o vermelho provisório continua ate obter a rota exata e o km.

fun validateManualOptionalContract127(
    serviceFile: java.io.File,
    mainFile: java.io.File,
) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o contrato opcional 0.1.127.")
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para o contrato opcional 0.1.127.")

    var service = serviceFile.readText()
    var main = mainFile.readText()

    // Compatibilidade defensiva para um checkout que tenha sido parcialmente
    // processado pelo finalizador estrito antigo antes desta versao do arquivo.
    service = service
        .replace(
            "requireRegisteredRideCard = true, // manual_registered_card_required_migration_0_1_127",
            "requireRegisteredRideCard = false, // manual_optional_card_setting_0_1_127",
        )
        .replace(
            Regex("const val SCAN_LOOP_MS = \\d+L(?: \\Q// adaptive_fallback_scan_0_1_127\\E)?"),
            "const val SCAN_LOOP_MS = 120L",
        )

    main = main
        .replace("Modelos de cards obrigatorios", "Modelos de cards opcionais")
        .replace(
            "Nenhum modelo nasce cadastrado. Cadastre pelo menos um print do card de cada aplicativo selecionado; sem correspondencia a bolinha nao calcula rota nem libera verde/vermelho.",
            "Nenhum modelo nasce cadastrado. Use prints somente quando um aplicativo ou formato de card precisar ser ensinado manualmente.",
        )
        .replace(
            "Nenhum modelo cadastrado. A bolinha permanece amarela e nao calcula rota.",
            "Nenhum modelo cadastrado.",
        )
        .replace(
            "Modelos de cards obrigatorios: true; cadastrados somente pelo usuario",
            "Modelos de cards: opcionais; cadastrados somente pelo usuario",
        )
        .replace(
            "Politica de leitura: app escolhido + modelo correspondente + passageiro + pelo menos dois enderecos",
            "Politica de leitura: app escolhido + passageiro + pelo menos dois enderecos",
        )

    // O bloco estrito de portaria por modelo nao pode existir no codigo final.
    // Em checkout limpo ele nunca e criado. Se algum processo concorrente voltar a
    // injeta-lo, o build para em vez de publicar um APK com contrato invertido.
    listOf(
        "manual_registered_card_gate_0_1_127",
        "manual_registered_card_required_migration_0_1_127",
        "manual_apps_and_cards_required_settings_0_1_127",
        "manual_registered_card_freshness_0_1_127",
        "manual.card.gate accepted=false reason=no_template",
        "manual.card.gate accepted=false reason=no_match",
        "Modelos de cards obrigatorios",
    ).forEach { forbidden ->
        if (forbidden in service || forbidden in main) {
            throw GradleException("Contrato estrito proibido ainda presente na 0.1.127: $forbidden")
        }
    }

    listOf(
        "manual_selected_apps_gate_0_1_127",
        "manual_cards_preserved_0_1_127",
        "fast_red_continues_exact_route_0_1_127",
        "const val SCAN_LOOP_MS = 120L",
    ).forEach { required ->
        if (required !in service) throw GradleException("Contrato opcional 0.1.127 ausente no servico: $required")
    }
    listOf(
        "Buscar aplicativos instalados",
        "Nenhum aplicativo vem marcado",
        "Modelos de cards opcionais",
        "Anexar modelos de cards (prints)",
    ).forEach { required ->
        if (required !in main) throw GradleException("Contrato opcional 0.1.127 ausente na interface: $required")
    }

    if ("manual_optional_contract_finalizer_0_1_127" !in service) {
        service += "\n// manual_optional_contract_finalizer_0_1_127\n"
    }
    if ("manual_optional_contract_finalizer_0_1_127" !in main) {
        main += "\n// manual_optional_contract_finalizer_0_1_127\n"
    }

    serviceFile.writeText(service)
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        validateManualOptionalContract127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
