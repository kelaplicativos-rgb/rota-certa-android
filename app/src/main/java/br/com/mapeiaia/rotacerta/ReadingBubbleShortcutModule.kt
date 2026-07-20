package br.com.mapeiaia.rotacerta

object ReadingBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "reading",
        emoji = "👁",
        label = "Leitura",
        action = BubbleShortcutAction.OpenReading,
        targetGroup = "reading",
        targetTab = "config",
    )
}
