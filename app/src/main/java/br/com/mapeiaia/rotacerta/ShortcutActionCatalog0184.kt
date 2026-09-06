package br.com.mapeiaia.rotacerta

/**
 * Catálogo fechado de ações que podem virar bolinhas na grade flutuante.
 * A grade nunca persiste Intent, pacote, comando ou código arbitrário: somente IDs daqui.
 */
object ShortcutActionCatalog0184 {
    const val CONTRACT_MARKER = "SHORTCUT_ACTION_CATALOG_0184"
    const val MAX_ACTIVE_ACTIONS = 32

    private val extraSpecs: List<BubbleShortcutSpec> = listOf(
        BubbleShortcutSpec(
            id = "action_copy_visible_text",
            emoji = "📋",
            label = "Copiar texto visível",
            displayLabel = "Copiar texto",
            action = BubbleShortcutAction.CopyTripConfirmation,
        ),
        BubbleShortcutSpec(
            id = "action_clear_cache",
            emoji = "🗑️",
            label = "Limpar cache do Rota Certa",
            displayLabel = "Limpar cache",
            action = BubbleShortcutAction.ClearClipboard,
        ),
        BubbleShortcutSpec(
            id = "action_create_alert_here",
            emoji = "⚠️",
            label = "Criar alerta neste local",
            displayLabel = "Criar alerta",
            action = BubbleShortcutAction.CreateAlert,
        ),
        BubbleShortcutSpec(
            id = "action_save_place_here",
            emoji = "📌",
            label = "Salvar este local",
            displayLabel = "Salvar local",
            action = BubbleShortcutAction.CreateSavedPlace,
        ),
        BubbleShortcutSpec(
            id = "action_create_radar_here",
            emoji = "📡",
            label = "Criar radar neste local",
            displayLabel = "Criar radar",
            action = BubbleShortcutAction.OpenRadars,
        ),
        BubbleShortcutSpec(
            id = "action_define_destination_here",
            emoji = "🎯",
            label = "Definir destino de trabalho pelo GPS",
            displayLabel = "Definir destino",
            action = BubbleShortcutAction.OpenDestination,
        ),
        BubbleShortcutSpec(
            id = "action_create_backup",
            emoji = "⬆️",
            label = "Criar backup",
            displayLabel = "Criar backup",
            action = BubbleShortcutAction.OpenBackup,
        ),
        BubbleShortcutSpec(
            id = "action_restore_backup",
            emoji = "⬇️",
            label = "Restaurar backup",
            displayLabel = "Restaurar",
            action = BubbleShortcutAction.OpenBackup,
        ),
        BubbleShortcutSpec(
            id = "action_create_quick_reply",
            emoji = "✍️",
            label = "Criar resposta rápida",
            displayLabel = "Nova resposta",
            action = BubbleShortcutAction.OpenQuickReplies,
        ),
        BubbleShortcutSpec(
            id = "action_open_primary_link",
            emoji = "🌐",
            label = "Abrir link principal",
            displayLabel = "Abrir link",
            action = BubbleShortcutAction.OpenQuickLinks,
        ),
        BubbleShortcutSpec(
            id = "action_open_whatsapp_app",
            emoji = "🟢",
            label = "Abrir WhatsApp",
            displayLabel = "WhatsApp",
            action = BubbleShortcutAction.OpenScreenWhatsApp,
        ),
        BubbleShortcutSpec(
            id = "action_open_work_tracking",
            emoji = "🗺️",
            label = "Abrir rastreamento de trabalho",
            displayLabel = "Rastreamento",
            action = BubbleShortcutAction.OpenSettings,
        ),
        BubbleShortcutSpec(
            id = "action_start_work_tracking",
            emoji = "▶️",
            label = "Iniciar rastreamento de trabalho",
            displayLabel = "Iniciar GPS",
            action = BubbleShortcutAction.OpenSettings,
        ),
        BubbleShortcutSpec(
            id = "action_stop_work_tracking",
            emoji = "⏹️",
            label = "Parar rastreamento de trabalho",
            displayLabel = "Parar GPS",
            action = BubbleShortcutAction.OpenSettings,
        ),
    )

    private val extraIdsByModule: Map<String, List<String>> = mapOf(
        "destination" to listOf("action_define_destination_here"),
        "alerts" to listOf("action_create_alert_here"),
        "saved_places" to listOf("action_save_place_here"),
        "radars" to listOf("action_create_radar_here"),
        "backup" to listOf("action_create_backup", "action_restore_backup"),
        "whatsapp" to listOf("action_open_whatsapp_app"),
        "copy_trip_confirmation" to listOf("action_copy_visible_text"),
        "clear_clipboard" to listOf("action_clear_cache"),
        "quick_replies" to listOf("action_create_quick_reply"),
        "quick_links" to listOf("action_open_primary_link"),
        "text_correction" to emptyList(),
        "manual_capture" to listOf("action_copy_visible_text"),
        "work_tracking" to listOf("action_start_work_tracking", "action_stop_work_tracking"),
    )

    val legacyModuleIds: List<String> = listOf(
        "route",
        "destination",
        "alerts",
        "saved_places",
        "radars",
        "appearance",
        "backup",
        "whatsapp",
        "copy_trip_confirmation",
        "passenger_value",
        "finance",
        "clear_clipboard",
        "diagnostic",
        "quick_replies",
        "quick_links",
        "manual_capture",
        "stop_app",
    )

    fun findSpec(id: String?): BubbleShortcutSpec? = extraSpecs.firstOrNull { it.id == id }

    fun allSpecs(): List<BubbleShortcutSpec> =
        (BubbleShortcutCatalog.modules.map { it.spec } + extraSpecs).distinctBy { it.id }

    fun actionsForModule(moduleId: String): List<BubbleShortcutSpec> {
        val moduleSpec = BubbleShortcutCatalog.modules.firstOrNull { it.spec.id == moduleId }?.spec
        val extras = extraIdsByModule[moduleId].orEmpty().mapNotNull(::findSpec)
        return listOfNotNull(moduleSpec) + extras
    }

    fun moduleIdForAction(actionId: String): String? {
        if (BubbleShortcutCatalog.modules.any { it.spec.id == actionId }) return actionId
        return extraIdsByModule.entries.firstOrNull { actionId in it.value }?.key
    }

    fun moduleSpecForAction(actionId: String): BubbleShortcutSpec? =
        moduleIdForAction(actionId)?.let { moduleId ->
            BubbleShortcutCatalog.modules.firstOrNull { it.spec.id == moduleId }?.spec
        }

    fun safeAlternativeSpecs(currentActionId: String): List<BubbleShortcutSpec> =
        allSpecs().filterNot { it.id == currentActionId }

    fun legacyDefaultSpecs(): List<BubbleShortcutSpec> =
        legacyModuleIds.mapNotNull { id -> BubbleShortcutCatalog.modules.firstOrNull { it.spec.id == id }?.spec }

    fun requireValid() {
        val all = allSpecs()
        require(all.size >= BubbleShortcutCatalog.modules.size)
        require(all.size <= 64)
        require(all.map { it.id }.distinct().size == all.size) { "Cada ação precisa de ID único." }
        require(extraSpecs.none { it.id in legacyModuleIds }) { "Ação nova não pode reutilizar ID legado." }
    }
}
