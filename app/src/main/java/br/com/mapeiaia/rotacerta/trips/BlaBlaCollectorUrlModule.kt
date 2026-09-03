package br.com.mapeiaia.rotacerta.trips

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Single URL and navigation-identity authority for every collector flow. */
internal object BlaBlaCollectorUrlModule {
    /**
     * Fallback origin only for legacy relative URLs captured by the Brazilian browser flow.
     * Absolute URLs keep their own verified official BlaBlaCar market host.
     */
    const val ORIGIN = "https://www.blablacar.com.br"

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
            append("https://").append(uri.host.lowercase())
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
        return "https://${uri.host.lowercase()}$path"
    }

    fun origin(raw: String?): String? =
        parseAllowed(raw)?.host?.lowercase()?.let { "https://$it" }

    internal fun isOfficialBlaBlaHost(host: String?): Boolean {
        val labels = host.orEmpty().trim().trim('.').lowercase().split('.').filter(String::isNotBlank)
        val root = if (labels.firstOrNull() == "www") labels.drop(1) else labels
        if (root.firstOrNull() != "blablacar") return false
        val suffix = root.drop(1)
        return when (suffix.size) {
            1 -> suffix[0] == "com" || suffix[0].length == 2
            2 -> suffix[0] in setOf("com", "co") && suffix[1].length == 2
            else -> false
        }
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

    /** Passenger-facing exact trip URL. Administrative /rides/offer URLs are rejected. */
    fun publicTrip(raw: String?, expectedTripId: String? = null): String? {
        val value = canonical(raw).takeIf(String::isNotBlank) ?: return null
        val uri = parseAllowed(value) ?: return null
        val path = uri.path.orEmpty().trimEnd('/').lowercase()
        if (path != "/trip" && !path.startsWith("/trip/")) return null
        val actualTripId = tripId(value)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val expected = expectedTripId?.trim()?.takeIf(String::isNotEmpty)
        if (expected != null && actualTripId != expected) return null
        return value
    }

    /**
     * Structured network responses may authoritatively bind an administrative trip id to a
     * passenger-facing permalink whose public token is different. This is the only path where
     * an id mismatch is accepted, and the returned value is always HTTPS.
     */
    fun publicTripFromAuthoritativeNetwork(
        raw: String?,
        expectedAdministrativeTripId: String?,
        boundAdministrativeTripId: String?,
    ): String? {
        val expected = expectedAdministrativeTripId?.trim()?.takeIf(STABLE_EXTERNAL_ID::matches) ?: return null
        val bound = boundAdministrativeTripId?.trim()?.takeIf(STABLE_EXTERNAL_ID::matches) ?: return null
        if (expected != bound) return null
        val value = canonicalPublicTripPromotingOfficialHttp(raw) ?: return null
        val actualPublicId = tripId(value)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!STABLE_EXTERNAL_ID.matches(actualPublicId)) return null
        return value
    }

    /**
     * Revalidates a permalink already carried by collector state. A network-authoritative
     * binding remains tied to the same canonical administrative trip id; every other source
     * keeps the historical strict same-id contract.
     */
    fun publicTripForCollectorState(
        raw: String?,
        expectedTripId: String?,
        binding: String?,
    ): String? = when (binding?.trim()) {
        PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE ->
            publicTripFromAuthoritativeNetwork(raw, expectedTripId, expectedTripId)
        else -> publicTrip(raw, expectedTripId)
    }

    fun publicTripPublicId(raw: String?): String? {
        val value = canonicalPublicTripPromotingOfficialHttp(raw) ?: return null
        return tripId(value)?.trim()?.takeIf(STABLE_EXTERNAL_ID::matches)
    }

    private fun canonicalPublicTripPromotingOfficialHttp(raw: String?): String? {
        val uri = parseOfficialHttpOrHttps(raw) ?: return null
        val path = uri.path.orEmpty().trimEnd('/').lowercase()
        if (path != "/trip" && !path.startsWith("/trip/")) return null
        val query = uri.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .filterNot { part -> part.substringBefore('=').equals("search_uuid", ignoreCase = true) }
            .joinToString("&")
        val promoted = buildString {
            append("https://").append(uri.host.lowercase())
            append(uri.rawPath.orEmpty().ifBlank { "/" })
            if (query.isNotBlank()) append('?').append(query)
        }
        return promoted.takeIf { publicTrip(it, null) != null }
    }

    private fun parseOfficialHttpOrHttps(raw: String?): URI? {
        val value = raw?.trim().orEmpty()
        val absoluteValue = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) -> value
            else -> return null
        }
        return runCatching {
            URI(absoluteValue)
        }.getOrNull()?.takeIf { uri ->
            val https = uri.scheme.equals("https", ignoreCase = true)
            val http = uri.scheme.equals("http", ignoreCase = true)
            (https || http) &&
                isOfficialBlaBlaHost(uri.host) &&
                uri.rawUserInfo == null &&
                when {
                    https -> uri.port in setOf(-1, 443)
                    http -> uri.port in setOf(-1, 80)
                    else -> false
                }
        }
    }

    const val PUBLIC_TRIP_BINDING_SAME_ID = "same_trip_id"
    const val PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE = "network_authoritative"

    /** URLs the authenticated management browser may open on explicit user action. */
    fun isManageTarget(raw: String?): Boolean =
        isSpecificTrip(raw) || (isAllowed(raw) && isPassenger(raw))

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
            "https://${uri.host.lowercase()}${uri.rawPath.orEmpty().ifBlank { "/" }}".take(240)
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
            isOfficialBlaBlaHost(uri.host) &&
            uri.rawUserInfo == null &&
            uri.port in setOf(-1, 443)
    }

    private data class NavigationIdentity(
        val path: String,
        val strongId: String,
    )

    private val STABLE_EXTERNAL_ID = Regex("[A-Za-z0-9_-]{8,160}")
}
