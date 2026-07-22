package br.com.mapeiaia.rotacerta

object SettingsBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "settings",
        emoji = "⚙️",
        label = "Ajustes",
        action = BubbleShortcutAction.OpenSettings,
        targetGroup = "general",
        targetTab = "config",
    )
}
