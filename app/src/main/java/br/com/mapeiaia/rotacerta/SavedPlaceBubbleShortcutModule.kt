package br.com.mapeiaia.rotacerta

object SavedPlaceBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "saved_place",
        emoji = "📍",
        label = "Salvar local",
        displayLabel = "Local",
        action = BubbleShortcutAction.CreateSavedPlace,
        defaultName = "Local salvo",
        targetGroup = "alerts",
        targetTab = "config",
    )
}
