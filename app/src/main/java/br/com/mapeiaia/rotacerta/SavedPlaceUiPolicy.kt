package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/** Regras puras da lista de locais e alertas. */
object SavedPlaceUiPolicy {
    fun initialDraftName(place: SavedPlace, highlighted: Boolean): String {
        if (place.type == SavedPlaceType.Place && highlighted && isLegacyPlaceholder(place.name)) return ""
        return place.name
    }

    fun canSave(place: SavedPlace, draftName: String): Boolean =
        draftName.trim().isNotBlank() || place.type == SavedPlaceType.ProximityAlert

    fun sortedByName(items: List<SavedPlace>): List<SavedPlace> = items.sortedWith(
        compareBy<SavedPlace> { normalizedSortValue(it.name.ifBlank { it.address }) }
            .thenBy { normalizedSortValue(it.address) }
            .thenBy { it.createdAtMillis },
    )

    private fun isLegacyPlaceholder(value: String): Boolean {
        val normalized = normalizedSortValue(value)
        return normalized == "local" || normalized == "local salvo"
    }

    private fun normalizedSortValue(value: String): String = Normalizer
        .normalize(value.trim().lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
}
