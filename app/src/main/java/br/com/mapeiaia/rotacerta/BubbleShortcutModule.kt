package br.com.mapeiaia.rotacerta

enum class BubbleShortcutAction {
    OpenRoute,
    OpenDestination,
    OpenAlerts,
    OpenSavedPlaces,
    OpenRadars,
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
    OpenCards,
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
        get() = "$emoji\n$displayLabel"
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
        targetTab = "config",
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

object SavedPlacesManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "saved_places",
        emoji = "📍",
        label = "Locais",
        action = BubbleShortcutAction.OpenSavedPlaces,
        targetGroup = "saved_places",
        targetTab = "config",
    )
}

object RadarsManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "radars",
        emoji = "📡",
        label = "Radares",
        action = BubbleShortcutAction.OpenRadars,
        targetGroup = "radars",
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

object CardsManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "cards",
        emoji = "🪪",
        label = "Cards cadastrados",
        action = BubbleShortcutAction.OpenCards,
        displayLabel = "Cards",
        targetGroup = "cards",
        targetTab = "config",
    )
}

object BubbleShortcutCatalog {
    val modules: List<BubbleShortcutModule> = listOf(
        RouteBubbleShortcutModule,
        DestinationBubbleShortcutModule,
        AlertsManagementBubbleShortcutModule,
        SavedPlacesManagementBubbleShortcutModule,
        RadarsManagementBubbleShortcutModule,
        AppearanceBubbleShortcutModule,
        PermissionsBubbleShortcutModule,
        BackupBubbleShortcutModule,
        WhatsAppBubbleShortcutModule,
        CollectorBubbleShortcutModule,
        ClearClipboardBubbleShortcutModule,
        DiagnosticBubbleShortcutModule,
        StopBubbleShortcutModule,
        CardsManagementBubbleShortcutModule,
        ReadingBubbleShortcutModule,
    )

    fun requireValid() {
        require(modules.size == 15) { "O popup deve conter quinze modulos." }
        require(modules.map { it.spec.id }.distinct().size == modules.size) {
            "Cada atalho precisa ter identificador unico."
        }
        require(modules.map { it.spec.action }.distinct().size == modules.size) {
            "Cada recurso precisa executar uma acao propria."
        }
    }
}

// popup_navigation_catalog_0_1_120
