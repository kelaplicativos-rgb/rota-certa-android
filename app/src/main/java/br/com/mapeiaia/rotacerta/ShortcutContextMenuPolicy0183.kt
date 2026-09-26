package br.com.mapeiaia.rotacerta

object ShortcutContextMenuPolicy0183 {
    const val CONTRACT_MARKER = "SHORTCUT_CONTEXT_MENU_0183"

    fun quickActionLabel(
        shortcutId: String,
        quickAction: BubbleShortcutQuickAction?,
    ): String? = when (quickAction) {
        BubbleShortcutQuickAction.CopyAllVisibleText -> "Copiar texto desta tela"
        BubbleShortcutQuickAction.CreateQuickReply -> "Criar resposta rápida"
        BubbleShortcutQuickAction.CreateRadarAtCurrentLocation -> "Criar radar neste local"
        BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation -> "Criar alerta aqui"
        BubbleShortcutQuickAction.CreateNamedSavedPlaceAtCurrentLocation -> "Salvar localização atual"
        BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation ->
            "Usar localização atual como destino"
        BubbleShortcutQuickAction.CaptureCurrentAppAndScreen -> "Capturar aplicativo e tela agora"
        BubbleShortcutQuickAction.OpenPrimaryQuickLink -> "Abrir link principal"
        BubbleShortcutQuickAction.ClearApplicationCache -> "Limpar cache do Rota Certa"
        null -> null
    }.takeUnless { shortcutId == "clear_clipboard" }

    fun primaryActionLabel(shortcutId: String): String = when (shortcutId) {
        "route" -> "Abrir módulo Rota"
        "destination" -> "Abrir módulo Destino"
        "alerts" -> "Abrir módulo Alertas"
        "saved_places" -> "Abrir módulo Locais"
        "radars" -> "Abrir módulo Radares"
        "appearance" -> "Abrir Aparência"
        "backup" -> "Abrir Backup"
        "quick_replies" -> "Abrir Respostas"
        "manual_capture" -> "Abrir aplicativos e cards"
        "collector" -> "Abrir Coletor"
        "diagnostic" -> "Exportar diagnóstico"
        "stop_app" -> "Encerrar Rota Certa"
        "quick_links" -> "Abrir módulo Links rápidos"
        "links" -> "Abrir Links"
        "finance" -> "Abrir Financeiro"
        "whatsapp" -> "Abrir WhatsApp"
        "copy_trip_confirmation" -> "Copiar confirmação da viagem"
        "passenger_value" -> "Capturar valor do passageiro"
        else -> "Executar ação"
    }
}

// contextual_shortcut_menu_0_1_183
