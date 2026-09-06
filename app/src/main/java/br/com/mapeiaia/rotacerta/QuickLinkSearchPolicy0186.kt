package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

object QuickLinkSearchPolicy0186 {
    const val CONTRACT_MARKER = "LOCAL_LINK_SEARCH_0186"

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace("\\s+".toRegex(), " ")

    fun filter(items: List<QuickLink0172>, query: String): List<QuickLink0172> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return items
        return items.filter { item ->
            sequenceOf(item.title, item.description, item.url)
                .map(::normalize)
                .any { normalizedQuery in it }
        }
    }
}


object QuickLinkCapacityPolicy0186 {
    const val MAX_ITEMS = 40

    fun canCreate(currentCount: Int): Boolean = currentCount in 0 until MAX_ITEMS
}
