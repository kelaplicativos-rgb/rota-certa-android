package br.com.mapeiaia.rotacerta

object StopBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "stop_app",
        emoji = "⏹️",
        label = "Encerrar Rota Certa",
        displayLabel = "Encerrar",
        action = BubbleShortcutAction.StopApplication,
    )
}
