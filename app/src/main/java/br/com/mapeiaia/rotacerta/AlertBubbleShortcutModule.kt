package br.com.mapeiaia.rotacerta

object AlertBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "alert",
        emoji = "⚠️",
        label = "Salvar alerta",
        displayLabel = "Alerta",
        action = BubbleShortcutAction.CreateAlert,
        defaultName = "Alerta",
        targetGroup = "alerts",
        targetTab = "config",
    )
}
