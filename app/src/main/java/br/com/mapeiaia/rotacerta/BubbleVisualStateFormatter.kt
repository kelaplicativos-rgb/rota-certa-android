package br.com.mapeiaia.rotacerta

import java.util.Locale
import kotlin.math.roundToInt

object BubbleVisualStateFormatter {
    fun formatDistanceKm(distanceKm: Double?): String = when {
        distanceKm == null -> ""
        distanceKm < 1.0 -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
        else -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
    }.take(4).trimEnd(',')

    fun textSizeSp(text: String): Float = when {
        text.isBlank() -> 14f
        text.length <= 1 -> 25f
        text.length <= 2 -> 23f
        text.length <= 3 -> 20f
        else -> 18f
    }

    fun roundedDistanceForDiagnostics(distanceKm: Double?): String =
        distanceKm?.let { String.format(Locale("pt", "BR"), "%.1fkm", it) }.orEmpty()
}
