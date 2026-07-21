// Rota Certa 0.1.119
// - transfere as acoes da Central de controle para o popup da bolinha;
// - remove a grade circular da Home;
// - preserva salvar alerta, local, card e alternar leitura;
// - mantem dezesseis modulos independentes no popup flutuante.

fun popup119ReplaceRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente para $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun enforcePopupOnlyControlCenter119(
    mainFile: java.io.File,
    serviceFile: java.io.File,
    catalogFile: java.io.File,
    controllerFile: java.io.File,
) {
    listOf(mainFile, serviceFile, catalogFile, controllerFile).forEach { file ->
        if (!file.exists()) throw GradleException("Arquivo ausente para popup 0.1.119: ${file.name}")
    }

    catalogFile.writeText(
        """package br.com.mapeiaia.rotacerta

enum class BubbleShortcutAction {
    OpenRoute,
    OpenDestination,
    OpenAlerts,
    OpenAppearance,
    OpenPermissions,
    OpenBackup,
    OpenReports,
    OpenScreenWhatsApp,
    OpenCollector,
    ClearClipboard,
    ExportDiagnostic,
    StopApplication,
    CreateAlert,
    CreateSavedPlace,
    SaveRideCard,
    ToggleReading,
    OpenSettings,
}

data class BubbleShortcutSpec(
    val id: String,
    val emoji: String,
    val label: String,
    val action: BubbleShortcutAction,
    val displayLabel: String = label,
    val defaultName: String? = null,
    val targetGroup: String? = null,
    val targetTab: String? = null,
) {
    val displayText: String
        get() = "${'$'}emoji\n${'$'}displayLabel"
}

interface BubbleShortcutModule {
    val spec: BubbleShortcutSpec
}

object RouteBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "route",
        emoji = "⚡",
        label = "Rota",
        action = BubbleShortcutAction.OpenRoute,
        targetGroup = "general",
        targetTab = "analysis",
    )
}

object AlertsManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "alerts",
        emoji = "⚠️",
        label = "Alertas",
        action = BubbleShortcutAction.OpenAlerts,
        targetGroup = "alerts",
        targetTab = "config",
    )
}

object AppearanceBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "appearance",
        emoji = "🎨",
        label = "Aparencia",
        action = BubbleShortcutAction.OpenAppearance,
        displayLabel = "Aparência",
        targetGroup = "appearance",
        targetTab = "config",
    )
}

object PermissionsBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "permissions",
        emoji = "🔐",
        label = "Permissoes",
        action = BubbleShortcutAction.OpenPermissions,
        displayLabel = "Permissão",
        targetGroup = "access",
        targetTab = "config",
    )
}

object BackupBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "backup",
        emoji = "💾",
        label = "Backup",
        action = BubbleShortcutAction.OpenBackup,
        targetGroup = "backup",
        targetTab = "config",
    )
}

object ReportsBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "reports",
        emoji = "📋",
        label = "Relatorios",
        action = BubbleShortcutAction.OpenReports,
        displayLabel = "Relatórios",
        targetGroup = "reports",
        targetTab = "history",
    )
}

object CollectorBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "collector",
        emoji = "🚗",
        label = "Coletor",
        action = BubbleShortcutAction.OpenCollector,
    )
}

object ClearClipboardBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "clear_clipboard",
        emoji = "🧹",
        label = "Limpar area de transferencia",
        action = BubbleShortcutAction.ClearClipboard,
        displayLabel = "Limpar",
    )
}

object DiagnosticBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "diagnostic",
        emoji = "🛠️",
        label = "Depurar",
        action = BubbleShortcutAction.ExportDiagnostic,
    )
}

object BubbleShortcutCatalog {
    val modules: List<BubbleShortcutModule> = listOf(
        RouteBubbleShortcutModule,
        DestinationBubbleShortcutModule,
        AlertsManagementBubbleShortcutModule,
        AppearanceBubbleShortcutModule,
        PermissionsBubbleShortcutModule,
        BackupBubbleShortcutModule,
        ReportsBubbleShortcutModule,
        WhatsAppBubbleShortcutModule,
        CollectorBubbleShortcutModule,
        ClearClipboardBubbleShortcutModule,
        DiagnosticBubbleShortcutModule,
        StopBubbleShortcutModule,
        AlertBubbleShortcutModule,
        SavedPlaceBubbleShortcutModule,
        RideCardBubbleShortcutModule,
        ReadingBubbleShortcutModule,
    )

    fun requireValid() {
        require(modules.size == 16) { "O popup deve conter dezesseis modulos." }
        require(modules.map { it.spec.id }.distinct().size == modules.size) {
            "Cada atalho precisa ter identificador unico."
        }
        require(modules.map { it.spec.action }.distinct().size == modules.size) {
            "Cada recurso precisa executar uma acao propria."
        }
    }
}

// popup_only_catalog_0_1_119
""",
    )

    var controller = controllerFile.readText()
    controller = controller
        .replace("        val menuWidth = dp(206)", "        val menuWidth = dp(194)")
        .replace("        val estimatedMenuHeight = dp(14 + rows * 66)", "        val estimatedMenuHeight = dp(14 + rows * 58)")
        .replace("        textSize = 10f", "        textSize = 9.5f")
        .replace("            width = dp(62)\n            height = dp(62)", "            width = dp(54)\n            height = dp(54)")
    if ("popup_only_shortcut_grid_0_1_119" !in controller) {
        controller += "\n// popup_only_shortcut_grid_0_1_119\n"
    }
    controllerFile.writeText(controller)

    var service = serviceFile.readText()
    if ("import android.content.ClipData" !in service) {
        service = service.replace("import android.content.Context\n", "import android.content.ClipData\nimport android.content.ClipboardManager\nimport android.content.Context\n")
    }
    service = popup119ReplaceRegion(
        source = service,
        startToken = "    private fun executeShortcutModule(spec: BubbleShortcutSpec) {",
        endToken = "    private fun toggleLiveReadingFromBubble() {",
        replacement = """    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        traceEvent("bubble.shortcut.execute id=" + spec.id)
        DiagnosticLogStore.record("bubble_action", "shortcut id=" + spec.id + " label=" + spec.label)
        when (spec.action) {
            BubbleShortcutAction.OpenRoute,
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenAlerts,
            BubbleShortcutAction.OpenAppearance,
            BubbleShortcutAction.OpenPermissions,
            BubbleShortcutAction.OpenBackup,
            BubbleShortcutAction.OpenReports,
            BubbleShortcutAction.OpenSettings,
            -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))

            BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp()
            BubbleShortcutAction.OpenCollector -> openCollectorFromBubble()
            BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()
            BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()
            BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, requireNotNull(spec.defaultName))
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place, requireNotNull(spec.defaultName))
            BubbleShortcutAction.SaveRideCard -> saveCurrentRideCardFromBubble()
            BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()
        }
    }

    private fun openCollectorFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        runCatching {
            startActivity(
                Intent(this, BlaBlaCarCollectorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("Nao foi possivel abrir o Coletor.") }
    }

    private fun clearClipboardFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        toast("Area de transferencia limpa.")
        DiagnosticLogStore.record("bubble_action", "clipboard cleared")
    }

    private fun exportDiagnosticFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        DiagnosticLogStore.record("bubble_action", "diagnostic export requested")
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_HISTORY)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "reports")
                    .putExtra("auto_export_report", true),
            )
        }.onFailure { toast("Nao foi possivel abrir a exportacao do relatorio.") }
    }

""",
        label = "acoes transferidas para o popup",
    )
    if ("popup_only_service_actions_0_1_119" !in service) {
        service += "\n// popup_only_service_actions_0_1_119\n"
    }
    serviceFile.writeText(service)

    var main = mainFile.readText()
    val dashboardStart = main.indexOf("            ProfessionalBubbleDashboard(")
    if (dashboardStart >= 0) {
        val dashboardEndToken = "            ) // professional_bubble_home_0_1_118"
        val dashboardEnd = main.indexOf(dashboardEndToken, dashboardStart)
        if (dashboardEnd < 0) throw GradleException("Fim da Central de controle nao encontrado.")
        val afterDashboard = dashboardEnd + dashboardEndToken.length
        main = main.substring(0, dashboardStart) + main.substring(afterDashboard).removePrefix("\n")
    }

    val highlightedAnchor = "        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)\n"
    if ("auto_export_report_0_1_119" !in main) {
        if (highlightedAnchor !in main) throw GradleException("LaunchedEffect da Home nao encontrado para exportacao automatica.")
        main = main.replaceFirst(
            highlightedAnchor,
            highlightedAnchor + """        if (launchIntent?.getBooleanExtra("auto_export_report", false) == true) {
            DiagnosticLogStore.record("support", "report.export.requested_from_popup")
            supportReportFileCreator.launch("rota-certa-relatorio-completo.txt")
        } // auto_export_report_0_1_119
""",
        )
    }
    if ("popup_only_control_center_0_1_119" !in main) {
        main += "\n// popup_only_control_center_0_1_119\n"
    }
    if ("            ProfessionalBubbleDashboard(" in main) {
        throw GradleException("A grade circular ainda esta sendo exibida na Home.")
    }
    mainFile.writeText(main)

    listOf(
        "modules.size == 16",
        "RouteBubbleShortcutModule",
        "ReportsBubbleShortcutModule",
        "ClearClipboardBubbleShortcutModule",
        "DiagnosticBubbleShortcutModule",
    ).forEach { marker ->
        if (marker !in catalogFile.readText()) throw GradleException("Catalogo 0.1.119 incompleto: $marker")
    }
    listOf(
        "BubbleShortcutAction.OpenCollector -> openCollectorFromBubble()",
        "BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()",
        "BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()",
        "popup_only_service_actions_0_1_119",
    ).forEach { marker ->
        if (marker !in serviceFile.readText()) throw GradleException("Servico 0.1.119 incompleto: $marker")
    }
}

// O leitor universal ja e a ultima etapa do encadeamento legado. Este ajuste
// depende apenas dele para evitar ciclos com as tarefas antigas de compatibilidade.
val popupOnlyControlCenter119 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val catalogFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt")
    val controllerFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt")
    inputs.files(mainFile, serviceFile, catalogFile, controllerFile)
    outputs.upToDateWhen { false }
    dependsOn("universalTwoAddressRuntimeFinal")
    doLast {
        enforcePopupOnlyControlCenter119(
            mainFile.asFile,
            serviceFile.asFile,
            catalogFile.asFile,
            controllerFile.asFile,
        )
    }
}

popupOnlyControlCenter119.configure {
    mustRunAfter("universalTwoAddressRuntimeFinal")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupOnlyControlCenter119)
}
