package br.com.mapeiaia.rotacerta

enum class BubbleShortcutAction {
    CopyTripConfirmation,
    CopyPassengerValue,
    OpenFinance,
    OpenQuickReplies,
    OpenQuickLinks,
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
    ClearClipboard,
    OpenMessageTemplates,
    ExportDiagnostic,
    StopApplication,
    CreateAlert,
    CreateSavedPlace,
    ToggleReading,
    OpenSettings,
    CaptureCurrentAppAndScreen,
    OpenAuthorizedAppsAndCards,
    OpenTextCorrection,
    SaveScreenPrint,
}

enum class BubbleShortcutQuickAction {
    CopyAllVisibleText,
    CreateQuickReply,
    CreateRadarAtCurrentLocation,
    CreateNamedAlertAtCurrentLocation,
    CreateNamedSavedPlaceAtCurrentLocation,
    DefineDestinationAtCurrentLocation,
    CaptureCurrentAppAndScreen,
    OpenPrimaryQuickLink,
    ClearApplicationCache,
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
    val doubleTapAction: BubbleShortcutQuickAction? = null,
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
        doubleTapAction = BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation,
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
        doubleTapAction = BubbleShortcutQuickAction.CreateNamedSavedPlaceAtCurrentLocation,
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
        doubleTapAction = BubbleShortcutQuickAction.CreateRadarAtCurrentLocation,
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


object PassengerValueBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "passenger_value",
        emoji = "💰",
        label = "Valor",
        action = BubbleShortcutAction.CopyPassengerValue,
    )
}

object FinanceBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "finance",
        emoji = "📊",
        label = "Financeiro",
        action = BubbleShortcutAction.OpenFinance,
    )
}

object ClearClipboardBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "clear_clipboard",
        emoji = "🧹",
        label = "Limpar area de transferencia",
        action = BubbleShortcutAction.ClearClipboard,
        displayLabel = "Limpar",
        doubleTapAction = BubbleShortcutQuickAction.ClearApplicationCache,
    )
}

object QuickLinksBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "quick_links",
        emoji = "🔗",
        label = "Links rápidos",
        action = BubbleShortcutAction.OpenQuickLinks,
        displayLabel = "Links",
        doubleTapAction = BubbleShortcutQuickAction.OpenPrimaryQuickLink,
    )
}

object TextCorrectionBubbleShortcutModule0186 : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "text_correction",
        emoji = "✍️",
        label = "Correção de texto",
        action = BubbleShortcutAction.OpenTextCorrection,
        displayLabel = "Corretor",
        targetGroup = "general",
        targetTab = "config",
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


object QuickRepliesBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "quick_replies",
        emoji = "💬",
        label = "Respostas rápidas",
        action = BubbleShortcutAction.OpenQuickReplies,
        displayLabel = "Respostas",
        doubleTapAction = BubbleShortcutQuickAction.CreateQuickReply,
    )
}

object CaptureCurrentAppScreenBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "manual_capture",
        emoji = "📸",
        label = "Capturar aplicativo e tela",
        displayLabel = "Capturar",
        action = BubbleShortcutAction.CaptureCurrentAppAndScreen,
        doubleTapAction = BubbleShortcutQuickAction.CaptureCurrentAppAndScreen,
    )
}


object PrintBubbleShortcutModuleStage32 : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "print",
        emoji = "▣",
        label = "Print",
        displayLabel = "Print",
        action = BubbleShortcutAction.SaveScreenPrint,
    )
}

object ReportsBubbleShortcutModule0184 : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "reports",
        emoji = "📄",
        label = "Relatórios e diagnóstico",
        displayLabel = "Relatórios",
        action = BubbleShortcutAction.OpenReports,
        targetGroup = "reports",
        targetTab = "history",
    )
}

object MessageTemplatesBubbleShortcutModule0184 : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "message_templates",
        emoji = "📝",
        label = "Frases e modelos",
        displayLabel = "Frases",
        action = BubbleShortcutAction.OpenMessageTemplates,
    )
}

object WorkTrackingBubbleShortcutModule0184 : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "work_tracking",
        emoji = "🗺️",
        label = "Rastreamento de trabalho",
        displayLabel = "Rastreamento",
        action = BubbleShortcutAction.OpenSettings,
    )
}

object TripAgendaBubbleShortcutModuleStage47 : BubbleShortcutModule {
    const val CONTRACT_MARKER = "TRIP_AGENDA_SHORTCUT_STAGE47"

    override val spec = BubbleShortcutSpec(
        id = "trip_agenda",
        emoji = "🗓️",
        label = "Agenda de viagens",
        displayLabel = "Viagens",
        action = BubbleShortcutAction.OpenSettings,
        targetGroup = "general",
        targetTab = "config",
    )
}

object BubbleShortcutCatalog {
    val modules: List<BubbleShortcutModule> = listOf(
        RouteBubbleShortcutModule,
        ReadingBubbleShortcutModule,
        DestinationBubbleShortcutModule,
        AlertsManagementBubbleShortcutModule,
        SavedPlacesManagementBubbleShortcutModule,
        RadarsManagementBubbleShortcutModule,
        AppearanceBubbleShortcutModule,
        PermissionsBubbleShortcutModule,
        BackupBubbleShortcutModule,
        WhatsAppBubbleShortcutModule,
        TripConfirmationBubbleShortcutModule,
        PassengerValueBubbleShortcutModule,
        FinanceBubbleShortcutModule,
        ClearClipboardBubbleShortcutModule,
        DiagnosticBubbleShortcutModule,
        QuickRepliesBubbleShortcutModule,
        QuickLinksBubbleShortcutModule,
        TextCorrectionBubbleShortcutModule0186,
        CaptureCurrentAppScreenBubbleShortcutModule,
        PrintBubbleShortcutModuleStage32,
        ReportsBubbleShortcutModule0184,
        MessageTemplatesBubbleShortcutModule0184,
        WorkTrackingBubbleShortcutModule0184,
        TripAgendaBubbleShortcutModuleStage47,
        StopBubbleShortcutModule,
    )

    fun findSpec(id: String?): BubbleShortcutSpec? = modules
        .firstOrNull { it.spec.id == id }
        ?.spec
        ?: ShortcutActionCatalog0184.findSpec(id)

    fun requireValid() {
        require(modules.size >= 22) { "A Home deve conter o catálogo completo de módulos, incluindo Leitura." }
        ShortcutActionCatalog0184.requireValid()
        require(modules.map { it.spec.id }.distinct().size == modules.size) {
            "Cada atalho precisa ter identificador unico."
        }
        val inheritedActionModules = modules.filterNot { it.spec.id == "trip_agenda" }
        require(inheritedActionModules.map { it.spec.action }.distinct().size == inheritedActionModules.size) {
            "Cada recurso herdado precisa executar uma acao propria."
        }
        val tripSpec = modules.singleOrNull { it.spec.id == "trip_agenda" }?.spec
        require(tripSpec?.action == BubbleShortcutAction.OpenSettings) {
            "Agenda deve usar a rota por identidade sem expandir o enum do FAROL."
        }
    }
}

// popup_navigation_catalog_0_1_120

// reading_permission_moved_out_of_popup_checklist_7
