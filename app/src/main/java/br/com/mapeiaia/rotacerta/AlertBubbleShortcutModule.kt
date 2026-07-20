package br.com.mapeiaia.rotacerta

object AlertBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "alert",
        emoji = "⚠️",
        label = "Alerta",
        action = BubbleShortcutAction.CreateAlert,
        defaultName = "Alerta",
        targetGroup = "alerts",
        targetTab = "config",
    )
}
