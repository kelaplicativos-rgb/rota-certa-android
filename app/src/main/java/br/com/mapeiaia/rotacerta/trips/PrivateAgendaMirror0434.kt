package br.com.mapeiaia.rotacerta.trips

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class TimelineMirrorFieldPolicy0434 {
    MIRROR,
    MIRROR_AND_OPTIONALLY_PUBLIC,
    LOCAL_ONLY,
    SECRET_NEVER_MIRROR,
    DERIVED,
}

@Serializable
internal data class PrivateAgendaMirrorStop0434(
    val id: String,
    val order: Int,
    val name: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val plannedArrivalMillis: Long? = null,
    val plannedDepartureMillis: Long? = null,
    val priceToNextCents: Long = 0L,
)

@Serializable
internal data class PrivateAgendaMirrorBooking0434(
    val id: String,
    val tripId: String,
    val passengerId: String = "",
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int,
    val status: String,
    val operationalStatus: String,
    val paymentStatus: String,
    val lastDriverSelection: String = "",
    val holdExpiresAtMillis: Long? = null,
    val source: String,
    val capacityClaimType: String,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
    val fareMinorUnits: Long? = null,
    val fareCurrencyCode: String = "",
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
internal data class PrivateAgendaMirrorPayload0434(
    val schemaVersion: String = "private-agenda-mirror-v1",
    val canonicalTripId: String,
    val internalTripId: String,
    val identityAliases: List<String> = emptyList(),
    val canonicalRevision: Long,
    val canonicalStateHash: String,
    val tripKey: String,
    val recordOrigin: String,
    val title: String,
    val departureAtMillis: Long,
    val timezoneId: String,
    val status: String,
    val stops: List<PrivateAgendaMirrorStop0434>,
    val capacity: Int,
    val publishedSeats: Int? = null,
    val rotaCertaSeatAllocation: Int,
    val operationalInventory: Int,
    val capacityReliable: Boolean,
    val segmentLoads: List<Int>,
    val segmentPassengerLoads: List<Int>,
    val segmentBlockedLoads: List<Int>,
    val availableSeatsMinimum: Int,
    val availableSeatsMaximum: Int,
    val operationalAvailableSeats: Int,
    val confirmedPassengerSeats: Int,
    val blockedSeats: Int,
    val operationalOverbookingSeats: Int,
    val publicBookingEnabled: Boolean,
    val itineraryAuthoritative: Boolean,
    val blablaProfileUuid: String,
    val blablaTripId: String,
    val blablaPublicUrl: String,
    val notes: String,
    val bookings: List<PrivateAgendaMirrorBooking0434>,
    val seatAllocationVersionUsed: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deleted: Boolean,
    val deletedAtMillis: Long? = null,
)

private val privateMirrorJson0434 = Json {
    encodeDefaults = true
    explicitNulls = true
}

internal fun privateAgendaMirrorPayload0434(
    trip: Trip,
    bookings: List<Booking>,
    operationalSnapshot: CanonicalOperationalSnapshot0434,
    canonicalTripId: String = trip.id,
): PrivateAgendaMirrorPayload0434 {
    val canonicalId = canonicalTripId.trim().ifBlank { trip.id }
    return PrivateAgendaMirrorPayload0434(
        canonicalTripId = canonicalId,
        internalTripId = trip.id,
        identityAliases = listOf(trip.id).filter { it.isNotBlank() && it != canonicalId },
        canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L),
        canonicalStateHash = trip.canonicalStateHash.trim(),
        tripKey = trip.tripKey.trim(),
        recordOrigin = resolvedTripRecordOrigin(trip).name,
        title = trip.title,
        departureAtMillis = trip.departureAtMillis,
        timezoneId = trip.publicTimezoneId0411.trim(),
        status = trip.status.name,
        stops = trip.stops.sortedBy(TripStop::order).map { stop ->
            PrivateAgendaMirrorStop0434(
                id = stop.id,
                order = stop.order,
                name = stop.name,
                address = stop.address,
                latitude = stop.latitude,
                longitude = stop.longitude,
                plannedArrivalMillis = stop.plannedArrivalMillis,
                plannedDepartureMillis = stop.plannedDepartureMillis,
                priceToNextCents = stop.priceToNextCents,
            )
        },
        capacity = trip.capacity.coerceAtLeast(0),
        publishedSeats = trip.publishedSeats?.coerceAtLeast(0),
        rotaCertaSeatAllocation = (trip.rotaCertaSeatAllocation ?: 0).coerceAtLeast(0),
        operationalInventory = operationalSnapshot.capacity,
        capacityReliable = trip.capacityReliable,
        segmentLoads = operationalSnapshot.segmentLoads,
        segmentPassengerLoads = operationalSnapshot.segmentPassengerLoads,
        segmentBlockedLoads = operationalSnapshot.segmentBlockedLoads,
        availableSeatsMinimum = operationalSnapshot.availableSeatsMinimum,
        availableSeatsMaximum = operationalSnapshot.availableSeatsMaximum,
        operationalAvailableSeats = operationalSnapshot.operationalAvailableSeats,
        confirmedPassengerSeats = operationalSnapshot.confirmedPassengerSeats,
        blockedSeats = operationalSnapshot.blockedSeats,
        operationalOverbookingSeats = operationalSnapshot.operationalOverbookingSeats,
        publicBookingEnabled = trip.publicBookingEnabled,
        itineraryAuthoritative = trip.itineraryAuthoritative,
        blablaProfileUuid = trip.blablaProfileUuid.orEmpty().trim().lowercase(),
        blablaTripId = trip.blablaTripId.orEmpty().trim(),
        blablaPublicUrl = canonicalBoundBlaBlaPublicUrl0423(trip.blablaPublicUrl, trip.blablaTripId).orEmpty(),
        notes = trip.notes,
        bookings = bookings.sortedBy(Booking::id).map { booking ->
            PrivateAgendaMirrorBooking0434(
                id = booking.id,
                tripId = canonicalId,
                passengerId = booking.passengerId,
                passengerName = booking.passengerName,
                passengerContact = booking.passengerContact,
                boardingStopId = booking.boardingStopId,
                dropoffStopId = booking.dropoffStopId,
                seats = booking.seats,
                status = booking.status.name,
                operationalStatus = booking.operationalStatus.name,
                paymentStatus = booking.paymentStatus.name,
                lastDriverSelection = booking.lastDriverSelection,
                holdExpiresAtMillis = booking.holdExpiresAtMillis,
                source = booking.source.name,
                capacityClaimType = booking.capacityClaimType.name,
                sourceReference = booking.sourceReference,
                occupancyGroupId = booking.occupancyGroupId,
                fareMinorUnits = booking.fareMinorUnits,
                fareCurrencyCode = booking.fareCurrencyCode,
                boardingAddress = booking.boardingAddress,
                dropoffAddress = booking.dropoffAddress,
                createdAtMillis = booking.createdAtMillis,
                updatedAtMillis = booking.updatedAtMillis,
            )
        },
        seatAllocationVersionUsed = trip.seatAllocationVersionUsed.coerceAtLeast(0L),
        createdAtMillis = trip.createdAtMillis,
        updatedAtMillis = trip.updatedAtMillis,
        deleted = trip.deleted,
        deletedAtMillis = trip.deletedAtMillis,
    )
}

internal fun privateAgendaMirrorCanonicalJson0434(payload: PrivateAgendaMirrorPayload0434): String =
    privateMirrorJson0434.encodeToString(payload)

internal fun privateAgendaMirrorHash0434(canonicalJson: String): String =
    "private-v1:" + MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal suspend fun syncPrivateAgendaMirror0434(
    api: TripRemoteApi,
    trip: Trip,
    bookings: List<Booking>,
    operationalSnapshot: CanonicalOperationalSnapshot0434,
    canonicalTripId: String,
    correlationId: String = "",
    syncOperationId: String = "",
    idempotencyKey: String = "",
    evidence0421: RemotePublicationEvidenceContext0421? = null,
): DriverPrivateMirrorReadback0434 {
    val payload = privateAgendaMirrorPayload0434(
        trip = trip,
        bookings = bookings,
        operationalSnapshot = operationalSnapshot,
        canonicalTripId = canonicalTripId,
    )
    val canonicalJson = privateAgendaMirrorCanonicalJson0434(payload)
    val privateHash = privateAgendaMirrorHash0434(canonicalJson)
    val write = api.writePrivateAgendaMirror0434(
        DriverPrivateMirrorWriteRequest0434(
            canonicalTripId = payload.canonicalTripId,
            canonicalRevision = payload.canonicalRevision,
            privateStateHash = privateHash,
            canonicalJson = canonicalJson,
            correlationId = correlationId,
            syncOperationId = syncOperationId,
            idempotencyKey = idempotencyKey,
        ),
        evidence0421 = evidence0421,
    )
    require(write.canonicalTripId == payload.canonicalTripId) { "PRIVATE_MIRROR_WRITE_IDENTITY_MISMATCH" }
    require(write.canonicalRevision == payload.canonicalRevision) { "PRIVATE_MIRROR_WRITE_REVISION_MISMATCH" }
    require(write.privateStateHash == privateHash) { "PRIVATE_MIRROR_WRITE_HASH_MISMATCH" }
    val readback = api.readPrivateAgendaMirror0434(payload.canonicalTripId, evidence0421 = evidence0421)
    require(readback.canonicalTripId == payload.canonicalTripId) { "PRIVATE_MIRROR_IDENTITY_MISMATCH" }
    require(readback.canonicalRevision == payload.canonicalRevision) { "PRIVATE_MIRROR_STALE_LOGICAL_REVISION" }
    require(readback.privateStateHash == privateHash) { "PRIVATE_MIRROR_HASH_MISMATCH" }
    require(readback.canonicalJson == canonicalJson) { "PRIVATE_MIRROR_CONTENT_MISMATCH" }
    require(readback.persistedAtMillis > 0L) { "PRIVATE_MIRROR_READBACK_NOT_COMMITTED" }
    return readback
}

internal fun timelineTripFieldPolicies0434(): Map<String, TimelineMirrorFieldPolicy0434> = mapOf(
    "id" to TimelineMirrorFieldPolicy0434.MIRROR,
    "title" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "departureAtMillis" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "capacity" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "status" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "stops" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "publicToken" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "notes" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "remoteId" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicUrl" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "blablaProfileUuid" to TimelineMirrorFieldPolicy0434.MIRROR,
    "blablaTripId" to TimelineMirrorFieldPolicy0434.MIRROR,
    "blablaManageUrl" to TimelineMirrorFieldPolicy0434.SECRET_NEVER_MIRROR,
    "blablaPublicUrl" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "publicBookingEnabled" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "itineraryAuthoritative" to TimelineMirrorFieldPolicy0434.MIRROR,
    "publishedSeats" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "capacityReliable" to TimelineMirrorFieldPolicy0434.MIRROR,
    "createdAtMillis" to TimelineMirrorFieldPolicy0434.MIRROR,
    "updatedAtMillis" to TimelineMirrorFieldPolicy0434.MIRROR,
    "rotaCertaSeatAllocation" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "recordOrigin" to TimelineMirrorFieldPolicy0434.MIRROR,
    "canonicalRevision" to TimelineMirrorFieldPolicy0434.MIRROR,
    "seatAllocationVersionUsed" to TimelineMirrorFieldPolicy0434.MIRROR,
    "publicationRevision" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicationTombstone" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicationEventId" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "externalSnapshot" to TimelineMirrorFieldPolicy0434.DERIVED,
    "externalSnapshotFingerprint" to TimelineMirrorFieldPolicy0434.DERIVED,
    "externalSnapshotComplete" to TimelineMirrorFieldPolicy0434.DERIVED,
    "tripKey" to TimelineMirrorFieldPolicy0434.MIRROR,
    "canonicalStateHash" to TimelineMirrorFieldPolicy0434.MIRROR,
    "publicTimezoneId0411" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "publicMirrorAttestationState0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorAttestedCanonicalRevision0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorAttestedPublicationRevision0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorExpectedHash0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorReadbackHash0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorAttestedAtMillis0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorLastReadbackAtMillis0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorReadbackLatencyMillis0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorMismatchFields0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorAttestationReason0411" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorAttemptedPublicationRevision0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorPublicIdentity0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorReadbackCanonicalRevision0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorReadbackPublicationRevision0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorEvidenceId0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorTraceId0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorHttpStatus0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorBackendErrorCode0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorFailedStage0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorNetworkCallId0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorRequestBytes0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorResponseBytes0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorRequestHash0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorResponseHash0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorExpectedBytes0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorActualBytes0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorFirstDifferentByteOffset0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorDifferentByteRanges0421" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "publicMirrorFieldDiffs0422" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
    "lastCollectionRunId" to TimelineMirrorFieldPolicy0434.DERIVED,
    "lastCollectionGeneration" to TimelineMirrorFieldPolicy0434.DERIVED,
    "lastObservedAtMillis" to TimelineMirrorFieldPolicy0434.DERIVED,
    "deleted" to TimelineMirrorFieldPolicy0434.MIRROR,
    "deletedAtMillis" to TimelineMirrorFieldPolicy0434.MIRROR,
)


internal fun timelineBookingFieldPolicies0434(): Map<String, TimelineMirrorFieldPolicy0434> = mapOf(
    "id" to TimelineMirrorFieldPolicy0434.MIRROR,
    "tripId" to TimelineMirrorFieldPolicy0434.MIRROR,
    "passengerName" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "passengerContact" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "boardingStopId" to TimelineMirrorFieldPolicy0434.MIRROR,
    "dropoffStopId" to TimelineMirrorFieldPolicy0434.MIRROR,
    "seats" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "status" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "operationalStatus" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "paymentStatus" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "lastDriverSelection" to TimelineMirrorFieldPolicy0434.MIRROR,
    "holdExpiresAtMillis" to TimelineMirrorFieldPolicy0434.MIRROR,
    ("cancellation" + "Token") to TimelineMirrorFieldPolicy0434.SECRET_NEVER_MIRROR,
    "createdAtMillis" to TimelineMirrorFieldPolicy0434.MIRROR,
    "updatedAtMillis" to TimelineMirrorFieldPolicy0434.MIRROR,
    "source" to TimelineMirrorFieldPolicy0434.MIRROR,
    "capacityClaimType" to TimelineMirrorFieldPolicy0434.MIRROR,
    "sourceReference" to TimelineMirrorFieldPolicy0434.MIRROR,
    "occupancyGroupId" to TimelineMirrorFieldPolicy0434.MIRROR,
    "passengerId" to TimelineMirrorFieldPolicy0434.MIRROR,
    "fareMinorUnits" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "fareCurrencyCode" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "boardingAddress" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "dropoffAddress" to TimelineMirrorFieldPolicy0434.MIRROR_AND_OPTIONALLY_PUBLIC,
    "localMetadataTouched" to TimelineMirrorFieldPolicy0434.LOCAL_ONLY,
)
