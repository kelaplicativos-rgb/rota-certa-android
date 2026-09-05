package br.com.mapeiaia.rotacerta.trips

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@Serializable
enum class PublicMirrorAttestationState0411 {
    UNPROVEN,
    PENDING,
    VALIDATED,
    DIVERGENT,
}

@Serializable
internal data class CanonicalPublicStop0411(
    val id: String,
    val order: Int,
    val name: String,
    val address: String,
    val plannedArrivalMillis: Long? = null,
    val plannedDepartureMillis: Long? = null,
)

@Serializable
internal data class CanonicalPublicTripPayload0411(
    val schemaVersion: String = "public-trip-v2",
    val canonicalTripId: String,
    val canonicalRevision: Long,
    val blablaProfileUuid: String,
    val blablaTripId: String,
    val title: String,
    val departureAtMillis: Long,
    val timezoneId: String,
    val status: String,
    val capacity: Int,
    val stops: List<CanonicalPublicStop0411>,
    val segmentLoads: List<Int>,
    val segmentPassengerLoads: List<Int>,
    val segmentBlockedLoads: List<Int>,
    val availableSeatsMinimum: Int,
    val availableSeatsMaximum: Int,
    val operationalAvailableSeats: Int,
    val publishedSeats: Int? = null,
    val rotaCertaSeatAllocation: Int,
    val publicBookingEnabled: Boolean,
    val capacityReliable: Boolean,
    val itineraryAuthoritative: Boolean,
    val publicUrl: String,
    val blablaPublicUrl: String,
    val publicationRevision: Long,
    val canonicalStateHash: String,
)

@Serializable
internal data class DriverPublicTripReadback0411(
    val remoteTripId: String,
    val payload: CanonicalPublicTripPayload0411,
    val publicProjectionHash: String,
    val persistedAtMillis: Long = 0L,
)

internal data class PublicMirrorAttestationDecision0411(
    val state: PublicMirrorAttestationState0411,
    val expectedHash: String,
    val readbackHash: String,
    val mismatchFields: List<String>,
    val reason: String,
    val identityValid: Boolean,
    val revisionValid: Boolean,
    val linkValid: Boolean,
)

private val publicProjectionJson0411 = Json {
    encodeDefaults = true
    explicitNulls = true
}

internal fun serverPublicAttestationConfirmed0433(
    expectedCanonicalRevision: Long,
    expectedPublicationRevision: Long,
    response: DriverPublicAttestationResponse0417?,
): Boolean =
    response != null &&
        response.verified &&
        response.state.equals("VERIFIED", ignoreCase = true) &&
        expectedCanonicalRevision > 0L &&
        expectedPublicationRevision > 0L &&
        response.canonicalRevision == expectedCanonicalRevision &&
        response.publicationRevision == expectedPublicationRevision

internal fun canonicalPublicProjectionPayload0411(
    trip: Trip,
    bookings: List<Booking>,
    publicationRevision: Long,
    nowMillis: Long = System.currentTimeMillis(),
    canonicalTripId: String = trip.id,
    operationalSnapshot: CanonicalOperationalSnapshot0434 = canonicalOperationalSnapshot0434(trip, bookings, nowMillis),
): CanonicalPublicTripPayload0411 {
    val reliable = trip.capacityReliable
    return CanonicalPublicTripPayload0411(
        canonicalTripId = canonicalTripId.trim().ifBlank { trip.id },
        canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L),
        blablaProfileUuid = trip.blablaProfileUuid.orEmpty().trim().lowercase(),
        blablaTripId = trip.blablaTripId.orEmpty().trim(),
        title = trip.title.trim(),
        departureAtMillis = trip.departureAtMillis,
        timezoneId = trip.publicTimezoneId0411.trim(),
        status = trip.status.name,
        capacity = operationalSnapshot.capacity,
        stops = trip.stops.sortedBy(TripStop::order).mapIndexed { index, stop ->
            CanonicalPublicStop0411(
                id = stop.id.trim(),
                order = index,
                name = stop.name.trim(),
                address = stop.address.trim(),
                plannedArrivalMillis = stop.plannedArrivalMillis,
                plannedDepartureMillis = stop.plannedDepartureMillis,
            )
        },
        segmentLoads = operationalSnapshot.segmentLoads,
        segmentPassengerLoads = operationalSnapshot.segmentPassengerLoads,
        segmentBlockedLoads = operationalSnapshot.segmentBlockedLoads,
        availableSeatsMinimum = operationalSnapshot.availableSeatsMinimum,
        availableSeatsMaximum = operationalSnapshot.availableSeatsMaximum,
        operationalAvailableSeats = operationalSnapshot.operationalAvailableSeats,
        publishedSeats = trip.publishedSeats?.coerceAtLeast(0),
        rotaCertaSeatAllocation = (trip.rotaCertaSeatAllocation ?: 0).coerceAtLeast(0),
        publicBookingEnabled = trip.publicBookingEnabled,
        capacityReliable = reliable,
        itineraryAuthoritative = trip.itineraryAuthoritative,
        publicUrl = trip.publicUrl.orEmpty().trim(),
        blablaPublicUrl = canonicalBoundBlaBlaPublicUrl0423(
            trip.blablaPublicUrl,
            trip.blablaTripId,
        ).orEmpty(),
        publicationRevision = publicationRevision.coerceAtLeast(0L),
        canonicalStateHash = trip.canonicalStateHash.trim(),
    )
}

internal fun canonicalPublicProjectionJson0411(payload: CanonicalPublicTripPayload0411): String =
    publicProjectionJson0411.encodeToString(payload)

internal fun canonicalPublicProjectionHash0411(payload: CanonicalPublicTripPayload0411): String =
    "public-v2:" + MessageDigest.getInstance("SHA-256")
        .digest(canonicalSemanticProjectionJson0422(payload).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun canonicalSemanticProjectionJson0422(payload: CanonicalPublicTripPayload0411): String =
    canonicalPublicProjectionJson0411(payload.copy(publicationRevision = 0L))

@Serializable
internal data class PublicProjectionFieldDiff0422(
    val fieldPath: String,
    val expectedValue: String,
    val actualValue: String,
    val expectedType: String,
    val actualType: String,
    val expectedByteStart: Int,
    val expectedByteEnd: Int,
    val actualByteStart: Int,
    val actualByteEnd: Int,
    val expectedBytesHex: String,
    val actualBytesHex: String,
    val expectedUtf8Fragment: String,
    val actualUtf8Fragment: String,
    val truncated: Boolean,
)

internal data class PublicProjectionByteDiff0421(
    val expectedLength: Int,
    val actualLength: Int,
    val expectedSha256: String,
    val actualSha256: String,
    val firstDifferentByteOffset: Int,
    val differentByteRanges: List<String>,
    val fieldDiffs: List<PublicProjectionFieldDiff0422> = emptyList(),
)

internal fun publicProjectionFieldDiffJson0422(diff: PublicProjectionFieldDiff0422): String =
    publicProjectionJson0411.encodeToString(diff)

private fun ByteArray.indexOfSequence0422(needle: ByteArray, startAt: Int = 0): Int {
    if (needle.isEmpty()) return startAt.coerceIn(0, size)
    if (needle.size > size) return -1
    for (index in startAt.coerceAtLeast(0)..(size - needle.size)) {
        var matches = true
        for (offset in needle.indices) {
            if (this[index + offset] != needle[offset]) {
                matches = false
                break
            }
        }
        if (matches) return index
    }
    return -1
}

private fun jsonElementType0422(value: JsonElement?): String = when (value) {
    null, JsonNull -> "null"
    is JsonObject -> "object"
    is JsonArray -> "array"
    is JsonPrimitive -> when {
        value.isString -> "string"
        value.content == "true" || value.content == "false" -> "boolean"
        else -> "number"
    }
    else -> "unknown"
}

private data class JsonByteRange0422(val start: Int, val endExclusive: Int)

private fun locateTopLevelJsonValue0422(
    jsonBytes: ByteArray,
    fieldName: String,
    value: JsonElement?,
): JsonByteRange0422 {
    val keyBytes = ("\"" + fieldName + "\":").toByteArray(Charsets.UTF_8)
    val keyStart = jsonBytes.indexOfSequence0422(keyBytes)
    if (keyStart < 0) return JsonByteRange0422(-1, -1)
    val valueStart = keyStart + keyBytes.size
    val serializedValue = (value ?: JsonNull).toString().toByteArray(Charsets.UTF_8)
    val located = jsonBytes.indexOfSequence0422(serializedValue, valueStart)
    return if (located == valueStart) {
        JsonByteRange0422(located, located + serializedValue.size)
    } else {
        JsonByteRange0422(valueStart, (valueStart + serializedValue.size).coerceAtMost(jsonBytes.size))
    }
}

private fun boundedByteEvidence0422(bytes: ByteArray, range: JsonByteRange0422): Triple<String, String, Boolean> {
    if (range.start < 0 || range.endExclusive < range.start || range.start >= bytes.size) {
        return Triple("", "", false)
    }
    val safeEnd = range.endExclusive.coerceAtMost(bytes.size)
    val evidenceEnd = minOf(safeEnd, range.start + 96)
    val fragment = bytes.copyOfRange(range.start, evidenceEnd)
    val hex = fragment.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val utf8 = fragment.toString(Charsets.UTF_8).take(240)
    return Triple(hex, utf8, safeEnd - range.start > fragment.size)
}

internal fun compareCanonicalPublicBytes0421(
    expected: CanonicalPublicTripPayload0411,
    actual: CanonicalPublicTripPayload0411,
): PublicProjectionByteDiff0421 {
    val expectedJson = canonicalSemanticProjectionJson0422(expected)
    val actualJson = canonicalSemanticProjectionJson0422(actual)
    val expectedBytes = expectedJson.toByteArray(Charsets.UTF_8)
    val actualBytes = actualJson.toByteArray(Charsets.UTF_8)
    val limit = minOf(expectedBytes.size, actualBytes.size)
    var first = -1
    val ranges = mutableListOf<String>()
    var rangeStart = -1
    for (index in 0 until limit) {
        val differs = expectedBytes[index] != actualBytes[index]
        if (differs && first < 0) first = index
        if (differs && rangeStart < 0) rangeStart = index
        if (!differs && rangeStart >= 0) {
            ranges += "$rangeStart-${index - 1}"
            rangeStart = -1
            if (ranges.size >= 12) break
        }
    }
    if (ranges.size < 12 && rangeStart >= 0) ranges += "$rangeStart-${limit - 1}"
    if (expectedBytes.size != actualBytes.size && ranges.size < 12) {
        val rangeTailStart = limit
        if (first < 0) first = rangeTailStart
        ranges += "$rangeTailStart-${maxOf(expectedBytes.size, actualBytes.size) - 1}"
    }

    val expectedObject = publicProjectionJson0411.parseToJsonElement(expectedJson).jsonObject
    val actualObject = publicProjectionJson0411.parseToJsonElement(actualJson).jsonObject
    val fieldDiffs = (expectedObject.keys + actualObject.keys)
        .distinct()
        .filter { field -> expectedObject[field] != actualObject[field] }
        .take(24)
        .map { field ->
            val expectedValue = expectedObject[field]
            val actualValue = actualObject[field]
            val expectedRange = locateTopLevelJsonValue0422(expectedBytes, field, expectedValue)
            val actualRange = locateTopLevelJsonValue0422(actualBytes, field, actualValue)
            val expectedEvidence = boundedByteEvidence0422(expectedBytes, expectedRange)
            val actualEvidence = boundedByteEvidence0422(actualBytes, actualRange)
            val expectedText = (expectedValue ?: JsonNull).toString()
            val actualText = (actualValue ?: JsonNull).toString()
            PublicProjectionFieldDiff0422(
                fieldPath = "$." + field,
                expectedValue = expectedText.take(240),
                actualValue = actualText.take(240),
                expectedType = jsonElementType0422(expectedValue),
                actualType = jsonElementType0422(actualValue),
                expectedByteStart = expectedRange.start,
                expectedByteEnd = expectedRange.endExclusive,
                actualByteStart = actualRange.start,
                actualByteEnd = actualRange.endExclusive,
                expectedBytesHex = expectedEvidence.first,
                actualBytesHex = actualEvidence.first,
                expectedUtf8Fragment = expectedEvidence.second,
                actualUtf8Fragment = actualEvidence.second,
                truncated = expectedEvidence.third || actualEvidence.third ||
                    expectedText.length > 240 || actualText.length > 240,
            )
        }

    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return PublicProjectionByteDiff0421(
        expectedLength = expectedBytes.size,
        actualLength = actualBytes.size,
        expectedSha256 = sha(expectedBytes),
        actualSha256 = sha(actualBytes),
        firstDifferentByteOffset = first,
        differentByteRanges = ranges,
        fieldDiffs = fieldDiffs,
    )
}

internal fun evaluatePublicMirrorReadback0411(
    expected: CanonicalPublicTripPayload0411,
    readback: DriverPublicTripReadback0411,
): PublicMirrorAttestationDecision0411 {
    val expectedHash = canonicalPublicProjectionHash0411(expected)
    val actual = readback.payload
    val mismatch = mutableListOf<String>()
    val identityValid =
        actual.canonicalTripId == expected.canonicalTripId &&
            actual.blablaProfileUuid.equals(expected.blablaProfileUuid, ignoreCase = true) &&
            actual.blablaTripId == expected.blablaTripId
    if (!identityValid) mismatch += "identity"

    val logicalRevisionValid = actual.canonicalRevision == expected.canonicalRevision &&
        actual.canonicalRevision > 0L
    if (!logicalRevisionValid) mismatch += "canonicalRevision"
    val transportRevisionValid = expected.publicationRevision > 0L &&
        actual.publicationRevision == expected.publicationRevision
    if (!transportRevisionValid) mismatch += "publicationRevision"
    val persistedCommitValid = readback.persistedAtMillis > 0L
    if (!persistedCommitValid) mismatch += "persistedAtMillis"
    val revisionValid = logicalRevisionValid && transportRevisionValid && persistedCommitValid

    if (actual.canonicalStateHash != expected.canonicalStateHash) mismatch += "canonicalStateHash"
    if (actual.title != expected.title) mismatch += "title"
    if (actual.departureAtMillis != expected.departureAtMillis) mismatch += "departureAtMillis"
    if (actual.timezoneId != expected.timezoneId) mismatch += "timezoneId"
    if (actual.status != expected.status) mismatch += "status"
    if (actual.capacity != expected.capacity) mismatch += "capacity"
    if (actual.stops != expected.stops) mismatch += "stops"
    if (actual.segmentLoads != expected.segmentLoads) mismatch += "segmentLoads"
    if (actual.segmentPassengerLoads != expected.segmentPassengerLoads) mismatch += "segmentPassengerLoads"
    if (actual.segmentBlockedLoads != expected.segmentBlockedLoads) mismatch += "segmentBlockedLoads"
    if (actual.availableSeatsMinimum != expected.availableSeatsMinimum ||
        actual.availableSeatsMaximum != expected.availableSeatsMaximum ||
        actual.operationalAvailableSeats != expected.operationalAvailableSeats
    ) mismatch += "availability"
    if (actual.publishedSeats != expected.publishedSeats) mismatch += "publishedSeats"
    if (actual.rotaCertaSeatAllocation != expected.rotaCertaSeatAllocation) mismatch += "rotaCertaSeatAllocation"
    if (actual.publicBookingEnabled != expected.publicBookingEnabled) mismatch += "publicBookingEnabled"
    if (actual.capacityReliable != expected.capacityReliable) mismatch += "capacityReliable"
    if (actual.itineraryAuthoritative != expected.itineraryAuthoritative) mismatch += "itineraryAuthoritative"
    if (actual.publicUrl.isBlank() || actual.publicUrl != expected.publicUrl) mismatch += "publicUrl"

    val expectedTripId = expected.blablaTripId.takeIf(String::isNotBlank)
    // By this stage the canonical URL was already bound upstream to the strong
    // administrative trip identity. The public /trip token may legitimately
    // differ from that administrative ID, so downstream validation must preserve
    // the proven canonical binding and then require readback byte-equivalence.
    val expectedLink = canonicalBoundBlaBlaPublicUrl0423(expected.blablaPublicUrl, expectedTripId).orEmpty()
    val actualLink = canonicalBoundBlaBlaPublicUrl0423(actual.blablaPublicUrl, expectedTripId).orEmpty()
    val linkRequired = expectedTripId != null
    val linkValid = if (linkRequired) expectedLink.isNotBlank() && actualLink == expectedLink else true
    if (!linkValid) mismatch += "blablaPublicUrl"

    val readbackHash = canonicalPublicProjectionHash0411(actual)
    if (readback.publicProjectionHash.isBlank() || readback.publicProjectionHash != readbackHash) {
        mismatch += "serverHash"
    }
    if (readbackHash != expectedHash) mismatch += "publicHash"

    val uniqueMismatch = mismatch.distinct()
    val validated = identityValid && revisionValid && linkValid && uniqueMismatch.isEmpty()
    val optionalLinkOnlyUnproven =
        identityValid &&
            revisionValid &&
            linkRequired &&
            expectedLink.isBlank() &&
            uniqueMismatch.all { it == "blablaPublicUrl" }
    return PublicMirrorAttestationDecision0411(
        state = when {
            validated -> PublicMirrorAttestationState0411.VALIDATED
            optionalLinkOnlyUnproven -> PublicMirrorAttestationState0411.UNPROVEN
            else -> PublicMirrorAttestationState0411.DIVERGENT
        },
        expectedHash = expectedHash,
        readbackHash = readbackHash,
        mismatchFields = uniqueMismatch,
        reason = when {
            validated -> "PUBLIC_READBACK_MATCH"
            linkRequired && expectedLink.isBlank() -> "BLABLACAR_PUBLIC_URL_PENDING"
            !transportRevisionValid -> "STALE_TRANSPORT_REVISION"
            !logicalRevisionValid -> "STALE_LOGICAL_REVISION"
            !persistedCommitValid -> "PUBLIC_COMMIT_TIMESTAMP_MISSING"
            else -> "PUBLIC_READBACK_MISMATCH"
        },
        identityValid = identityValid,
        revisionValid = revisionValid,
        linkValid = linkValid,
    )
}

internal fun Trip.publicMirrorAttestationCurrent0411(): Boolean =
    publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.VALIDATED &&
        publicMirrorAttestedCanonicalRevision0411 == canonicalRevision &&
        publicMirrorReadbackCanonicalRevision0421 == canonicalRevision &&
        publicMirrorAttestedPublicationRevision0411 == publicationRevision &&
        publicMirrorAttemptedPublicationRevision0421 == publicationRevision &&
        publicMirrorReadbackPublicationRevision0421 == publicationRevision &&
        publicMirrorLastReadbackAtMillis0421 > 0L &&
        publicMirrorPublicIdentity0421.isNotBlank() &&
        publicMirrorExpectedHash0411.isNotBlank() &&
        publicMirrorExpectedHash0411 == publicMirrorReadbackHash0411

/**
 * The public projection can be fully read back while the optional BlaBlaCar /trip
 * enrichment is still unavailable. This never grants blue attestation.
 */
internal fun Trip.publicMirrorProjectionCurrent0411(): Boolean =
    publicMirrorAttestationCurrent0411() ||
        (
            publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.UNPROVEN &&
                publicMirrorAttestationReason0411 in setOf("BLABLACAR_PUBLIC_URL_PENDING", "BLABLACAR_PUBLIC_URL_UNRESOLVED") &&
                publicMirrorMismatchFields0411.isNotEmpty() &&
                publicMirrorMismatchFields0411.all { it == "blablaPublicUrl" } &&
                publicMirrorReadbackCanonicalRevision0421 == canonicalRevision &&
                publicMirrorAttemptedPublicationRevision0421 == publicationRevision &&
                publicMirrorReadbackPublicationRevision0421 == publicationRevision &&
                publicMirrorLastReadbackAtMillis0421 > 0L &&
                publicMirrorPublicIdentity0421.isNotBlank() &&
                publicMirrorExpectedHash0411.isNotBlank() &&
                publicMirrorExpectedHash0411 == publicMirrorReadbackHash0411
        )

internal fun Trip.publicMirrorPublishedWithoutBlaBlaUrl0465(): Boolean =
    !publicMirrorAttestationCurrent0411() &&
        publicMirrorProjectionCurrent0411() &&
        canonicalBoundBlaBlaPublicUrl0423(blablaPublicUrl, blablaTripId).isNullOrBlank()

internal fun Trip.invalidatePublicMirror0411(reason: String): Trip = copy(
    publicMirrorAttestationState0411 = if (deleted || publicationTombstone) {
        PublicMirrorAttestationState0411.UNPROVEN
    } else {
        PublicMirrorAttestationState0411.PENDING
    },
    publicMirrorAttestedCanonicalRevision0411 = 0L,
    publicMirrorAttestedPublicationRevision0411 = 0L,
    publicMirrorExpectedHash0411 = "",
    publicMirrorReadbackHash0411 = "",
    publicMirrorAttestedAtMillis0411 = 0L,
    publicMirrorReadbackLatencyMillis0411 = 0L,
    publicMirrorAttestationReason0411 = reason.take(160),
    publicMirrorMismatchFields0411 = emptyList(),
    publicMirrorReadbackCanonicalRevision0421 = 0L,
    publicMirrorAttemptedPublicationRevision0421 = 0L,
    publicMirrorReadbackPublicationRevision0421 = 0L,
    publicMirrorPublicIdentity0421 = "",
    publicMirrorLastReadbackAtMillis0421 = 0L,
    publicMirrorEvidenceId0421 = "",
    publicMirrorTraceId0421 = "",
    publicMirrorExpectedBytes0421 = 0,
    publicMirrorActualBytes0421 = 0,
    publicMirrorFirstDifferentByteOffset0421 = -1,
    publicMirrorDifferentByteRanges0421 = emptyList(),
    publicMirrorFieldDiffs0422 = emptyList(),
    publicMirrorHttpStatus0421 = 0,
    publicMirrorBackendErrorCode0421 = "",
    publicMirrorFailedStage0421 = "",
    publicMirrorNetworkCallId0421 = "",
    publicMirrorRequestBytes0421 = 0,
    publicMirrorResponseBytes0421 = 0,
    publicMirrorRequestHash0421 = "",
    publicMirrorResponseHash0421 = "",
)
