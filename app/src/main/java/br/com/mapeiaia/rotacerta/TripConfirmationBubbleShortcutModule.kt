package br.com.mapeiaia.rotacerta

object TripConfirmationBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "copy_trip_confirmation",
        emoji = "📋",
        label = "Copiar confirmação da viagem",
        displayLabel = "Copiar viagem",
        action = BubbleShortcutAction.CopyTripConfirmation,
    )
}
