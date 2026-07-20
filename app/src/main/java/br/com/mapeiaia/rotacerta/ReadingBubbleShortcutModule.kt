package br.com.mapeiaia.rotacerta

object ReadingBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "reading",
        emoji = "👁",
        label = "Leitura",
        action = BubbleShortcutAction.ToggleReading,
        targetGroup = "access",
        targetTab = "config",
    )
}
