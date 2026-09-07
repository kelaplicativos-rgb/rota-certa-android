package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.TenantStorageScope
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.KeyStore
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TripStore(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tripsKey = tenantScope.key(KEY_TRIPS)
    private val bookingsKey = tenantScope.key(KEY_BOOKINGS)
    private val onlineKey = tenantScope.key(KEY_ONLINE)
    private val publicExternalBindingsKey = tenantScope.key(KEY_PUBLIC_EXTERNAL_BINDINGS)
    private val timelineCanonicalCacheKey0494 = tenantScope.key(KEY_TIMELINE_CANONICAL_CACHE_0494)
    private val timelineCanonicalCacheUpdatedAtKey0494 = tenantScope.key(KEY_TIMELINE_CANONICAL_CACHE_UPDATED_AT_0494)
    private val secretStore = TripSecretStore(appContext, tenantScope)
    private val publicAgendaLinkStore = PublicAgendaLinkStore(appContext, tenantScope)
    private val passengerIdentityStore = PassengerIdentityStore(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    internal fun bookingReconcileScopeKey(): String = tenantScope.tenantId

    fun trips(): List<Trip> = decode<List<Trip>>(prefs.getString(tripsKey, null)).orEmpty()
        .sortedByDescending(Trip::departureAtMillis)

    fun bookings(): List<Booking> = decode<List<Booking>>(prefs.getString(bookingsKey, null)).orEmpty()

    fun bookingsFor(tripId: String): List<Booking> = bookings().filter { it.tripId == tripId }

    /**
     * Offline/performance cache of the last backend-canonical Timeline snapshot.
     * This cache is never published and never feeds the collector/outbox pipeline.
     */
    internal fun timelineCanonicalCache0494(): DriverTripSyncStateResponse0402? =
        decode<DriverTripSyncStateResponse0402>(prefs.getString(timelineCanonicalCacheKey0494, null))

    internal fun timelineCanonicalCacheUpdatedAt0494(): Long =
        prefs.getLong(timelineCanonicalCacheUpdatedAtKey0494, 0L).coerceAtLeast(0L)

    internal fun saveTimelineCanonicalCache0494(
        incoming: DriverTripSyncStateResponse0402,
        nowMillis: Long = System.currentTimeMillis(),
    ): DriverTripSyncStateResponse0402 = synchronized(CANONICAL_LOCK) {
        require(incoming.source.isBlank() || incoming.source == "CANONICAL_BACKEND") {
            "Timeline aceita somente snapshot do backend canônico."
        }
        val previous = timelineCanonicalCache0494()
        val previousById = previous?.trips.orEmpty().associateBy { state ->
            state.canonicalTripId.ifBlank { state.remoteTripId }
        }
        val accepted = incoming.trips
            .map { state ->
                val canonicalId = state.canonicalTripId.ifBlank { state.remoteTripId }
                val old = previousById[canonicalId]
                if (old != null && old.canonicalRevision > state.canonicalRevision) {
                    UnifiedDebugEventStore.record(
                        "TIMELINE_CANONICAL_STALE_REJECTED_0494",
                        appContext.packageName,
                        "canonicalTripId=${seatSyncDiagnosticKey(canonicalId)} incomingRevision=${state.canonicalRevision} cachedRevision=${old.canonicalRevision}",
                    )
                    old
                } else {
                    state
                }
            }
            .distinctBy { state -> state.canonicalTripId.ifBlank { state.remoteTripId } }
        val normalized = incoming.copy(
            trips = accepted,
            source = "CANONICAL_BACKEND",
            snapshotAtMillis = incoming.snapshotAtMillis.coerceAtLeast(nowMillis),
        )
        require(
            prefs.edit()
                .putString(timelineCanonicalCacheKey0494, json.encodeToString(normalized))
                .putLong(timelineCanonicalCacheUpdatedAtKey0494, nowMillis)
                .commit(),
        ) { "Falha ao persistir cache da Timeline canônica." }
        UnifiedDebugEventStore.record(
            "TIMELINE_CANONICAL_CACHE_COMMITTED_0494",
            appContext.packageName,
            "trips=${accepted.size} source=CANONICAL_BACKEND snapshotAt=${normalized.snapshotAtMillis}",
        )
        normalized
    }

    fun getTrip(id: String): Trip? = trips().firstOrNull { it.id == id }

    internal fun recordPublicationCommitted0411(
        canonicalTripId: String,
        publicationRevision: Long,
        publicationEventId: String,
        tombstone: Boolean,
    ): Trip? = synchronized(CANONICAL_LOCK) {
        if (canonicalTripId.isBlank() || publicationRevision <= 0L) return@synchronized getTrip(canonicalTripId)
        val current = trips()
        val existing = current.firstOrNull { it.id == canonicalTripId } ?: return@synchronized null
        if (publicationRevision < existing.publicationRevision) {
            UnifiedDebugEventStore.record(
                "PUBLICATION_METADATA_STALE_REJECTED_0411",
                appContext.packageName,
                "tenantId=${tenantScope.tenantId} internalTripId=${seatSyncDiagnosticKey(canonicalTripId)} incomingRevision=$publicationRevision currentRevision=${existing.publicationRevision}",
            )
            return@synchronized existing
        }
        val nextState = if (tombstone) {
            PublicMirrorAttestationState0411.UNPROVEN
        } else if (existing.publicMirrorAttestationCurrent0411()) {
            // A transport replay of the exact same logical canonical snapshot does not
            // invalidate evidence already proven by independent public readback.
            PublicMirrorAttestationState0411.VALIDATED
        } else {
            PublicMirrorAttestationState0411.PENDING
        }
        val updated = existing.copy(
            publicationRevision = publicationRevision,
            publicationTombstone = tombstone,
            publicationEventId = publicationEventId.take(120),
            publicMirrorAttestationState0411 = nextState,
            publicMirrorAttestationReason0411 = if (tombstone) "PUBLICATION_TOMBSTONED" else "PUBLICATION_COMMITTED_AWAITING_READBACK",
            updatedAtMillis = System.currentTimeMillis(),
        )
        if (updated != existing) persistCanonicalTrip0406(updated, current)
        updated
    }

    internal fun recordPublicMirrorAttestation0411(
        canonicalTripId: String,
        expectedCanonicalRevision: Long,
        expectedPublicationRevision: Long,
        state: PublicMirrorAttestationState0411,
        expectedHash: String,
        readbackHash: String,
        mismatchFields: List<String>,
        reason: String,
        readbackLatencyMillis: Long,
        publicUrlFromReadback: String? = null,
        publicIdentityFromReadback0421: String = "",
        readbackCanonicalRevision0421: Long = 0L,
        readbackPublicationRevision0421: Long = 0L,
        evidenceId0421: String = "",
        traceId0421: String = "",
        expectedBytes0421: Int = 0,
        actualBytes0421: Int = 0,
        firstDifferentByteOffset0421: Int = -1,
        differentByteRanges0421: List<String> = emptyList(),
        fieldDiffs0422: List<String> = emptyList(),
        httpStatus0421: Int = 0,
        backendErrorCode0421: String = "",
        failedStage0421: String = "",
        networkCallId0421: String = "",
        requestBytes0421: Int = 0,
        responseBytes0421: Int = 0,
        requestHash0421: String = "",
        responseHash0421: String = "",
        readbackAtMillis0421: Long = 0L,
        nowMillis: Long = System.currentTimeMillis(),
    ): Trip? = synchronized(CANONICAL_LOCK) {
        val current = trips()
        val existing = current.firstOrNull { it.id == canonicalTripId } ?: return@synchronized null
        if (existing.canonicalRevision != expectedCanonicalRevision) {
            val invalidated = existing.invalidatePublicMirror0411("CANONICAL_REVISION_CHANGED_DURING_READBACK")
            if (invalidated != existing) persistCanonicalTrip0406(invalidated, current)
            return@synchronized invalidated
        }
        val updated = existing.copy(
            publicUrl = existing.publicUrl?.takeIf(String::isNotBlank)
                ?: publicUrlFromReadback?.trim()?.takeIf(String::isNotBlank),
            publicMirrorAttestationState0411 = state,
            publicMirrorAttestedCanonicalRevision0411 = if (state == PublicMirrorAttestationState0411.VALIDATED) expectedCanonicalRevision else 0L,
            publicMirrorAttestedPublicationRevision0411 = if (state == PublicMirrorAttestationState0411.VALIDATED) readbackPublicationRevision0421.coerceAtLeast(expectedPublicationRevision) else 0L,
            publicMirrorExpectedHash0411 = expectedHash.take(96),
            publicMirrorReadbackHash0411 = readbackHash.take(96),
            publicMirrorAttestedAtMillis0411 = if (state == PublicMirrorAttestationState0411.VALIDATED) nowMillis else 0L,
            publicMirrorReadbackLatencyMillis0411 = readbackLatencyMillis.coerceAtLeast(0L),
            publicMirrorAttestationReason0411 = reason.take(160),
            publicMirrorMismatchFields0411 = mismatchFields.distinct().take(24),
            publicMirrorReadbackCanonicalRevision0421 = readbackCanonicalRevision0421.coerceAtLeast(0L),
            publicMirrorAttemptedPublicationRevision0421 = expectedPublicationRevision.coerceAtLeast(0L),
            publicMirrorReadbackPublicationRevision0421 = readbackPublicationRevision0421.coerceAtLeast(0L),
            publicMirrorPublicIdentity0421 = publicIdentityFromReadback0421.take(180),
            publicMirrorLastReadbackAtMillis0421 = readbackAtMillis0421.coerceAtLeast(0L),
            publicMirrorEvidenceId0421 = evidenceId0421.take(80),
            publicMirrorTraceId0421 = traceId0421.take(120),
            publicMirrorExpectedBytes0421 = expectedBytes0421.coerceAtLeast(0),
            publicMirrorActualBytes0421 = actualBytes0421.coerceAtLeast(0),
            publicMirrorFirstDifferentByteOffset0421 = firstDifferentByteOffset0421,
            publicMirrorDifferentByteRanges0421 = differentByteRanges0421.distinct().take(12),
            publicMirrorFieldDiffs0422 = fieldDiffs0422.distinct().take(24),
            publicMirrorHttpStatus0421 = httpStatus0421.coerceAtLeast(0),
            publicMirrorBackendErrorCode0421 = backendErrorCode0421.take(80),
            publicMirrorFailedStage0421 = failedStage0421.take(80),
            publicMirrorNetworkCallId0421 = networkCallId0421.take(120),
            publicMirrorRequestBytes0421 = requestBytes0421.coerceAtLeast(0),
            publicMirrorResponseBytes0421 = responseBytes0421.coerceAtLeast(0),
            publicMirrorRequestHash0421 = requestHash0421.take(96),
            publicMirrorResponseHash0421 = responseHash0421.take(96),
        )
        if (updated != existing) persistCanonicalTrip0406(updated, current)
        updated
    }

    internal fun recordPublicMirrorPublicationFailure0421(
        canonicalTripId: String,
        expectedCanonicalRevision: Long,
        transportRevision: Long,
        evidenceId: String,
        traceId: String,
        retryable: Boolean,
        httpStatus: Int,
        backendErrorCode: String,
        networkCallId: String,
        requestBytes: Int,
        responseBytes: Int,
        requestHash: String,
        responseHash: String,
        reason: String,
        failedStage: String = "",
    ): Trip? = synchronized(CANONICAL_LOCK) {
        val current = trips()
        val existing = current.firstOrNull { it.id == canonicalTripId } ?: return@synchronized null
        if (existing.canonicalRevision != expectedCanonicalRevision) return@synchronized existing
        if (existing.publicMirrorAttestationCurrent0411()) return@synchronized existing
        val resolvedReason = backendErrorCode.ifBlank { reason.ifBlank { "PUBLICATION_FAILED" } }.take(160)
        val updated = existing.copy(
            publicMirrorAttestationState0411 = if (retryable) {
                PublicMirrorAttestationState0411.PENDING
            } else {
                PublicMirrorAttestationState0411.DIVERGENT
            },
            publicMirrorAttestedCanonicalRevision0411 = 0L,
            publicMirrorAttestedPublicationRevision0411 = 0L,
            publicMirrorExpectedHash0411 = "",
            publicMirrorReadbackHash0411 = "",
            publicMirrorAttestedAtMillis0411 = 0L,
            publicMirrorReadbackLatencyMillis0411 = 0L,
            publicMirrorAttestationReason0411 = resolvedReason,
            publicMirrorMismatchFields0411 = listOf("publication"),
            publicMirrorReadbackCanonicalRevision0421 = 0L,
            publicMirrorAttemptedPublicationRevision0421 = transportRevision.coerceAtLeast(0L),
            publicMirrorReadbackPublicationRevision0421 = 0L,
            publicMirrorPublicIdentity0421 = "",
            publicMirrorLastReadbackAtMillis0421 = 0L,
            publicMirrorEvidenceId0421 = evidenceId.take(80),
            publicMirrorTraceId0421 = traceId.take(120),
            publicMirrorExpectedBytes0421 = 0,
            publicMirrorActualBytes0421 = 0,
            publicMirrorFirstDifferentByteOffset0421 = -1,
            publicMirrorDifferentByteRanges0421 = emptyList(),
            publicMirrorHttpStatus0421 = httpStatus.coerceAtLeast(0),
            publicMirrorBackendErrorCode0421 = backendErrorCode.take(80),
            publicMirrorFailedStage0421 = failedStage.ifBlank {
                if (httpStatus > 0) "HTTP_RESPONSE" else if (networkCallId.isNotBlank() || requestBytes > 0) "HTTP_SEND" else "OUTBOX_FAILURE"
            }.take(80),
            publicMirrorNetworkCallId0421 = networkCallId.take(120),
            publicMirrorRequestBytes0421 = requestBytes.coerceAtLeast(0),
            publicMirrorResponseBytes0421 = responseBytes.coerceAtLeast(0),
            publicMirrorRequestHash0421 = requestHash.take(96),
            publicMirrorResponseHash0421 = responseHash.take(96),
        )
        if (updated != existing) persistCanonicalTrip0406(updated, current)
        updated
    }

    fun publicExternalBindings(): List<PublicExternalTripBinding> =
        decode<List<PublicExternalTripBinding>>(prefs.getString(publicExternalBindingsKey, null)).orEmpty()
            .sortedByDescending(PublicExternalTripBinding::departureAtMillis)

    fun publicExternalBinding(remoteTripId: String): PublicExternalTripBinding? =
        publicExternalBindings().firstOrNull { it.remoteTripId == remoteTripId }

    fun publicExternalBindingForStrongIdentity(profileUuid: String, blablaTripId: String): PublicExternalTripBinding? {
        val profile = profileUuid.trim()
        val tripId = blablaTripId.trim()
        if (profile.isBlank() || tripId.isBlank()) return null
        return publicExternalBindings().firstOrNull {
            it.profileUuid.trim().equals(profile, ignoreCase = true) && it.blablaTripId.trim() == tripId
        }
    }

    fun publicExternalBindingFor(entry: TripTimelineEntry): PublicExternalTripBinding? =
        publicExternalBindings().firstOrNull { it.matches(entry) }

    fun savePublicExternalBinding(binding: PublicExternalTripBinding): PublicExternalTripBinding = synchronized(CANONICAL_LOCK) {
        val normalized = binding.copy(updatedAtMillis = System.currentTimeMillis())
        val current = publicExternalBindings().filterNot {
            it.remoteTripId == normalized.remoteTripId ||
                (normalized.bookingTripId.isNotBlank() && it.bookingTripId == normalized.bookingTripId) ||
                (normalized.profileUuid.isNotBlank() && normalized.blablaTripId.isNotBlank() &&
                    it.profileUuid.equals(normalized.profileUuid, ignoreCase = true) && it.blablaTripId == normalized.blablaTripId)
        }
        require(prefs.edit().putString(publicExternalBindingsKey, json.encodeToString(listOf(normalized) + current)).commit()) {
            "Falha ao persistir vínculo externo canônico."
        }
        normalized
    }


    internal fun promoteExternalIdentity0472(
        targetTripId: String,
        profileUuid: String,
        blablaTripId: String,
        blablaManageUrl: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Trip? {
        val staged = synchronized(CANONICAL_LOCK) {
            val profile = profileUuid.trim()
            val providerTripId = blablaTripId.trim()
            val targetId = targetTripId.trim()
            val strongKey = canonicalBlaBlaTripKey0406(tenantScope.tenantId, profile, providerTripId)
                ?: return@synchronized null
            val current = trips()
            val target = current.firstOrNull { it.id == targetId } ?: return@synchronized null
            val sameStrongIdentity = current.filter { candidate ->
                candidate.id != target.id && candidate.tripKey == strongKey
            }
            if (sameStrongIdentity.any { candidate ->
                    !canonicalProjectionPhysicalIdentityCompatible0421(target, candidate)
                }
            ) {
                throw IllegalStateException("BLABLACAR_IDENTITY_PHYSICAL_CONFLICT_0472")
            }
            val canonicalManage = blablaManageUrl.trim()
            val alreadyStrong =
                target.blablaProfileUuid?.trim()?.equals(profile, ignoreCase = true) == true &&
                    target.blablaTripId?.trim() == providerTripId &&
                    target.blablaManageUrl.orEmpty().trim() == canonicalManage
            if (alreadyStrong && sameStrongIdentity.isEmpty()) return@synchronized target

            val nextRevision = maxOf(
                target.canonicalRevision,
                sameStrongIdentity.maxOfOrNull(Trip::canonicalRevision) ?: 0L,
            ) + 1L
            val promotedWithoutHash = canonicalizeTripIdentity0406(
                target.copy(
                    recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
                    blablaProfileUuid = profile,
                    blablaTripId = providerTripId,
                    blablaManageUrl = canonicalManage.takeIf(String::isNotBlank),
                    canonicalRevision = nextRevision,
                    canonicalStateHash = "",
                    updatedAtMillis = nowMillis,
                ).invalidatePublicMirror0411("BLABLACAR_IDENTITY_PROMOTED_0472"),
            )
            val promoted = promotedWithoutHash.copy(
                canonicalStateHash = canonicalTripStateHash0406(
                    promotedWithoutHash,
                    bookingsFor(target.id),
                ),
            )
            // Preserve any pre-existing strong candidate for one reconciliation pass.
            // The established reconciler deterministically keeps this higher-revision
            // target and remaps duplicate bookings/bindings instead of orphaning them.
            val stagedTrips = listOf(promoted) + current.filterNot { it.id == target.id }
            require(prefs.edit().putString(tripsKey, json.encodeToString(stagedTrips)).commit()) {
                "Falha ao preparar promoção da identidade externa."
            }
            promoted
        } ?: return null

        reconcileCanonicalIntegrity0406(nowMillis)
        return getTrip(staged.id)
    }

    fun saveTrip(trip: Trip): Trip = synchronized(CANONICAL_LOCK) {
        val keyedIncoming = canonicalizeTripIdentity0406(trip.normalizedRecordOrigin()).let { keyed ->
            if (keyed.publicTimezoneId0411.isBlank()) keyed.copy(publicTimezoneId0411 = ZoneId.systemDefault().id) else keyed
        }
        val allTrips = trips()
        val existingById = allTrips.firstOrNull { it.id == keyedIncoming.id }
        val existingByStrongKey = keyedIncoming.tripKey.takeIf(String::isNotBlank)?.let { key ->
            allTrips.firstOrNull { it.tripKey == key }
        }
        val existing = existingById ?: existingByStrongKey
        val incoming = if (existing != null && existing.id != keyedIncoming.id) {
            keyedIncoming.copy(id = existing.id, createdAtMillis = existing.createdAtMillis)
        } else {
            keyedIncoming
        }
        if (existing != null && existing.canonicalRevision > 0L && incoming.canonicalRevision < existing.canonicalRevision) {
            UnifiedDebugEventStore.record(
                "TRIP_CANONICAL_WRITE",
                appContext.packageName,
                "tenantId=" + tenantScope.tenantId +
                    " internalTripId=" + incoming.id +
                    " source=TripStore oldRevision=" + existing.canonicalRevision +
                    " newRevision=" + incoming.canonicalRevision +
                    " changedFields=unknown publicationTarget=LOCAL result=SKIP_STALE_REVISION" +
                    " reason=older_local_snapshot configVersion=" + existing.seatAllocationVersionUsed,
            )
            return@synchronized existing
        }
        val semanticChanged = existing == null || canonicalTripComparable0395(existing) != canonicalTripComparable0395(incoming)
        if (existing != null && !semanticChanged && incoming.canonicalRevision <= existing.canonicalRevision) {
            val incomingGeneration = incoming.lastCollectionGeneration
            val metadata = existing.copy(
                tripKey = incoming.tripKey.ifBlank { existing.tripKey },
                canonicalStateHash = existing.canonicalStateHash.ifBlank {
                    canonicalTripStateHash0406(existing.copy(tripKey = incoming.tripKey.ifBlank { existing.tripKey }), bookingsFor(existing.id))
                },
                publicTimezoneId0411 = incoming.publicTimezoneId0411.ifBlank { existing.publicTimezoneId0411 },
                publicMirrorAttestationState0411 = existing.publicMirrorAttestationState0411,
                publicMirrorAttestedCanonicalRevision0411 = existing.publicMirrorAttestedCanonicalRevision0411,
                publicMirrorAttestedPublicationRevision0411 = existing.publicMirrorAttestedPublicationRevision0411,
                publicMirrorExpectedHash0411 = existing.publicMirrorExpectedHash0411,
                publicMirrorReadbackHash0411 = existing.publicMirrorReadbackHash0411,
                publicMirrorAttestedAtMillis0411 = existing.publicMirrorAttestedAtMillis0411,
                publicMirrorReadbackLatencyMillis0411 = existing.publicMirrorReadbackLatencyMillis0411,
                publicMirrorAttestationReason0411 = existing.publicMirrorAttestationReason0411.take(160),
                publicMirrorMismatchFields0411 = existing.publicMirrorMismatchFields0411.distinct().take(24),
                lastCollectionGeneration = maxOf(existing.lastCollectionGeneration, incomingGeneration),
                lastCollectionRunId = if (
                    incoming.lastCollectionRunId.isNotBlank() &&
                    incomingGeneration >= existing.lastCollectionGeneration
                ) incoming.lastCollectionRunId else existing.lastCollectionRunId,
                lastObservedAtMillis = maxOf(existing.lastObservedAtMillis, incoming.lastObservedAtMillis),
            )
            if (metadata != existing) persistCanonicalTrip0406(metadata, allTrips)
            return@synchronized metadata
        }
        val nextRevision = if (existing == null) {
            maxOf(1L, incoming.canonicalRevision)
        } else {
            nextCanonicalTripRevision0395(existing.canonicalRevision, incoming.canonicalRevision, semanticChanged)
        }
        val attestationState = when {
            incoming.deleted || incoming.publicationTombstone -> PublicMirrorAttestationState0411.UNPROVEN
            semanticChanged -> PublicMirrorAttestationState0411.PENDING
            else -> incoming.publicMirrorAttestationState0411
        }
        val normalizedWithoutHash = incoming.copy(
            canonicalRevision = nextRevision,
            canonicalStateHash = "",
            publicMirrorAttestationState0411 = attestationState,
            publicMirrorAttestedCanonicalRevision0411 = if (semanticChanged) 0L else incoming.publicMirrorAttestedCanonicalRevision0411,
            publicMirrorAttestedPublicationRevision0411 = if (semanticChanged) 0L else incoming.publicMirrorAttestedPublicationRevision0411,
            publicMirrorExpectedHash0411 = if (semanticChanged) "" else incoming.publicMirrorExpectedHash0411,
            publicMirrorReadbackHash0411 = if (semanticChanged) "" else incoming.publicMirrorReadbackHash0411,
            publicMirrorAttestedAtMillis0411 = if (semanticChanged) 0L else incoming.publicMirrorAttestedAtMillis0411,
            publicMirrorReadbackLatencyMillis0411 = if (semanticChanged) 0L else incoming.publicMirrorReadbackLatencyMillis0411,
            publicMirrorAttestationReason0411 = if (semanticChanged) "CANONICAL_REVISION_CHANGED" else incoming.publicMirrorAttestationReason0411,
            publicMirrorMismatchFields0411 = if (semanticChanged) emptyList() else incoming.publicMirrorMismatchFields0411,
            updatedAtMillis = System.currentTimeMillis(),
        )
        val normalized = normalizedWithoutHash.copy(
            canonicalStateHash = canonicalTripStateHash0406(
                normalizedWithoutHash,
                bookingsFor(normalizedWithoutHash.id),
            ),
        )
        persistCanonicalTrip0406(normalized, allTrips)
        UnifiedDebugEventStore.record(
            "TRIP_CANONICAL_WRITE",
            appContext.packageName,
            "tenantId=" + tenantScope.tenantId +
                " internalTripId=" + normalized.id +
                " tripKeyPresent=" + normalized.tripKey.isNotBlank() +
                " stateHash=" + normalized.canonicalStateHash.takeLast(12) +
                " source=TripStore oldRevision=" + (existing?.canonicalRevision ?: 0L) +
                " newRevision=" + normalized.canonicalRevision +
                " changedFields=trip publicationTarget=LOCAL result=UPDATE" +
                " reason=canonical_mutation configVersion=" + normalized.seatAllocationVersionUsed,
        )
        normalized
    }

    private fun canonicalizeTripIdentity0406(trip: Trip): Trip {
        val key = when (resolvedTripRecordOrigin(trip)) {
            TripRecordOrigin.EXTERNAL_BACKING -> canonicalBlaBlaTripKey0406(
                tenantId = tenantScope.tenantId,
                profileUuid = trip.blablaProfileUuid,
                providerTripId = trip.blablaTripId,
            )
            TripRecordOrigin.LOCAL -> canonicalLocalTripKey0406(tenantScope.tenantId, trip.id)
        }
        return if (key.isNullOrBlank() || trip.tripKey == key) trip else trip.copy(tripKey = key)
    }

    private fun persistCanonicalTrip0406(trip: Trip, current: List<Trip>) {
        val next = current.filterNot { existing ->
            existing.id == trip.id || (trip.tripKey.isNotBlank() && existing.tripKey == trip.tripKey)
        }
        require(prefs.edit().putString(tripsKey, json.encodeToString(listOf(trip) + next)).commit()) {
            "Falha ao persistir estado canônico da viagem."
        }
    }

    private fun canonicalTripComparable0395(trip: Trip): Trip = trip.copy(
        canonicalRevision = 0L,
        canonicalStateHash = "",
        tripKey = "",
        publicMirrorAttestationState0411 = PublicMirrorAttestationState0411.UNPROVEN,
        publicMirrorAttestedCanonicalRevision0411 = 0L,
        publicMirrorAttestedPublicationRevision0411 = 0L,
        publicMirrorExpectedHash0411 = "",
        publicMirrorReadbackHash0411 = "",
        publicMirrorAttestedAtMillis0411 = 0L,
        publicMirrorReadbackLatencyMillis0411 = 0L,
        publicMirrorAttestationReason0411 = "",
        publicMirrorMismatchFields0411 = emptyList(),
        lastCollectionRunId = "",
        lastCollectionGeneration = 0L,
        lastObservedAtMillis = 0L,
        deletedAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    internal fun reconcileCanonicalIntegrity0406(nowMillis: Long = System.currentTimeMillis()): CanonicalIntegrityReport0406 =
        synchronized(CANONICAL_LOCK) {
            val originalTrips = trips()
            val keyedTrips = originalTrips.map(::canonicalizeTripIdentity0406)
            val strongIdentityGroups0421 = keyedTrips
                .mapNotNull { trip ->
                    canonicalBlaBlaTripKey0406(
                        tenantId = tenantScope.tenantId,
                        profileUuid = trip.blablaProfileUuid,
                        providerTripId = trip.blablaTripId,
                    )?.let { key -> key to trip }
                }
                .groupBy({ it.first }, { it.second })
            val conflictStrongKeys0421 = strongIdentityGroups0421
                .filterValues { candidates ->
                    candidates.size > 1 && candidates.any { left ->
                        candidates.any { right ->
                            left.id != right.id && !canonicalProjectionPhysicalIdentityCompatible0421(left, right)
                        }
                    }
                }
                .keys
            val loserToWinner = mutableMapOf<String, String>()
            strongIdentityGroups0421.forEach { (key, candidates) ->
                if (candidates.size <= 1 || key in conflictStrongKeys0421) return@forEach
                val winner = candidates.maxWithOrNull(
                    compareBy<Trip> { resolvedTripRecordOrigin(it) == TripRecordOrigin.LOCAL }
                        .thenBy { it.canonicalRevision }
                        .thenBy { it.updatedAtMillis },
                ) ?: return@forEach
                candidates.filterNot { it.id == winner.id }.forEach { loser ->
                    loserToWinner[loser.id] = winner.id
                }
            }
            if (conflictStrongKeys0421.isNotEmpty()) {
                val conflictIds = strongIdentityGroups0421
                    .filterKeys { it in conflictStrongKeys0421 }
                    .values
                    .flatten()
                    .map { seatSyncDiagnosticKey(it.id) }
                    .distinct()
                    .take(24)
                UnifiedDebugEventStore.record(
                    "CANONICAL_STRONG_IDENTITY_CONFLICT_0421",
                    appContext.packageName,
                    "tenantId=" + tenantScope.tenantId +
                        " conflictKeys=" + conflictStrongKeys0421.size +
                        " affectedTrips=" + conflictIds.size +
                        " tripKeys=" + conflictIds.joinToString(",") +
                        " action=preserve_unproven_no_destructive_merge",
                )
            }
            val originalBookings = bookings()
            val remappedBookings = originalBookings.map { booking ->
                loserToWinner[booking.tripId]?.let { winnerId -> booking.copy(tripId = winnerId) } ?: booking
            }
            val retainedTrips = keyedTrips.filterNot { it.id in loserToWinner }
            val bookingsByTrip = remappedBookings.groupBy(Booking::tripId)
            val activeTimezone = ZoneId.systemDefault().id
            val hashedTrips = retainedTrips.map { trip ->
                val identityConflict = canonicalBlaBlaTripKey0406(
                    tenantId = tenantScope.tenantId,
                    profileUuid = trip.blablaProfileUuid,
                    providerTripId = trip.blablaTripId,
                ) in conflictStrongKeys0421
                val conflictAware = if (identityConflict) {
                    trip.invalidatePublicMirror0411("STRONG_IDENTITY_CONFLICT_0421")
                } else trip
                val authoritativePublicUrl0490 = if (identityConflict) {
                    null
                } else {
                    canonicalCollectorAuthorityPublicUrl0490(conflictAware)
                }
                val currentPublicUrl0490 = canonicalBoundBlaBlaPublicUrl0423(
                    conflictAware.blablaPublicUrl,
                    conflictAware.blablaTripId,
                )
                val publicUrlNeedsRepair0490 =
                    authoritativePublicUrl0490 != null && authoritativePublicUrl0490 != currentPublicUrl0490
                val timezoneNeedsMigration = conflictAware.publicTimezoneId0411.isBlank()
                val migrated = if (timezoneNeedsMigration || publicUrlNeedsRepair0490) {
                    conflictAware.copy(
                        publicTimezoneId0411 = conflictAware.publicTimezoneId0411.ifBlank { activeTimezone },
                        blablaPublicUrl = authoritativePublicUrl0490 ?: conflictAware.blablaPublicUrl,
                        canonicalRevision = conflictAware.canonicalRevision.coerceAtLeast(0L) + 1L,
                        canonicalStateHash = "",
                        updatedAtMillis = nowMillis,
                    ).invalidatePublicMirror0411(
                        if (publicUrlNeedsRepair0490) "BLABLACAR_PUBLIC_URL_RECONCILED_0490"
                        else "LEGACY_PUBLIC_TIMEZONE_MIGRATED",
                    )
                } else {
                    conflictAware
                }
                val stateHash = canonicalTripStateHash0406(migrated, bookingsByTrip[migrated.id].orEmpty())
                if (migrated.canonicalStateHash == stateHash) migrated else migrated.copy(canonicalStateHash = stateHash)
            }
            val canonicalByKey = hashedTrips.filter { it.tripKey.isNotBlank() }.associateBy(Trip::tripKey)
            val canonicalByStrongExternalKey0421 = hashedTrips
                .mapNotNull { trip ->
                    canonicalBlaBlaTripKey0406(
                        tenantId = tenantScope.tenantId,
                        profileUuid = trip.blablaProfileUuid,
                        providerTripId = trip.blablaTripId,
                    )?.let { key -> key to trip }
                }
                .groupBy({ it.first }, { it.second })
                .filterKeys { it !in conflictStrongKeys0421 }
                .mapNotNull { (key, candidates) -> candidates.singleOrNull()?.let { key to it } }
                .toMap()
            val knownStrongExternalKeys0421 = strongIdentityGroups0421.keys
            val originalBindings = publicExternalBindings()
            val normalizedBindings = originalBindings.map { binding ->
                val key = canonicalBlaBlaTripKey0406(
                    tenantId = tenantScope.tenantId,
                    profileUuid = binding.profileUuid,
                    providerTripId = binding.blablaTripId,
                )
                val canonical = key?.let { canonicalByStrongExternalKey0421[it] ?: canonicalByKey[it] }
                val canonicalPublicHref0490 = canonical?.let { trip ->
                    canonicalBoundBlaBlaPublicUrl0423(trip.blablaPublicUrl, trip.blablaTripId)
                }.orEmpty()
                val bindingPublicHrefNeedsRepair0490 =
                    canonicalPublicHref0490.isNotBlank() &&
                        canonicalBoundBlaBlaPublicUrl0423(binding.blablaPublicHref, binding.blablaTripId) != canonicalPublicHref0490
                if (canonical != null && binding.bookingTripId != canonical.id) {
                    binding.copy(
                        bookingTripId = canonical.id,
                        blablaPublicHref = canonicalPublicHref0490.ifBlank { binding.blablaPublicHref },
                        canonicalRevision = canonical.canonicalRevision.coerceAtLeast(0L),
                        stateHash = canonical.canonicalStateHash,
                        updatedAtMillis = nowMillis,
                    )
                } else if (
                    canonical != null &&
                    (binding.stateHash != canonical.canonicalStateHash ||
                        binding.canonicalRevision != canonical.canonicalRevision ||
                        bindingPublicHrefNeedsRepair0490)
                ) {
                    binding.copy(
                        blablaPublicHref = canonicalPublicHref0490.ifBlank { binding.blablaPublicHref },
                        canonicalRevision = canonical.canonicalRevision.coerceAtLeast(0L),
                        stateHash = canonical.canonicalStateHash,
                        updatedAtMillis = nowMillis,
                    )
                } else binding
            }
            val groupedBindings = normalizedBindings.groupBy { binding ->
                val key = canonicalBlaBlaTripKey0406(
                    tenantId = tenantScope.tenantId,
                    profileUuid = binding.profileUuid,
                    providerTripId = binding.blablaTripId,
                )
                if (key != null && key !in conflictStrongKeys0421) key else "remote:" + binding.remoteTripId
            }
            val orderedBindingGroups = groupedBindings.values.map { candidates ->
                candidates.sortedWith(
                    compareByDescending<PublicExternalTripBinding> { it.canonicalRevision }
                        .thenByDescending { it.updatedAtMillis },
                )
            }
            val dedupedBindings = orderedBindingGroups.map { it.first() }
            val duplicateBindingsForCleanup = orderedBindingGroups.flatMap { ordered ->
                val keeperRemoteId = ordered.first().remoteTripId
                ordered.drop(1)
                    .filter { it.remoteTripId.isNotBlank() && it.remoteTripId != keeperRemoteId }
            }.distinctBy(PublicExternalTripBinding::remoteTripId)
            val orphanBindingsForCleanup = dedupedBindings.filter { binding ->
                val key = canonicalBlaBlaTripKey0406(
                    tenantId = tenantScope.tenantId,
                    profileUuid = binding.profileUuid,
                    providerTripId = binding.blablaTripId,
                )
                key != null && key !in knownStrongExternalKeys0421
            }
            val changed = hashedTrips != originalTrips ||
                remappedBookings != originalBookings ||
                dedupedBindings != originalBindings
            if (changed) {
                require(
                    prefs.edit()
                        .putString(tripsKey, json.encodeToString(hashedTrips))
                        .putString(bookingsKey, json.encodeToString(remappedBookings))
                        .putString(publicExternalBindingsKey, json.encodeToString(dedupedBindings))
                        .commit(),
                ) { "Falha ao migrar integridade canônica." }
            }
            CanonicalIntegrityReport0406(
                canonicalTrips = hashedTrips.size,
                duplicateCanonicalTrips = loserToWinner.size,
                migratedBookings = originalBookings.indices.count { index -> originalBookings[index].tripId != remappedBookings[index].tripId },
                duplicateAgendaBindings = (originalBindings.size - dedupedBindings.size).coerceAtLeast(0),
                orphanAgendaBindings = orphanBindingsForCleanup.size,
                unresolvedExternalIdentity = hashedTrips.count {
                    resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING && it.tripKey.isBlank()
                },
                consolidatedStrongIdentityTrips0421 = loserToWinner.size,
                strongIdentityConflicts0421 = conflictStrongKeys0421.size,
                duplicateAgendaBindingsForCleanup = duplicateBindingsForCleanup,
                orphanAgendaBindingsForCleanup = orphanBindingsForCleanup,
            )
        }

    fun tombstoneExternalTrip0406(
        canonicalTripId: String,
        collectionRunId: String,
        collectionGeneration: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Trip? = synchronized(CANONICAL_LOCK) {
        val current = getTrip(canonicalTripId) ?: return@synchronized null
        if (resolvedTripRecordOrigin(current) != TripRecordOrigin.EXTERNAL_BACKING) return@synchronized null
        if (collectionGeneration > 0L && current.lastCollectionGeneration > collectionGeneration) return@synchronized current
        if (current.deleted) return@synchronized current
        saveTrip(
            current.copy(
                status = TripStatus.CANCELLED,
                publicBookingEnabled = false,
                deleted = true,
                deletedAtMillis = nowMillis,
                lastCollectionRunId = collectionRunId.take(160),
                lastCollectionGeneration = maxOf(current.lastCollectionGeneration, collectionGeneration),
                lastObservedAtMillis = maxOf(current.lastObservedAtMillis, nowMillis),
            ),
        )
    }

    /**
     * Reconciles active/future trips to the channel-derived inventory. The old
     * vehicle_capacity preference is intentionally absent from this calculation.
     */
    fun reconcileOperationalInventory(
        rotaCertaSeatAllocation: Int,
        nowMillis: Long = System.currentTimeMillis(),
        seatAllocationVersion: Long = 0L,
    ): Pair<Int, Int> = reconcileOperationalInventoryTripIds(
        rotaCertaSeatAllocation = rotaCertaSeatAllocation,
        seatAllocationVersion = seatAllocationVersion,
        nowMillis = nowMillis,
    ).size to 0

    internal fun reconcileOperationalInventoryTripIds(
        rotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<String> = synchronized(CANONICAL_LOCK) {
        require(rotaCertaSeatAllocation in 0..999) { "Vagas do Rota Certa inválidas." }
        require(seatAllocationVersion >= 0L) { "Versão de vagas do Rota Certa inválida." }
        val activeStatuses = setOf(
            TripStatus.DRAFT,
            TripStatus.PUBLISHED,
            TripStatus.FULL,
            TripStatus.STARTING,
            TripStatus.ACTIVE,
        )
        val bookingsByTrip = bookings().groupBy(Booking::tripId)
        val changedTripIds = linkedSetOf<String>()
        val currentTrips = trips()
        val reconciledTrips = currentTrips.map { trip ->
            val shouldApply = trip.status in activeStatuses &&
                (trip.departureAtMillis >= nowMillis || trip.status in setOf(TripStatus.STARTING, TripStatus.ACTIVE))
            // 0.1.416: this tenant value is migration-only. Explicit per-trip allocation is canonical
            // and must never be overwritten by a later settings save or background cycle.
            if (!shouldApply || trip.rotaCertaSeatAllocation != null) {
                trip
            } else {
                val withAllocation = trip.copy(
                    rotaCertaSeatAllocation = rotaCertaSeatAllocation,
                    seatAllocationVersionUsed = maxOf(trip.seatAllocationVersionUsed, seatAllocationVersion),
                )
                val derivedCapacity = operationalInventoryCapacity(withAllocation, bookingsByTrip[trip.id].orEmpty())
                changedTripIds += trip.id
                val updated = withAllocation.copy(
                    capacity = derivedCapacity,
                    canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L) + 1L,
                    canonicalStateHash = "",
                    updatedAtMillis = nowMillis,
                ).invalidatePublicMirror0411("SEAT_ALLOCATION_CHANGED")
                updated.copy(
                    canonicalStateHash = canonicalTripStateHash0406(
                        updated,
                        bookingsByTrip[trip.id].orEmpty(),
                    ),
                )
            }
        }
        if (changedTripIds.isNotEmpty()) {
            require(prefs.edit().putString(tripsKey, json.encodeToString(reconciledTrips)).commit()) {
                "Falha ao persistir migração canônica de vagas por viagem."
            }
        }
        changedTripIds
    }

    @Deprecated("Legacy compatibility only; vehicle capacity no longer drives trip inventory.")
    fun reconcilePhysicalPassengerCapacity(
        @Suppress("UNUSED_PARAMETER") capacity: Int,
        rotaCertaSeatAllocation: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
    ): Pair<Int, Int> = reconcileOperationalInventory(rotaCertaSeatAllocation, nowMillis)

    fun deleteTrip(id: String) {
        prefs.edit()
            .putString(tripsKey, json.encodeToString(trips().filterNot { it.id == id }))
            .putString(bookingsKey, json.encodeToString(bookings().filterNot { it.tripId == id }))
            .apply()
    }

    /**
     * Historical compatibility shim. Timeline cleanup must never delete canonical local trips,
     * bookings or passenger history. Visual cleanup is handled by TripTimelineArchiveStore.
     */
    fun clearTimelineLocalData(): Pair<Int, Int> = 0 to 0

    fun saveBooking(booking: Booking): Booking =
        saveBookingsBatch(listOf(booking), preserveSourceUpdatedAt = false).single()

    /**
     * Persists a reconcile diff as one coherent booking snapshot. Remote imports keep
     * the server updatedAt value so an unchanged reservation compares equal on the
     * next pull instead of being imported again only because the local clock changed.
     */
    internal fun saveBookingsBatch(
        bookingsToSave: List<Booking>,
        preserveSourceUpdatedAt: Boolean,
    ): List<Booking> = synchronized(CANONICAL_LOCK) {
        if (bookingsToSave.isEmpty()) return emptyList()

        val existingAll = bookings()
        val existingById = existingAll.associateBy(Booking::id)
        val distinctIncoming = LinkedHashMap<String, Booking>().apply {
            bookingsToSave.forEach { put(it.id, it) }
        }.values.toList()

        val prepared = distinctIncoming.map { booking ->
            prepareBookingForPersistence(booking, existingById[booking.id])
        }
        val passengerIds = passengerIdentityStore.ensureLocalBookingProfilesBatch(prepared)
        val now = System.currentTimeMillis()
        val normalized = prepared.map { invariantState ->
            val passengerId = passengerIds[invariantState.id] ?: invariantState.passengerId
            invariantState.copy(
                passengerId = passengerId,
                updatedAtMillis = if (preserveSourceUpdatedAt) {
                    invariantState.updatedAtMillis.takeIf { it > 0L } ?: now
                } else {
                    now
                },
            )
        }

        val changedTripIds = normalized.asSequence()
            .filter { incoming ->
                val existing = existingById[incoming.id]
                existing == null || existing.copy(updatedAtMillis = 0L) != incoming.copy(updatedAtMillis = 0L)
            }
            .map(Booking::tripId)
            .toSet()
        if (changedTripIds.isEmpty()) return@synchronized normalized

        val next = mergeBookingBatch0380(existingAll, normalized)
        require(prefs.edit().putString(bookingsKey, json.encodeToString(next)).commit()) {
            "Falha ao persistir reservas do estado canônico."
        }
        refreshCanonicalTripStateBatch0395(
            tripIds = changedTripIds,
            bookingSnapshot = next,
            nowMillis = now,
        )
        normalized
    }

    internal fun reconcileBookingDerivedInventory(tripIds: Set<String>): Int = synchronized(CANONICAL_LOCK) {
        if (tripIds.isEmpty()) return@synchronized 0
        val allBookings = bookings()
        val bookingsByTrip = allBookings.groupBy(Booking::tripId)
        val currentTrips = trips()
        val now = System.currentTimeMillis()
        var changed = 0
        val nextTrips = currentTrips.map { trip ->
            if (trip.id !in tripIds) {
                trip
            } else {
                val derived = operationalInventoryCapacity(trip, bookingsByTrip[trip.id].orEmpty())
                if (trip.capacity == derived) {
                    trip
                } else {
                    changed++
                    val updated = trip.copy(
                        capacity = derived,
                        canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L) + 1L,
                        canonicalStateHash = "",
                        updatedAtMillis = now,
                    ).invalidatePublicMirror0411("BOOKING_DERIVED_INVENTORY_CHANGED")
                    updated.copy(
                        canonicalStateHash = canonicalTripStateHash0406(
                            updated,
                            bookingsByTrip[trip.id].orEmpty(),
                        ),
                    )
                }
            }
        }
        if (changed > 0) {
            require(prefs.edit().putString(tripsKey, json.encodeToString(nextTrips)).commit()) {
                "Falha ao reconciliar inventário canônico."
            }
        }
        changed
    }

    private fun prepareBookingForPersistence(booking: Booking, existing: Booking?): Booking {
        val withPreservedLocalMetadata = if (
            existing?.localMetadataTouched == true && !booking.localMetadataTouched
        ) {
            booking.copy(
                passengerId = existing.passengerId,
                fareMinorUnits = existing.fareMinorUnits,
                fareCurrencyCode = existing.fareCurrencyCode,
                boardingAddress = existing.boardingAddress,
                dropoffAddress = existing.dropoffAddress,
                localMetadataTouched = true,
            )
        } else {
            booking
        }
        val withPreservedOperationalState = existing?.let { current ->
            val explicitCancellationTombstone =
                current.status == BookingStatus.CANCELLED && current.lastDriverSelection == "CANCELLED"
            withPreservedLocalMetadata.copy(
                status = if (explicitCancellationTombstone) BookingStatus.CANCELLED else withPreservedLocalMetadata.status,
                operationalStatus = when {
                    explicitCancellationTombstone -> PassengerOperationalStatus.CANCELLED
                    withPreservedLocalMetadata.status == BookingStatus.CANCELLED -> PassengerOperationalStatus.CANCELLED
                    withPreservedLocalMetadata.lastDriverSelection.isBlank() &&
                        withPreservedLocalMetadata.operationalStatus == PassengerOperationalStatus.CONFIRMED &&
                        current.operationalStatus != PassengerOperationalStatus.CONFIRMED -> current.operationalStatus
                    else -> withPreservedLocalMetadata.operationalStatus
                },
                paymentStatus = if (
                    withPreservedLocalMetadata.lastDriverSelection.isBlank() &&
                    current.paymentStatus == PassengerPaymentStatus.PAID &&
                    withPreservedLocalMetadata.paymentStatus == PassengerPaymentStatus.UNPAID
                ) PassengerPaymentStatus.PAID else withPreservedLocalMetadata.paymentStatus,
                lastDriverSelection = when {
                    explicitCancellationTombstone -> "CANCELLED"
                    withPreservedLocalMetadata.status == BookingStatus.CANCELLED -> "CANCELLED"
                    else -> withPreservedLocalMetadata.lastDriverSelection.ifBlank { current.lastDriverSelection }
                },
            )
        } ?: withPreservedLocalMetadata
        return if (withPreservedOperationalState.status == BookingStatus.CANCELLED) {
            withPreservedOperationalState.copy(
                operationalStatus = PassengerOperationalStatus.CANCELLED,
                lastDriverSelection = "CANCELLED",
            )
        } else {
            withPreservedOperationalState
        }
    }

    private fun refreshCanonicalTripStateBatch0395(
        tripIds: Set<String>,
        bookingSnapshot: List<Booking>,
        nowMillis: Long,
    ) {
        if (tripIds.isEmpty()) return
        val bookingsByTrip = bookingSnapshot.groupBy(Booking::tripId)
        val currentTrips = trips()
        var changed = false
        val nextTrips = currentTrips.map { trip ->
            if (trip.id !in tripIds) {
                trip
            } else {
                val tripBookings = bookingsByTrip[trip.id].orEmpty()
                val status = SeatAvailabilityEngine.suggestedStatus(trip, tripBookings)
                val capacity = operationalInventoryCapacity(trip, tripBookings)
                changed = true
                val updated = trip.copy(
                    status = status,
                    capacity = capacity,
                    canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L) + 1L,
                    canonicalStateHash = "",
                    updatedAtMillis = nowMillis,
                ).invalidatePublicMirror0411("BOOKING_STATE_CHANGED")
                updated.copy(
                    canonicalStateHash = canonicalTripStateHash0406(updated, tripBookings),
                )
            }
        }
        if (changed) {
            require(prefs.edit().putString(tripsKey, json.encodeToString(nextTrips)).commit()) {
                "Falha ao persistir revisão canônica derivada de reservas."
            }
        }
    }

    fun deleteBooking(id: String): Unit = synchronized(CANONICAL_LOCK) {
        val current = bookings()
        val booking = current.firstOrNull { it.id == id } ?: return@synchronized
        val next = current.filterNot { it.id == id }
        require(prefs.edit().putString(bookingsKey, json.encodeToString(next)).commit()) {
            "Falha ao remover reserva do estado canônico."
        }
        refreshCanonicalTripStateBatch0395(
            tripIds = setOf(booking.tripId),
            bookingSnapshot = next,
            nowMillis = System.currentTimeMillis(),
        )
    }

    fun onlineSettings(): TripOnlineSettings {
        val publicSettings = decode<TripOnlineSettings>(prefs.getString(onlineKey, null)) ?: TripOnlineSettings()
        val stablePublicAgendaToken = publicAgendaLinkStore.currentOrMigrate(publicSettings.publicCalendarToken)
        return publicSettings.copy(
            driverToken = secretStore.driverToken(),
            publicCalendarToken = stablePublicAgendaToken,
        )
    }

    fun saveOnlineSettings(settings: TripOnlineSettings) {
        secretStore.saveDriverToken(settings.driverToken)
        val stablePublicAgendaToken = publicAgendaLinkStore.currentOrMigrate(settings.publicCalendarToken)
        val withoutAdministrativeSecret = settings.copy(
            driverToken = "",
            publicCalendarToken = stablePublicAgendaToken,
        )
        prefs.edit().putString(onlineKey, json.encodeToString(withoutAdministrativeSecret)).apply()
    }

    fun replacePublicAgendaLinkAfterConfirmedRotation(
        expectedCurrent: String,
        replacement: String,
    ): Boolean = publicAgendaLinkStore.replaceAfterConfirmedRotation(expectedCurrent, replacement)

    fun clearOnlineCredentials() {
        secretStore.clear()
        val current = decode<TripOnlineSettings>(prefs.getString(onlineKey, null)) ?: TripOnlineSettings()
        prefs.edit().putString(onlineKey, json.encodeToString(current.copy(driverToken = ""))).apply()
    }

    fun nextPublishedTrip(nowMillis: Long = System.currentTimeMillis()): Trip? = trips()
        .asSequence()
        .filter { it.departureAtMillis >= nowMillis }
        .filter { it.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL, TripStatus.STARTING) }
        .minByOrNull(Trip::departureAtMillis)

    private inline fun <reified T> decode(value: String?): T? = runCatching {
        if (value.isNullOrBlank()) null else json.decodeFromString<T>(value)
    }.getOrNull()

    companion object {
        private val CANONICAL_LOCK = Any()
        private const val PREFS = "rota_certa_trips_stage47"
        private const val KEY_TRIPS = "trips"
        private const val KEY_BOOKINGS = "bookings"
        private const val KEY_ONLINE = "online_settings"
        private const val KEY_PUBLIC_EXTERNAL_BINDINGS = "public_external_bindings_v1"
        private const val KEY_TIMELINE_CANONICAL_CACHE_0494 = "timeline_canonical_backend_cache_v1"
        private const val KEY_TIMELINE_CANONICAL_CACHE_UPDATED_AT_0494 = "timeline_canonical_backend_cache_updated_at_v1"
    }
}

internal data class CanonicalIntegrityReport0406(
    val canonicalTrips: Int = 0,
    val duplicateCanonicalTrips: Int = 0,
    val migratedBookings: Int = 0,
    val duplicateAgendaBindings: Int = 0,
    val orphanAgendaBindings: Int = 0,
    val unresolvedExternalIdentity: Int = 0,
    val consolidatedStrongIdentityTrips0421: Int = 0,
    val strongIdentityConflicts0421: Int = 0,
    val duplicateAgendaBindingsForCleanup: List<PublicExternalTripBinding> = emptyList(),
    val orphanAgendaBindingsForCleanup: List<PublicExternalTripBinding> = emptyList(),
)

@kotlinx.serialization.Serializable
data class PublicExternalTripBinding(
    val remoteTripId: String,
    val publicToken: String,
    val bookingTripId: String,
    val profileUuid: String = "",
    val blablaTripId: String = "",
    val blablaTripHref: String = "",
    val blablaPublicHref: String = "",
    val title: String,
    val departureAtMillis: Long,
    val capacity: Int,
    val stops: List<TripStop>,
    /** Stable tenant-scoped internal identity used by Timeline/Agenda reconciliation. */
    val canonicalRevision: Long = 0L,
    val seatAllocationVersionUsed: Long = 0L,
    val externalFingerprint: String = "",
    val stateHash: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun matches(entry: TripTimelineEntry): Boolean {
        val entryProfile = entry.blablaProfileUuid.orEmpty().trim().lowercase()
        val entryTripId = entry.blablaTripId.orEmpty().trim()
        if (profileUuid.isNotBlank() && blablaTripId.isNotBlank() &&
            entryProfile == profileUuid.trim().lowercase() && entryTripId == blablaTripId.trim()
        ) return true

        val leftHref = blablaTripHref.substringBefore("&search_uuid=").trim()
        val rightHref = entry.blablaTripHref.orEmpty().substringBefore("&search_uuid=").trim()
        if (profileUuid.isNotBlank() && leftHref.isNotBlank() &&
            entryProfile == profileUuid.trim().lowercase() && leftHref == rightHref
        ) return true

        // Route/time similarity is diagnostic only. A public projection binding must
        // never be selected by presentation fields when strong identity is absent.
        return false
    }

    fun asTrip(): Trip = Trip(
        id = bookingTripId,
        title = title,
        departureAtMillis = departureAtMillis,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = stops,
        publicToken = publicToken,
        remoteId = remoteTripId,
        blablaProfileUuid = profileUuid.takeIf(String::isNotBlank),
        blablaTripId = blablaTripId.takeIf(String::isNotBlank),
        blablaManageUrl = blablaTripHref.takeIf(String::isNotBlank),
        blablaPublicUrl = blablaPublicHref.takeIf(String::isNotBlank),
        publicBookingEnabled = true,
        canonicalRevision = canonicalRevision,
        seatAllocationVersionUsed = seatAllocationVersionUsed,
        canonicalStateHash = stateHash,
    )
}

internal fun canonicalProjectionPhysicalIdentityCompatible0421(left: Trip, right: Trip): Boolean {
    if (kotlin.math.abs(left.departureAtMillis - right.departureAtMillis) > 45L * 60L * 1000L) return false
    val leftStops = left.stops.sortedBy(TripStop::order)
    val rightStops = right.stops.sortedBy(TripStop::order)
    if (leftStops.size < 2 || rightStops.size < 2) return false
    return normalizeBindingPlace(leftStops.first().name) == normalizeBindingPlace(rightStops.first().name) &&
        normalizeBindingPlace(leftStops.last().name) == normalizeBindingPlace(rightStops.last().name)
}

private fun normalizeBindingPlace(value: String): String = java.text.Normalizer
    .normalize(value.substringBefore(',').trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

@kotlinx.serialization.Serializable
data class TripOnlineSettings(
    val apiBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val driverToken: String = "",
    val publicCalendarToken: String = "",
    val driverDisplayName: String = "",
    val driverUsername: String = "",
    val driverWhatsapp: String = "",
    val driverPhotoUrl: String = "",
    val driverPublicAbout: String = "",
    val driverPublicRating: String = "",
    val driverPublicReviewCount: Int = 0,
    val driverPublicBadge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val vehicleAmenities: String = "",
    val driverPreferences: String = "",
    val paymentInstructions: String = "",
    val googleCalendarPublicUrl: String = "",
    val publicProfileMode: PublicDriverProfileMode = PublicDriverProfileMode.MANUAL,
    val selectedPublicProfileAccountId: String = "",
    val publicProfileOverrideFields: Set<String> = emptySet(),
) {
    val configured: Boolean
        get() = apiBaseUrl.startsWith("https://") && driverToken.isNotBlank()

    val publicAgendaUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            val username = driverUsername.takeIf(DriverIdentityRules::isValidPublicUsername) ?: return@let null
            publicCalendarToken.takeIf { it.length >= 16 }?.let { "$base/$username" }
        }

    val publicCalendarUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            publicCalendarToken.takeIf { it.length >= 16 }?.let { token ->
                if (DriverIdentityRules.isValidUsername(driverUsername)) "$base/calendar/$driverUsername/$token.ics" else "$base/calendar/$token.ics"
            }
        }

    val googleCalendarMirrorUrl: String?
        get() = googleCalendarPublicUrl.trim().takeIf { it.startsWith("https://") }
}

internal class PublicAgendaLinkStore(
    context: Context,
    tenantScope: TenantStorageScope,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val valueKey = tenantScope.key(KEY_VALUE)
    private val generationKey = tenantScope.key(KEY_GENERATION)

    @Synchronized
    fun currentOrMigrate(legacyValue: String = ""): String {
        val current = prefs.getString(valueKey, "").orEmpty().trim()
        if (current.isNotBlank()) return current
        val candidate = normalize(legacyValue)
        if (candidate.length < MIN_LENGTH) return ""
        prefs.edit().putString(valueKey, candidate).apply()
        return candidate
    }

    @Synchronized
    fun replaceAfterConfirmedRotation(expectedCurrentRaw: String, replacementRaw: String): Boolean {
        val expected = normalize(expectedCurrentRaw)
        val replacement = normalize(replacementRaw)
        if (expected.length < MIN_LENGTH || replacement.length < MIN_LENGTH || replacement == expected) return false
        val current = prefs.getString(valueKey, "").orEmpty().trim()
        if (current != expected) return false
        return prefs.edit()
            .putString(valueKey, replacement)
            .putLong(generationKey, generation() + 1L)
            .commit()
    }

    fun generation(): Long = prefs.getLong(generationKey, 1L).coerceAtLeast(1L)

    private fun normalize(value: String): String =
        value.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(120)

    companion object {
        private const val PREFS = "rota_certa_public_agenda_link_v1"
        private const val KEY_VALUE = "public_agenda_identifier"
        private const val KEY_GENERATION = "generation"
        private const val MIN_LENGTH = 16
    }
}

private class TripSecretStore(
    context: Context,
    private val tenantScope: TenantStorageScope,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ciphertextKey = tenantScope.key(KEY_CIPHERTEXT)
    private val ivKey = tenantScope.key(KEY_IV)
    private val keyAlias = tenantScope.keyAlias(KEY_ALIAS_BASE)

    fun saveDriverToken(token: String) {
        val value = token.trim()
        if (value.isBlank()) {
            prefs.edit().remove(ciphertextKey).remove(ivKey).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(ciphertextKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun driverToken(): String {
        val ciphertext = prefs.getString(ciphertextKey, null)?.let(::decode) ?: return ""
        val iv = prefs.getString(ivKey, null)?.let(::decode) ?: return ""
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val key = keyStore.getKey(keyAlias, null) as? SecretKey ?: return@runCatching ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun clear() {
        prefs.edit().remove(ciphertextKey).remove(ivKey).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun decode(value: String): ByteArray? = runCatching {
        Base64.decode(value, Base64.NO_WRAP)
    }.getOrNull()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_BASE = "rota_certa_stage47_driver_token_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS = "rota_certa_trip_secrets_stage47"
        private const val KEY_CIPHERTEXT = "driver_token_ciphertext"
        private const val KEY_IV = "driver_token_iv"
    }
}


internal fun mergeBookingBatch0380(
    existing: List<Booking>,
    updated: List<Booking>,
): List<Booking> {
    if (updated.isEmpty()) return existing
    val distinctUpdated = LinkedHashMap<String, Booking>().apply {
        updated.forEach { put(it.id, it) }
    }.values.toList()
    val updatedIds = distinctUpdated.map(Booking::id).toSet()
    return distinctUpdated.asReversed() + existing.filterNot { it.id in updatedIds }
}
