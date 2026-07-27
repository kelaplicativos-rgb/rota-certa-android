package br.com.mapeiaia.rotacerta

object DestinationBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "destination",
        emoji = "🏠",
        label = "Destino",
        action = BubbleShortcutAction.OpenDestination,
        targetGroup = "destination",
        targetTab = "analysis",
        doubleTapAction = BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation,
    )
}
