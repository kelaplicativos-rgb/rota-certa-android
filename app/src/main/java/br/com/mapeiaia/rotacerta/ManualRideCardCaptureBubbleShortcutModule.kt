package br.com.mapeiaia.rotacerta

object ManualRideCardCaptureBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "manual_card_capture",
        emoji = "📸",
        label = "Capturar card agora",
        displayLabel = "Capturar card",
        action = BubbleShortcutAction.SaveRideCard,
    )
}
