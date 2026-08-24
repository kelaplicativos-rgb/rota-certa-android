package br.com.mapeiaia.rotacerta.trips

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Single URL and navigation-identity authority for every collector flow. */
internal object BlaBlaCollectorUrlModule {
    const val ORIGIN = "https://www.blablacar.com.br"
    private const val HOST = "www.blablacar.com.br"

    fun absolute(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith('/') -> "$ORIGIN$value"
            else -> value
        }
    }

    fun isAllowed(raw: String?): Boolean = parseAllowed(raw) != null

    fun canonical(raw: String?): String {
        val uri = parseAllowed(raw) ?: return ""
        val query = uri.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .filterNot { part -> part.substringBefore('=').equals("search_uuid", ignoreCase = true) }
            .joinToString("&")
        return buildString {
            append(ORIGIN)
            append(uri.rawPath.orEmpty().ifBlank { "/" })
            if (query.isNotBlank()) append('?').append(query)
        }
    }

    fun isPassenger(raw: String?): Boolean {
        val path = parseAllowed(raw)?.path.orEmpty()
        return path.contains("/passenger/", ignoreCase = true) ||
            path.contains("/booking/", ignoreCase = true)
    }

    fun passengerPageKey(raw: String?): String {
        val uri = parseAllowed(raw) ?: return ""
        val path = uri.rawPath.orEmpty().trimEnd('/').ifBlank { "/" }
        return "$ORIGIN$path"
    }

    fun samePassengerPage(expected: String?, actual: String?): Boolean =
        isPassenger(expected) && isPassenger(actual) &&
            passengerPageKey(expected) == passengerPageKey(actual)

    fun passengerIdentityKey(raw: String?): String {
        val uri = parseAllowed(raw) ?: return "passenger"
        val fromPath = Regex("/(?:passenger|booking)/([^/?#]+)", RegexOption.IGNORE_CASE)
            .find(uri.path.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
        return (fromPath ?: queryValue(uri, "id") ?: uri.path.orEmpty().substringAfterLast('/'))
            .take(80)
            .ifBlank { "passenger" }
    }

    fun passengerPage(passengerId: String?, tripId: String?): String? {
        val passenger = passengerId?.trim()?.takeIf(STABLE_EXTERNAL_ID::matches) ?: return null
        val trip = tripId?.trim()?.takeIf(STABLE_EXTERNAL_ID::matches) ?: return null
        return "$ORIGIN/rides/offer/passenger/$passenger/0?id=$trip"
            .takeIf(::isPassenger)
    }

    fun tripId(raw: String?): String? {
        val absolute = absolute(raw)
        if (!isAllowed(absolute)) return null
        return BlaBlaTripIdentity.externalTripIdFromHref(absolute)
    }

    fun isSpecificTrip(raw: String?): Boolean =
        isAllowed(raw) && !isPassenger(raw) && !isEditOrOptions(raw) && tripId(raw) != null

    fun ridesPageMatches(raw: String?): Boolean =
        parseAllowed(raw)?.path.orEmpty().trimEnd('/').equals("/rides", ignoreCase = true)

    fun isEditOrOptions(raw: String?): Boolean {
        val path = parseAllowed(raw)?.path.orEmpty().trimEnd('/').lowercase()
        return path == "/rides/offer/edit" || path.startsWith("/rides/offer/edit/")
    }

    fun editTripId(raw: String?): String? = Regex(
        "/rides/offer/edit/([^/?#]+)(?:$|[/?#])",
        RegexOption.IGNORE_CASE,
    ).find(parseAllowed(raw)?.path.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)

    fun optionsTripId(raw: String?): String? = Regex(
        "/rides/offer/edit/([^/?#]+)/options(?:$|[/?#])",
        RegexOption.IGNORE_CASE,
    ).find(parseAllowed(raw)?.path.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)

    fun sameNavigation(left: String?, right: String?): Boolean {
        val first = navigationIdentity(left) ?: return false
        val second = navigationIdentity(right) ?: return false
        return first == second
    }

    fun sanitizeForLog(raw: String?): String {
        val uri = parseAllowed(raw)
        return if (uri == null) {
            absolute(raw).substringBefore('?').substringBefore('#').take(240)
        } else {
            "$ORIGIN${uri.rawPath.orEmpty().ifBlank { "/" }}".take(240)
        }
    }

    private fun navigationIdentity(raw: String?): NavigationIdentity? {
        val uri = parseAllowed(raw) ?: return null
        return NavigationIdentity(
            path = uri.path.orEmpty().trimEnd('/').lowercase(),
            strongId = queryValue(uri, "id").orEmpty(),
        )
    }

    private fun queryValue(uri: URI, expectedKey: String): String? = uri.rawQuery.orEmpty()
        .split('&')
        .firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=', missingDelimiterValue = "")
            if (!key.equals(expectedKey, ignoreCase = true)) return@firstNotNullOfOrNull null
            runCatching {
                URLDecoder.decode(
                    part.substringAfter('=', missingDelimiterValue = ""),
                    StandardCharsets.UTF_8.name(),
                ).trim()
            }.getOrNull()?.takeIf(String::isNotEmpty)
        }

    private fun parseAllowed(raw: String?): URI? = runCatching {
        URI(absolute(raw))
    }.getOrNull()?.takeIf { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            uri.rawUserInfo == null &&
            uri.port in setOf(-1, 443)
    }

    private data class NavigationIdentity(
        val path: String,
        val strongId: String,
    )

    private val STABLE_EXTERNAL_ID = Regex("[A-Za-z0-9_-]{8,160}")
}
