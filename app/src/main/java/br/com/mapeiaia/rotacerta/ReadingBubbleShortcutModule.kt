package br.com.mapeiaia.rotacerta

object ReadingBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "reading",
        emoji = "👁",
        label = "Leitura do Farol",
        displayLabel = "Leitura",
        action = BubbleShortcutAction.ToggleReading,
        targetGroup = "general",
        targetTab = "config",
    )
}
