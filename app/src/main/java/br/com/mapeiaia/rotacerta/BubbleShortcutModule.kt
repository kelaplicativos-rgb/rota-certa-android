package br.com.mapeiaia.rotacerta

enum class BubbleShortcutAction {
    CreateAlert,
    CreateSavedPlace,
    SaveRideCard,
    OpenDestination,
    OpenReading,
    OpenSettings,
}

data class BubbleShortcutSpec(
    val id: String,
    val emoji: String,
    val label: String,
    val action: BubbleShortcutAction,
    val defaultName: String? = null,
    val targetGroup: String? = null,
    val targetTab: String? = null,
) {
    val displayText: String
        get() = "$emoji\n$label"
}

interface BubbleShortcutModule {
    val spec: BubbleShortcutSpec
}

object BubbleShortcutCatalog {
    val modules: List<BubbleShortcutModule> = listOf(
        AlertBubbleShortcutModule,
        SavedPlaceBubbleShortcutModule,
        RideCardBubbleShortcutModule,
        DestinationBubbleShortcutModule,
        ReadingBubbleShortcutModule,
        SettingsBubbleShortcutModule,
    )

    fun requireValid() {
        require(modules.size == 6) { "A grade deve conter seis modulos." }
        require(modules.map { it.spec.id }.distinct().size == modules.size) {
            "Cada atalho precisa ter identificador unico."
        }
    }
}
