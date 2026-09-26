package br.com.mapeiaia.rotacerta

object WhatsAppBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "whatsapp",
        emoji = "🟢",
        label = "WhatsApp da tela",
        displayLabel = "WhatsApp",
        action = BubbleShortcutAction.OpenScreenWhatsApp,
    )
}
