package br.com.mapeiaia.rotacerta.core

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Presenter visual puro da bolinha.
 * Ele nao le tela, nao calcula rota e nao decide corrida.
 * Apenas transforma um modo do Core em texto/tamanho para exibicao.
 */
object CoreBubblePresenter {
    fun present(mode: CoreBubbleMode, distanceKm: Double?): CoreBubblePresentation {
        val text = when (mode) {
            CoreBubbleMode.Good,
            CoreBubbleMode.Bad -> formatDistance(distanceKm)
            CoreBubbleMode.Waiting,
            CoreBubbleMode.Hidden -> ""
        }
        return CoreBubblePresentation(
            mode = mode,
            text = text,
            textSizeSp = bubbleTextSizeSp(text),
            shouldShow = true,
        )
    }

    private fun formatDistance(distanceKm: Double?): String = when {
        distanceKm == null -> ""
        distanceKm < 0.0 -> ""
        distanceKm < 1.0 -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
        distanceKm < 100.0 -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
        else -> distanceKm.roundToInt().coerceAtMost(999).toString()
    }

    private fun bubbleTextSizeSp(text: String): Float = when {
        text.isBlank() -> 14f
        text.length <= 1 -> 25f
        text.length <= 2 -> 23f
        text.length <= 3 -> 20f
        text.length <= 4 -> 18f
        else -> 16f
    }
}

data class CoreBubblePresentation(
    val mode: CoreBubbleMode,
    val text: String,
    val textSizeSp: Float,
    val shouldShow: Boolean,
)
