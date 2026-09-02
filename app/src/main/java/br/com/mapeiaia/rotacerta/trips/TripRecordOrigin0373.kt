package br.com.mapeiaia.rotacerta.trips

import java.security.MessageDigest
import java.util.Locale

/**
 * Canonical authority for persisted trip origin.
 *
 * EXTERNAL_BACKING is a local persistence projection of a real external BlaBlaCar
 * publication. It may carry local passengers/history, but it is never itself a
 * LOCAL_TRIP_PUBLISH source.
 */
enum class TripRecordOrigin {
    LOCAL,
    EXTERNAL_BACKING,
}

internal fun canonicalExternalTripIdentityKey(
    profileUuidRaw: String?,
    tripIdRaw: String?,
    manageHrefRaw: String?,
): String? {
    val profile = profileUuidRaw
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.matches(CANONICAL_PROFILE_UUID_0373) }
        ?: return null
    val tripId = tripIdRaw?.trim()?.takeIf(String::isNotEmpty)
    val href = manageHrefRaw
        ?.trim()
        ?.substringBefore("&search_uuid=")
        ?.takeIf { value ->
            value.startsWith("https://") &&
                (value.contains("/rides/offer/") || value.contains("/trip/"))
        }
    val identity = tripId?.let { "id:$it" } ?: href?.let { "href:$it" } ?: return null
    return "$profile|$identity"
}

internal fun externalBackingTripIdFor(
    profileUuid: String?,
    blablaTripId: String?,
    blablaManageUrl: String?,
): String? = canonicalExternalTripIdentityKey(profileUuid, blablaTripId, blablaManageUrl)
    ?.let { "timeline-ext-${sha256Short0373(it, 24)}" }

internal fun canonicalTripKey0406(
    tenantIdRaw: String,
    providerRaw: String,
    profileUuidRaw: String,
    providerTripIdRaw: String,
): String? {
    val tenantId = tenantIdRaw.trim().takeIf(String::isNotEmpty) ?: return null
    val provider = providerRaw.trim().uppercase(Locale.ROOT).takeIf(String::isNotEmpty) ?: return null
    val profileUuid = profileUuidRaw.trim().lowercase(Locale.ROOT)
        .takeIf { it.matches(CANONICAL_PROFILE_UUID_0373) } ?: return null
    val providerTripId = providerTripIdRaw.trim().takeIf(String::isNotEmpty) ?: return null
    return "tripkey:" + sha256Short0373(
        listOf(tenantId, provider, profileUuid, providerTripId).joinToString("|"),
        48,
    )
}

internal fun canonicalBlaBlaTripKey0406(
    tenantId: String,
    profileUuid: String?,
    providerTripId: String?,
): String? = canonicalTripKey0406(
    tenantIdRaw = tenantId,
    providerRaw = "BLABLACAR",
    profileUuidRaw = profileUuid.orEmpty(),
    providerTripIdRaw = providerTripId.orEmpty(),
)

internal fun canonicalLocalTripKey0406(tenantId: String, internalTripId: String): String? {
    val tenant = tenantId.trim().takeIf(String::isNotEmpty) ?: return null
    val id = internalTripId.trim().takeIf(String::isNotEmpty) ?: return null
    return "tripkey:" + sha256Short0373("$tenant|LOCAL|$id", 48)
}

/**
 * Legacy 0.1.372 backings predate Trip.recordOrigin. They are recognized only when
 * their strong external identity deterministically regenerates the exact persisted id.
 * The textual prefix by itself is deliberately insufficient.
 */
internal fun resolvedTripRecordOrigin(trip: Trip): TripRecordOrigin {
    if (trip.recordOrigin == TripRecordOrigin.EXTERNAL_BACKING) return TripRecordOrigin.EXTERNAL_BACKING
    val deterministicId = externalBackingTripIdFor(
        trip.blablaProfileUuid,
        trip.blablaTripId,
        trip.blablaManageUrl,
    )
    return if (deterministicId != null && deterministicId == trip.id) {
        TripRecordOrigin.EXTERNAL_BACKING
    } else {
        TripRecordOrigin.LOCAL
    }
}

internal fun Trip.isCanonicalLocalPublishSource(): Boolean =
    resolvedTripRecordOrigin(this) == TripRecordOrigin.LOCAL

internal fun Trip.normalizedRecordOrigin(): Trip =
    if (recordOrigin == resolvedTripRecordOrigin(this)) this
    else copy(recordOrigin = resolvedTripRecordOrigin(this))

private fun sha256Short0373(value: String, chars: Int): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        .take(chars)

private val CANONICAL_PROFILE_UUID_0373 = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
)
