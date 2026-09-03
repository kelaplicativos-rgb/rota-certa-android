package br.com.mapeiaia.rotacerta.trips

import java.security.MessageDigest
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

internal fun canonicalPublicProjectionPayload0411(
    trip: Trip,
    bookings: List<Booking>,
    publicationRevision: Long,
    nowMillis: Long = System.currentTimeMillis(),
): CanonicalPublicTripPayload0411 {
    val capacity = operationalInventoryCapacity(trip, bookings)
    val projectedTrip = trip.copy(capacity = capacity)
    val loads = SeatAvailabilityEngine.segmentLoads(projectedTrip, bookings, nowMillis)
    val summary = operationalSeatSummary(projectedTrip, bookings, nowMillis)
    val status = SeatAvailabilityEngine.suggestedStatus(projectedTrip, bookings, nowMillis)
    val reliable = trip.capacityReliable
    val available = if (reliable) summary.availableSeats.coerceAtLeast(0) else 0
    return CanonicalPublicTripPayload0411(
        canonicalTripId = trip.id,
        canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L),
        blablaProfileUuid = trip.blablaProfileUuid.orEmpty().trim().lowercase(),
        blablaTripId = trip.blablaTripId.orEmpty().trim(),
        title = trip.title.trim(),
        departureAtMillis = trip.departureAtMillis,
        timezoneId = trip.publicTimezoneId0411.ifBlank { ZoneId.systemDefault().id },
        status = status.name,
        capacity = capacity,
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
        segmentLoads = loads.map { it.occupiedSeats.coerceAtLeast(0) },
        segmentPassengerLoads = loads.map { it.passengerSeats.coerceAtLeast(0) },
        segmentBlockedLoads = loads.map { it.blockedSeats.coerceAtLeast(0) },
        availableSeatsMinimum = available,
        availableSeatsMaximum = available,
        operationalAvailableSeats = available,
        publishedSeats = trip.publishedSeats?.coerceAtLeast(0),
        rotaCertaSeatAllocation = (trip.rotaCertaSeatAllocation ?: 0).coerceAtLeast(0),
        publicBookingEnabled = trip.publicBookingEnabled,
        capacityReliable = reliable,
        itineraryAuthoritative = trip.itineraryAuthoritative,
        publicUrl = trip.publicUrl.orEmpty().trim(),
        blablaPublicUrl = BlaBlaCollectorUrlModule.publicTrip(
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
        .digest(
            canonicalPublicProjectionJson0411(
                payload.copy(publicationRevision = 0L),
            ).toByteArray(Charsets.UTF_8),
        )
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal data class PublicProjectionByteDiff0421(
    val expectedLength: Int,
    val actualLength: Int,
    val expectedSha256: String,
    val actualSha256: String,
    val firstDifferentByteOffset: Int,
    val differentByteRanges: List<String>,
)

internal fun compareCanonicalPublicBytes0421(
    expected: CanonicalPublicTripPayload0411,
    actual: CanonicalPublicTripPayload0411,
): PublicProjectionByteDiff0421 {
    val expectedBytes = canonicalPublicProjectionJson0411(expected.copy(publicationRevision = 0L)).toByteArray(Charsets.UTF_8)
    val actualBytes = canonicalPublicProjectionJson0411(actual.copy(publicationRevision = 0L)).toByteArray(Charsets.UTF_8)
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
        val start = limit
        if (first < 0) first = start
        ranges += "$start-${maxOf(expectedBytes.size, actualBytes.size) - 1}"
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

    val revisionValid = actual.canonicalRevision == expected.canonicalRevision &&
        actual.canonicalRevision > 0L
    if (!revisionValid) mismatch += "canonicalRevision"

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
    val expectedLink = BlaBlaCollectorUrlModule.publicTrip(expected.blablaPublicUrl, expectedTripId).orEmpty()
    val actualLink = BlaBlaCollectorUrlModule.publicTrip(actual.blablaPublicUrl, expectedTripId).orEmpty()
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
    return PublicMirrorAttestationDecision0411(
        state = if (validated) PublicMirrorAttestationState0411.VALIDATED else PublicMirrorAttestationState0411.DIVERGENT,
        expectedHash = expectedHash,
        readbackHash = readbackHash,
        mismatchFields = uniqueMismatch,
        reason = if (validated) "PUBLIC_READBACK_MATCH" else "PUBLIC_READBACK_MISMATCH",
        identityValid = identityValid,
        revisionValid = revisionValid,
        linkValid = linkValid,
    )
}

internal fun Trip.publicMirrorAttestationCurrent0411(): Boolean =
    publicMirrorAttestationState0411 == PublicMirrorAttestationState0411.VALIDATED &&
        publicMirrorAttestedCanonicalRevision0411 == canonicalRevision &&
        publicMirrorAttestedPublicationRevision0411 == publicationRevision &&
        publicMirrorExpectedHash0411.isNotBlank() &&
        publicMirrorExpectedHash0411 == publicMirrorReadbackHash0411

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
)
