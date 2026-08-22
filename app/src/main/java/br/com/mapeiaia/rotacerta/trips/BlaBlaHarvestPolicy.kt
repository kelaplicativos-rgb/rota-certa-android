package br.com.mapeiaia.rotacerta.trips

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Automatic harvest is intentionally focused on trip/passenger evidence.
 * Published-seat lookup through Editar -> Lugares is not a physical-capacity authority
 * and is kept out of the normal synchronization path for speed and determinism.
 * Manual external seat synchronization remains a separate explicit flow.
 *
 * MHTML is diagnostic/contingency evidence, not a prerequisite for the normal collector.
 * The automatic path should prefer structured DOM evidence and only archive MHTML when an
 * explicit diagnostic flow asks for it.
 */
internal object BlaBlaHarvestPolicy {
    const val AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = false
    const val AUTOMATIC_MHTML_ARCHIVE: Boolean = false
    const val AUTOMATIC_PAGE_SETTLE_MS: Long = 250L
}

/** Pure strong-identity helper shared by watchdog and JVM regression tests. */
internal object BlaBlaHarvestNavigationIdentity {
    fun same(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        val a = parse(left) ?: return false
        val b = parse(right) ?: return false
        return a == b
    }

    fun isEditOrOptionsHref(value: String?): Boolean {
        val parsed = parse(value) ?: return false
        return parsed.path == "/rides/offer/edit" || parsed.path.startsWith("/rides/offer/edit/")
    }

    private fun parse(value: String?): Identity? = runCatching {
        val uri = URI(value ?: return null)
        Identity(
            scheme = uri.scheme.orEmpty().lowercase(),
            host = uri.host.orEmpty().lowercase(),
            path = uri.path.orEmpty().trimEnd('/').lowercase(),
            strongId = strongId(uri.rawQuery.orEmpty()),
        )
    }.getOrNull()?.takeIf { it.scheme == "https" && it.host == "www.blablacar.com.br" }

    private fun strongId(rawQuery: String): String = rawQuery
        .split('&')
        .firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=', missingDelimiterValue = "")
            if (key != "id") return@firstNotNullOfOrNull null
            URLDecoder.decode(part.substringAfter('=', missingDelimiterValue = ""), StandardCharsets.UTF_8.name())
                .trim()
        }
        .orEmpty()

    private data class Identity(
        val scheme: String,
        val host: String,
        val path: String,
        val strongId: String,
    )
}
