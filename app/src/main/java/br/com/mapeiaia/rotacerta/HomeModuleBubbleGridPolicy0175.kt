package br.com.mapeiaia.rotacerta

object HomeModuleBubbleGridPolicy0175 {
    const val CONTRACT_MARKER = "HOME_MODULE_BUBBLE_GRID_0175"
    const val COLUMNS = 3

    fun <T> rows(items: List<T>): List<List<T>> = items.chunked(COLUMNS)

    fun expandedIdInRow(rowIds: List<String>, expandedId: String?): String? =
        expandedId?.takeIf(rowIds::contains)
}
