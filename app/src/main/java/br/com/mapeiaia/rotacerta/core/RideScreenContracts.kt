package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideFields

/**
 * Contrato central do Rota Certa Core.
 * A bolinha nao decide cor/km diretamente a partir de texto bruto.
 * Primeiro, a tela precisa ser classificada com seguranca.
 */
enum class RideScreenKind {
    NotRideApp,
    PassiveOverlay,
    RideListing,
    OpenRideCard,
    PartialRideCard,
    UnknownRideScreen,
}

enum class CoreBubbleMode {
    Hidden,
    Waiting,
    Good,
    Bad,
}

data class RideScreenSnapshot(
    val packageName: String?,
    val text: String,
    val fields: RideFields,
)

data class RideScreenClassification(
    val kind: RideScreenKind,
    val packageName: String?,
    val reason: String,
    val confidence: Double = 0.0,
) {
    val canAnalyzeRoute: Boolean get() = true // open_all_screen_classifications_0_1_94
}
