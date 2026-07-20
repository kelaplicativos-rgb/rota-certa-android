package br.com.mapeiaia.rotacerta

object RideCardBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "ride_card",
        emoji = "💾",
        label = "Salvar card",
        action = BubbleShortcutAction.SaveRideCard,
    )
}
