package br.com.mapeiaia.rotacerta.trips

/** Bounded Set helper kept local to the script executor persistence layer. */
internal fun <T> Set<T>.takeLast(count: Int): List<T> =
    if (count <= 0) emptyList() else toList().takeLast(count)
