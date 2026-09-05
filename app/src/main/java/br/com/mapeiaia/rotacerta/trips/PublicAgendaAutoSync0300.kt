package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PublicAgendaAutoSyncResult(
    val localPublished: Int = 0,
    val externalPublished: Int = 0,
    val seatClaimsSynced: Int = 0,
    val failures: Int = 0,
)

internal data class PublicAgendaExternalTrip(
    val trip: Trip,
    val bookedSeats: Int,
    val sourceReference: String,
    val capacityClaims: List<Booking> = emptyList(),
    val publishedSeats: Int? = null,
    val profileUuid: String = "",
    val blablaTripId: String = "",
    val blablaTripHref: String = "",
    val blablaPublicHref: String = "",
    val sourceComplete: Boolean = false,
    val snapshotRevision: String = "",
    val realAvailableSeats: Int = 0,
)

private data class ExternalCapacitySnapshotSyncResult(
    val published: Boolean,
    val claimsApplied: Int = 0,
    val changed: Boolean = false,
    val shapePreserved: Boolean = false,
)

internal fun remotePublicProjectionMatches0425(
    remote: DriverTripSyncState0402?,
    expectedPublicProjectionHash: String,
    snapshotRevision: String,
): Boolean {
    val expected = expectedPublicProjectionHash.trim()
    return remote?.capacityReliable == true &&
        remote.capacitySnapshotRevision == snapshotRevision &&
        expected.isNotBlank() &&
        remote.publicProjectionHash == expected
}

internal fun remoteCanonicalProjectionMatches0436(
    remote: DriverTripSyncState0402?,
    expectedPublicProjectionHash: String,
    snapshotRevision: String,
    sourceComplete: Boolean,
): Boolean {
    val expected = expectedPublicProjectionHash.trim()
    if (expected.isBlank() || remote == null) return false
    return if (sourceComplete) {
        remotePublicProjectionMatches0425(remote, expected, snapshotRevision)
    } else {
        remote.publicProjectionHash == expected
    }
}

/**
 * Once a Timeline TripStore aggregate exists, its Booking set is the only occupancy
 * input allowed to materialize the private/public Agenda mirror. Re-appending the
 * collector's synthesized claims here duplicates the same passenger occupancy
 * (for example 4 canonical seats becoming 8 in public readback).
 *
 * The synthesized claims remain valid only for the legacy/no-canonical fallback.
 */
internal fun canonicalMirrorBookings0441(
    canonicalSourceAuthoritative: Boolean,
    storedCanonicalBookings: List<Booking>,
    synthesizedCapacityClaims: List<Booking>,
): List<Booking> =
    if (canonicalSourceAuthoritative) {
        storedCanonicalBookings
    } else {
        (storedCanonicalBookings + synthesizedCapacityClaims)
            .associateBy(Booking::id)
            .values
            .toList()
    }

internal fun externalIncrementalPublicationIdentityAccepted0455(
    resolvedInternalTripId: String,
    expectedStrongId: String,
    boundInternalTripId: String,
    canonicalTripSnapshot: Trip?,
    source: BlaBlaCollectorTrip,
    accountIdentityConfirmed: Boolean,
): Boolean {
    val resolved = resolvedInternalTripId.trim()
    if (resolved.isBlank()) return false
    if (
        strongExternalSnapshotIdentityMatches0387(
            canonicalTripId = resolved,
            snapshotTrip = canonicalTripSnapshot,
            sourceTrip = source,
        )
    ) return true
    if (!accountIdentityConfirmed) return false
    return resolved == expectedStrongId || resolved == boundInternalTripId
}

internal fun externalIncrementalCanonicalIdentityMatches0452(
    resolvedInternalTripId: String,
    expectedStrongId: String,
    boundInternalTripId: String,
    canonicalTripSnapshot: Trip?,
    source: BlaBlaCollectorTrip,
): Boolean = externalIncrementalPublicationIdentityAccepted0455(
    resolvedInternalTripId = resolvedInternalTripId,
    expectedStrongId = expectedStrongId,
    boundInternalTripId = boundInternalTripId,
    canonicalTripSnapshot = canonicalTripSnapshot,
    source = source,
    accountIdentityConfirmed = true,
)

internal object PublicAgendaAutoSync0300 {
    suspend fun sync(
        context: Context,
        store: TripStore,
        configuredVehicleCapacity: Int,
        configuredRotaCertaSeatAllocation: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
    ): PublicAgendaAutoSyncResult = withContext(Dispatchers.IO) {
        syncOnIo(
            context = context,
            store = store,
            configuredVehicleCapacity = configuredVehicleCapacity,
            configuredRotaCertaSeatAllocation = configuredRotaCertaSeatAllocation,
            nowMillis = nowMillis,
        )
    }

    private suspend fun syncOnIo(
        context: Context,
        store: TripStore,
        configuredVehicleCapacity: Int,
        configuredRotaCertaSeatAllocation: Int,
        nowMillis: Long,
    ): PublicAgendaAutoSyncResult {
        val settings = store.onlineSettings()
        if (!settings.configured) return PublicAgendaAutoSyncResult()

        val traceId = AgendaTrace.currentTraceId()
        val syncOperation = AgendaTrace.operationStart(
            context,
            "PUBLIC_AGENDA_SYNC",
            "PublicAgendaAutoSync0300.sync",
            traceId,
        )
        try {
        val api = TripRemoteApi(settings)
        val profileOperation = AgendaTrace.operationStart(
            context,
            "PROFILE_SYNC",
            "PublicAgendaAutoSync0300",
            traceId,
            syncOperation.operationId,
        )
        val resolvedPublicProfile = try {
            PublicDriverProfileResolver(context).resolve(settings)
        } catch (error: CancellationException) {
            AgendaTrace.operationCancelled(context, profileOperation)
            throw error
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, profileOperation, error)
            throw error
        }
        runCatching { api.ensurePublicAgenda(settings.publicCalendarToken, resolvedPublicProfile) }
            .onSuccess { response ->
                val updated = settings.copy(
                    driverDisplayName = response.displayName.ifBlank { settings.driverDisplayName },
                    driverUsername = response.username.ifBlank { settings.driverUsername },
                )
                if (updated != settings) store.saveOnlineSettings(updated)
                AgendaTrace.operationEnd(context, profileOperation, result = "synced", processedCount = 1)
                UnifiedDebugEventStore.record(
                    "PUBLIC_DRIVER_PROFILE_SYNCED",
                    context.packageName,
                    "stableAgendaToken=true profileMode=${resolvedPublicProfile.sourceMode.name} profileUuidPresent=${resolvedPublicProfile.selectedProfileUuid.isNotBlank()} automaticProfileAvailable=${resolvedPublicProfile.automaticProfileAvailable} whatsappConfigured=${resolvedPublicProfile.whatsapp.isNotBlank()} vehicleConfigured=${resolvedPublicProfile.vehicleMakeModel.isNotBlank()} paymentConfigured=${resolvedPublicProfile.paymentInstructions.isNotBlank()}",
                )
            }
            .onFailure { error ->
                if (error is CancellationException) {
                    AgendaTrace.operationCancelled(context, profileOperation)
                    throw error
                }
                AgendaTrace.operationError(context, profileOperation, error)
                UnifiedDebugEventStore.record(
                    "PUBLIC_DRIVER_PROFILE_SYNC_FAILED",
                    context.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "PROFILE_SYNC",
                        component = "PublicAgendaAutoSync0300",
                        method = "ensurePublicAgenda",
                    ),
                )
            }
        val canonicalPassengerProfiles = PassengerIdentityStore(context).profiles()
            .groupBy { passengerContactKey(it.agendaAccessContact()) }
            .filter { (contactKey, profiles) -> contactKey.isNotBlank() && profiles.size == 1 }
            .values
            .map { it.single() }
            .take(450)
        val passengerDirectoryOperation = AgendaTrace.operationStart(
            context,
            "PASSENGER_DIRECTORY_SYNC",
            "PublicAgendaAutoSync0300",
            traceId,
            syncOperation.operationId,
        )
        runCatching { api.syncPassengerDirectory(canonicalPassengerProfiles) }
            .onSuccess { response ->
                AgendaTrace.operationEnd(context, passengerDirectoryOperation, result = "synced", processedCount = response.synced)
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_PASSENGER_DIRECTORY_SYNCED",
                    context.packageName,
                    "canonicalPassengers=${canonicalPassengerProfiles.size} synced=${response.synced}",
                )
            }
            .onFailure { error ->
                if (error is CancellationException) {
                    AgendaTrace.operationCancelled(context, passengerDirectoryOperation)
                    throw error
                }
                AgendaTrace.operationError(context, passengerDirectoryOperation, error)
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_PASSENGER_DIRECTORY_SYNC_FAILED",
                    context.packageName,
                    "canonicalPassengers=${canonicalPassengerProfiles.size} " +
                        AgendaFailureEvidence.describe(
                            error = error,
                            operation = "PASSENGER_DIRECTORY_SYNC",
                            component = "PublicAgendaAutoSync0300",
                            method = "syncPassengerDirectory",
                        ),
                )
            }

        var localPublished = 0
        var externalPublished = 0
        var seatClaimsSynced = 0
        var failures = 0
        var externalRetries = 0
        var preservedShapes = 0

        val localDiscoveryOperation = AgendaTrace.operationStart(
            context,
            "LOCAL_TRIPS_DISCOVERY",
            "PublicAgendaAutoSync0300",
            traceId,
            syncOperation.operationId,
        )
        val persistedTrips = store.trips()
        val localTrips = persistedTrips
            .filter(Trip::isCanonicalLocalPublishSource)
            .filter { it.departureAtMillis > nowMillis }
            .filter { it.status in PUBLIC_LOCAL_STATUSES }
        val externalBackingsExcluded = persistedTrips.count {
            resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING &&
                it.departureAtMillis > nowMillis &&
                it.status in PUBLIC_LOCAL_STATUSES
        }
        AgendaTrace.operationEnd(context, localDiscoveryOperation, processedCount = localTrips.size)
        UnifiedDebugEventStore.record(
            "PUBLIC_AGENDA_LOCAL_SOURCE_CLASSIFIED",
            context.packageName,
            "persisted=${persistedTrips.size} local=${localTrips.size} externalBackingsExcluded=$externalBackingsExcluded sourceAuthority=trip_record_origin_strong_identity",
        )

        localTrips.forEach { original ->
            val localBookings = store.bookingsFor(original.id)
            val localAllocation = original.rotaCertaSeatAllocation?.takeIf { it in 0..999 }
                ?: configuredRotaCertaSeatAllocation.takeIf { it in 0..999 }
                ?: 0
            val localFailureTrip = original.copy(
                rotaCertaSeatAllocation = localAllocation,
                capacity = operationalInventoryCapacity(
                    original.copy(rotaCertaSeatAllocation = localAllocation),
                    localBookings,
                ),
            )
            val localRevision = localCapacitySnapshotRevision(original, localBookings, localAllocation)
            val localFailureContext = AgendaFailureEvidence.tripContext(
                trip = localFailureTrip,
                bookings = localBookings,
                tripKey = sha256(original.id).take(12),
                publicIdentity = original.remoteId,
                origin = resolvedTripRecordOrigin(original).name,
                revision = localRevision,
            )
            val localPublishOperation = AgendaTrace.operationStart(
                context,
                "LOCAL_CAPACITY_SNAPSHOT",
                "PublicAgendaAutoSync0300",
                traceId,
                syncOperation.operationId,
            )
            try {
                val changed = syncLocalTripIncremental(
                    context = context,
                    store = store,
                    localTripId = original.id,
                    configuredRotaCertaSeatAllocation = configuredRotaCertaSeatAllocation,
                    nowMillis = nowMillis,
                )
                localPublished++
                if (changed) seatClaimsSynced += localCapacityMirrors(original, localBookings).size
                AgendaTrace.operationEnd(
                    context,
                    localPublishOperation,
                    result = if (changed) "changed" else "no_op",
                    processedCount = if (changed) 1 else 0,
                )
            } catch (error: CancellationException) {
                AgendaTrace.operationCancelled(context, localPublishOperation)
                throw error
            } catch (error: Throwable) {
                AgendaTrace.operationError(
                    context,
                    localPublishOperation,
                    error,
                    failureContext = localFailureContext,
                )
                failures++
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_LOCAL_PUBLISH_FAILED",
                    context.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "LOCAL_CAPACITY_SNAPSHOT",
                        component = "PublicAgendaAutoSync0300",
                        method = "syncLocalTripIncremental",
                        trip = localFailureContext,
                    ),
                )
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val legacyVehicleCapacityIgnored = configuredVehicleCapacity
        val configuredRotaCertaAllocation = configuredRotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
        val internallyCancelledExternalReservationKeys =
            PassengerIdentityStore(context).internallyCancelledExternalReservationKeys()
        val canonicalExternalTrips = persistedTrips
            .filter { resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING }
            .filter { !it.deleted && it.status != TripStatus.CANCELLED && it.departureAtMillis > nowMillis }
            .filter { it.externalSnapshot != null && it.tripKey.isNotBlank() }
        val canonicalResponse = BlaBlaCollectorMonthResponse(
            status = "canonical",
            trips = canonicalExternalTrips.mapNotNull(Trip::externalSnapshot),
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = canonicalExternalTrips.all(Trip::externalSnapshotComplete),
                reason = "trip_store_canonical_projection",
            ),
        )
        val cancellationAdjusted = applyInternalCancellationTombstones(
            canonicalResponse,
            internallyCancelledExternalReservationKeys,
        )
        val canonicalByIdentity = canonicalExternalTrips.associateBy { trip ->
            canonicalExternalTripIdentityKey(
                trip.blablaProfileUuid,
                trip.blablaTripId,
                trip.blablaManageUrl,
            ).orEmpty()
        }
        UnifiedDebugEventStore.record(
            "PUBLIC_AGENDA_CANONICAL_SOURCE_0406",
            context.packageName,
            "persisted=" + persistedTrips.size +
                " canonicalExternal=" + canonicalExternalTrips.size +
                " collectorDirectRead=false internalCancellationTombstones=" +
                internallyCancelledExternalReservationKeys.size,
        )
        val externalDiscoveryOperation = AgendaTrace.operationStart(
            context,
            "EXTERNAL_TRIPS_DISCOVERY",
            "PublicAgendaAutoSync0300",
            traceId,
            syncOperation.operationId,
        )
        val externalTrips = cancellationAdjusted
            ?.trips
            .orEmpty()
            .asSequence()
            .filterNot(BlaBlaCollectorTrip::identity_conflict)
            .mapNotNull { source ->
                val key = canonicalExternalTripIdentityKey(source.profile_uuid, source.trip_id, source.trip_href).orEmpty()
                val canonical = canonicalByIdentity[key] ?: return@mapNotNull null
                toCanonicalExternalProjection0406(
                    canonical = canonical,
                    source = source,
                    nowMillis = nowMillis,
                )
            }
            .distinctBy { it.trip.tripKey.ifBlank { it.trip.id } }
            .take(100)
            .toList()
        AgendaTrace.operationEnd(context, externalDiscoveryOperation, processedCount = externalTrips.size)

        val existingExternalBindings = store.publicExternalBindings()
        val remoteSyncStateList0402 = runCatching { api.listDriverTripSyncStates0402().trips }
            .onFailure { error ->
                UnifiedDebugEventStore.record(
                    "PUBLIC_CAPACITY_REMOTE_STATE_READ_FAILED_0402",
                    context.packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "EXTERNAL_CAPACITY_REMOTE_STATE",
                        component = "PublicAgendaAutoSync0300",
                        method = "listDriverTripSyncStates0402",
                    ),
                )
            }
            .getOrDefault(emptyList())
        val remoteSyncStates0402 = remoteSyncStateList0402.associateBy(DriverTripSyncState0402::remoteTripId)
        val remoteByStrongIdentity0408 = remoteSyncStateList0402
            .mapNotNull { remote ->
                canonicalExternalTripIdentityKey(
                    remote.blablaProfileUuid,
                    remote.blablaTripId,
                    "",
                )?.let { key -> key to remote }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, matches) ->
                matches.maxWithOrNull(
                    compareBy<DriverTripSyncState0402> { it.publicationRevision }
                        .thenBy { it.occupancyRevision }
                        .thenBy { it.remoteTripId },
                )
            }
        UnifiedDebugEventStore.record(
            "PUBLIC_CAPACITY_REMOTE_STATE_READ_0402",
            context.packageName,
            "requested=${externalTrips.size} serverStates=${remoteSyncStates0402.size} strongIdentities=${remoteByStrongIdentity0408.size} oneBatch=true",
        )
        externalTrips.forEachIndexed { index, synthesized ->
            val diagnosticTripKey = sha256(synthesized.trip.publicToken).take(12)
            val existingBindingHint = existingExternalBindings.firstOrNull { binding ->
                (binding.profileUuid.equals(synthesized.profileUuid, ignoreCase = true) && binding.blablaTripId == synthesized.blablaTripId) ||
                    binding.publicToken == synthesized.trip.publicToken
            }
            val strongIdentity0408 = canonicalExternalTripIdentityKey(
                synthesized.profileUuid,
                synthesized.blablaTripId,
                "",
            )
            val canonicalSource0434 = strongIdentity0408?.let { canonicalByIdentity[it] } ?: synthesized.trip
            val canonicalTripId0434 = canonicalSource0434.tripKey.ifBlank { canonicalSource0434.id }
            val canonicalBookings0434 = canonicalMirrorBookings0441(
                canonicalSourceAuthoritative = true,
                storedCanonicalBookings = store.bookingsFor(canonicalSource0434.id),
                synthesizedCapacityClaims = synthesized.capacityClaims,
            )
            val canonicalOperational0434 = canonicalOperationalSnapshot0434(
                trip = canonicalSource0434,
                bookings = canonicalBookings0434,
                nowMillis = nowMillis,
            )
            syncPrivateAgendaMirror0434(
                api = api,
                trip = canonicalSource0434,
                bookings = canonicalBookings0434,
                operationalSnapshot = canonicalOperational0434,
                canonicalTripId = canonicalTripId0434,
                correlationId = traceId,
                syncOperationId = syncOperation.operationId,
                idempotencyKey = "full:" + canonicalSource0434.canonicalRevision,
            )
            val remoteStateHint0402 = remoteSyncStates0402[
                existingBindingHint?.remoteTripId ?: synthesized.trip.publicToken
            ] ?: strongIdentity0408?.let(remoteByStrongIdentity0408::get)
            val resolvedPublicIdentity = remoteStateHint0402?.remoteTripId ?: existingBindingHint?.remoteTripId
            val externalFailureContext = AgendaFailureEvidence.tripContext(
                trip = synthesized.trip,
                bookings = synthesized.capacityClaims,
                tripKey = diagnosticTripKey,
                publicIdentity = resolvedPublicIdentity,
                origin = TripRecordOrigin.EXTERNAL_BACKING.name,
                revision = synthesized.snapshotRevision,
                confirmedSeatsOverride = synthesized.bookedSeats,
                realAvailableSeatsOverride = synthesized.realAvailableSeats,
            )
            val operation = AgendaTrace.operationStart(
                context,
                "EXTERNAL_CAPACITY_SNAPSHOT",
                "PublicAgendaAutoSync0300",
                traceId,
                syncOperation.operationId,
            )
            try {
                val snapshot = syncExternalCapacitySnapshot(
                    context = context,
                    store = store,
                    api = api,
                    synthesized = synthesized,
                    traceId = traceId,
                    parentOperationId = operation.operationId,
                    canonicalTripId = canonicalTripId0434,
                    providedOperationalSnapshot0434 = canonicalOperational0434,
                    existingBindingHint = existingBindingHint,
                    remoteStateHint0402 = remoteStateHint0402,
                )
                if (snapshot.published) externalPublished++
                seatClaimsSynced += snapshot.claimsApplied
                if (snapshot.shapePreserved) preservedShapes++
                AgendaTrace.operationEnd(
                    context,
                    operation,
                    result = if (snapshot.changed) "changed" else "no_op",
                    processedCount = if (snapshot.changed) 1 else 0,
                )
            } catch (error: CancellationException) {
                AgendaTrace.operationCancelled(context, operation)
                throw error
            } catch (error: Throwable) {
                failures++
                AgendaTrace.operationError(
                    context,
                    operation,
                    error,
                    failureContext = externalFailureContext,
                )
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED",
                    context.packageName,
                    "index=${index + 1}/${externalTrips.size} sourceComplete=${synthesized.sourceComplete} claims=${synthesized.capacityClaims.size} " +
                        AgendaFailureEvidence.describe(
                            error = error,
                            operation = "EXTERNAL_CAPACITY_SNAPSHOT",
                            component = "PublicAgendaAutoSync0300",
                            method = "syncExternalCapacitySnapshot",
                            trip = externalFailureContext,
                        ),
                )
            }
        }

        val result = PublicAgendaAutoSyncResult(
            localPublished = localPublished,
            externalPublished = externalPublished,
            seatClaimsSynced = seatClaimsSynced,
            failures = failures,
        )
        AgendaTrace.event(
            context,
            "PUBLIC_AGENDA_SYNC_RESULT",
            "canonicalExternal=${canonicalExternalTrips.size} totalTrips=${localTrips.size + externalTrips.size} processed=${localPublished + externalPublished} localPublished=$localPublished externalPublished=$externalPublished claims=$seatClaimsSynced failures=$failures cancelled=0 retries=$externalRetries preservedShape=$preservedShapes",
            traceId,
            syncOperation.operationId,
        )
        AgendaTrace.operationEnd(
            context,
            syncOperation,
            result = "completed",
            processedCount = localPublished + externalPublished,
        )
        return result
        } catch (error: CancellationException) {
            AgendaTrace.operationCancelled(context, syncOperation)
            throw error
        } catch (error: Throwable) {
            AgendaTrace.operationError(context, syncOperation, error)
            throw error
        }
    }

    suspend fun syncLocalTripIncremental(
        context: Context,
        store: TripStore,
        localTripId: String,
        configuredRotaCertaSeatAllocation: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
        snapshotTrip: Trip? = null,
        snapshotBookings: List<Booking>? = null,
        entityRevision: Long = 0L,
        outboxEventId: String = "",
        mutationId0421: String = "",
        idempotencyKey0421: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        val settings = store.onlineSettings()
        if (!settings.configured) return@withContext false
        val original = snapshotTrip ?: store.trips().firstOrNull { it.id == localTripId } ?: return@withContext false
        if (!original.isCanonicalLocalPublishSource() || original.departureAtMillis <= nowMillis || original.status !in PUBLIC_LOCAL_STATUSES) {
            return@withContext false
        }
        val localBookings = snapshotBookings ?: store.bookingsFor(original.id)
        val allocation = original.rotaCertaSeatAllocation?.takeIf { it in 0..999 }
            ?: configuredRotaCertaSeatAllocation.takeIf { it in 0..999 }
            ?: 0
        val withAllocation = original.copy(
            rotaCertaSeatAllocation = allocation,
        )
        var publicTrip = withAllocation.copy(
            capacity = operationalInventoryCapacity(withAllocation, localBookings),
            capacityReliable = true,
        )
        val canonicalOperational0434 = canonicalOperationalSnapshot0434(
            trip = publicTrip,
            bookings = localBookings,
            nowMillis = nowMillis,
        )
        val mirrors = localCapacityMirrors(publicTrip, localBookings)
        val revision = localCapacitySnapshotRevision(publicTrip, localBookings, allocation)
        val failureContext = AgendaFailureEvidence.tripContext(
            trip = publicTrip,
            bookings = localBookings,
            tripKey = sha256(original.id).take(12),
            publicIdentity = original.remoteId,
            origin = resolvedTripRecordOrigin(original).name,
            revision = revision,
        )
        val api = TripRemoteApi(settings)
        syncPrivateAgendaMirror0434(
            api = api,
            trip = publicTrip,
            bookings = localBookings,
            operationalSnapshot = canonicalOperational0434,
            canonicalTripId = original.id,
            correlationId = outboxEventId,
            syncOperationId = mutationId0421,
            idempotencyKey = idempotencyKey0421,
        )
        var remoteTripId = publicTrip.remoteId?.takeIf(String::isNotBlank) ?: publicTrip.publicToken
        var created = false
        val startedAt = System.nanoTime()

        fun expectedPublicProjectionHash0425(): String =
            canonicalPublicProjectionHash0411(
                canonicalPublicProjectionPayload0411(
                    trip = publicTrip,
                    bookings = localBookings,
                    publicationRevision = entityRevision.takeIf { it > 0L } ?: publicTrip.publicationRevision,
                    nowMillis = nowMillis,
                    canonicalTripId = original.id,
                    operationalSnapshot = canonicalOperational0434,
                ),
            )

        suspend fun reconcile(): DriverCapacitySnapshotResponse = api.reconcileCapacitySnapshot(
            remoteTripId = remoteTripId,
            trip = publicTrip.copy(remoteId = remoteTripId),
            claims = mirrors,
            protectedBookings = if (entityRevision > 0L) {
                localBookings.filter(::protectedBookingMustBeTransmitted0422)
            } else {
                emptyList()
            },
            claimNamespace = LOCAL_MIRROR_PREFIX,
            snapshotRevision = revision,
            entityRevision = entityRevision,
            canonicalTripId = original.id,
            outboxEventId = outboxEventId,
            mutationId0421 = mutationId0421,
            idempotencyKey0421 = idempotencyKey0421,
            expectedPublicProjectionHash0425 = expectedPublicProjectionHash0425(),
            expectedPublicProjectionJson0434 = canonicalPublicProjectionJson0411(
                canonicalPublicProjectionPayload0411(
                    trip = publicTrip,
                    bookings = localBookings,
                    publicationRevision = entityRevision.takeIf { it > 0L } ?: publicTrip.publicationRevision,
                    nowMillis = nowMillis,
                    canonicalTripId = original.id,
                    operationalSnapshot = canonicalOperational0434,
                ),
            ),
        )

        val response = try {
            try {
                reconcile()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (!isRemoteTripNotFound(error)) throw error
                val published = api.publish(publicTrip.copy(capacityReliable = false, remoteId = null))
                created = true
                remoteTripId = published.tripId
                publicTrip = publicTrip.copy(
                    remoteId = published.tripId,
                    publicToken = published.publicToken,
                    publicUrl = published.publicUrl,
                )
                store.getTrip(original.id)?.let { current ->
                    if (current.remoteId.isNullOrBlank()) {
                        store.saveTrip(
                            current.copy(
                                remoteId = published.tripId,
                                publicToken = published.publicToken,
                                publicUrl = published.publicUrl,
                                capacityReliable = true,
                            ),
                        )
                    }
                }
                reconcile()
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            UnifiedDebugEventStore.record(
                "PUBLIC_LOCAL_CAPACITY_INCREMENTAL_FAILED_DETAIL",
                context.packageName,
                AgendaFailureEvidence.describe(
                    error = error,
                    operation = "PUBLISH_INCREMENTAL_CAPACITY",
                    component = "PublicAgendaAutoSync0300",
                    method = "syncLocalTripIncremental",
                    trip = failureContext,
                ),
            )
            throw error
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        if (response.stale) {
            throw PublicationStaleRevision0387(response.entityRevision)
        }
        UnifiedDebugEventStore.record(
            if (response.changed) "PUBLIC_LOCAL_CAPACITY_INCREMENTAL_PUBLISHED" else "PUBLIC_LOCAL_CAPACITY_INCREMENTAL_NO_OP",
            context.packageName,
            "tripKey=${sha256(original.id).take(12)} revision=${revision.takeLast(12)} entityRevision=$entityRevision mutationId=${mutationId0421.take(56)} idempotencyKey=${idempotencyKey0421.take(56)} outboxEventId=${outboxEventId.take(56)} occupancyRevision=${response.occupancyRevision} rotaCertaSeats=$allocation confirmedOccupiedSeats=${operationalSeatSummary(publicTrip, localBookings).confirmedPassengerSeats} availableMin=${response.availableSeatsMinimum} availableMax=${response.availableSeatsMaximum} changed=${response.changed} stale=${response.stale} createdPlaceholder=$created durationMs=$elapsedMs fullSyncRequested=false",
        )
        response.changed
    }

    internal fun localCapacitySnapshotRevision(
        trip: Trip,
        bookings: List<Booking>,
        rotaCertaSeatAllocation: Int,
    ): String {
        val semantic = buildString {
            append(trip.id).append('|').append(trip.title.trim()).append('|')
            append(trip.publicTimezoneId0411.trim()).append('|')
            append(canonicalBoundBlaBlaPublicUrl0423(trip.blablaPublicUrl, trip.blablaTripId).orEmpty()).append('|')
            append(trip.departureAtMillis).append('|').append(trip.status.name).append('|')
            append(trip.publicBookingEnabled).append('|').append(trip.itineraryAuthoritative).append('|')
            append(trip.capacityReliable).append('|').append(trip.publishedSeats ?: -1).append('|')
            append(rotaCertaSeatAllocation.coerceIn(0, 999)).append('|')
            trip.stops.sortedBy(TripStop::order).forEach { stop ->
                append(stop.id).append('~').append(stop.order).append('~').append(normalizePlace(stop.name)).append('~')
                append(normalizePlace(stop.address)).append('~').append(stop.priceToNextCents).append(',')
            }
            bookings.sortedBy(Booking::id).forEach { booking ->
                append(booking.id).append('~').append(booking.passengerId.trim()).append('~')
                append(booking.passengerName.trim()).append('~').append(booking.passengerContact.trim()).append('~')
                append(booking.boardingStopId).append('~').append(booking.dropoffStopId).append('~')
                append(normalizePlace(booking.boardingAddress)).append('~').append(normalizePlace(booking.dropoffAddress)).append('~')
                append(booking.seats).append('~').append(booking.status.name).append('~')
                append(booking.operationalStatus.name).append('~').append(booking.paymentStatus.name).append('~')
                append(booking.lastDriverSelection.trim()).append('~').append(booking.source.name).append('~')
                append(booking.capacityClaimType.name).append('~').append(booking.sourceReference).append('~')
                append(booking.occupancyGroupId.orEmpty()).append('~')
                append(booking.fareMinorUnits ?: -1L).append('~').append(booking.fareCurrencyCode.trim()).append(',')
            }
        }
        return "localcap-v2:${sha256(semantic)}"
    }

    suspend fun syncExternalTripIncremental(
        context: Context,
        store: TripStore,
        source: BlaBlaCollectorTrip,
        configuredRotaCertaSeatAllocation: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
        entityRevision: Long = 0L,
        outboxEventId: String = "",
        mutationId0421: String = "",
        idempotencyKey0421: String = "",
        externalAccountId: String = "",
        canonicalTripId: String = "",
        seatAllocationVersion: Long = 0L,
        canonicalTripSnapshot: Trip? = null,
        remoteStateHint0402: DriverTripSyncState0402? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (source.identity_conflict) {
            if (outboxEventId.isNotBlank()) {
                error("EXTERNAL_SOURCE_IDENTITY_CONFLICT")
            }
            return@withContext false
        }
        val profileUuid = source.profile_uuid.trim()
        val tripId = source.trip_id?.trim().orEmpty()
        val boundInternalTripId = store.publicExternalBindingForStrongIdentity(profileUuid, tripId)?.bookingTripId.orEmpty()
        val resolvedInternalTripId = canonicalTripId.takeIf(String::isNotBlank)
            ?: boundInternalTripId.takeIf(String::isNotBlank)
            ?: if (
                entityRevision > 0L &&
                externalAccountId.isNotBlank() &&
                profileUuid.isNotBlank() &&
                tripId.isNotBlank()
            ) {
                canonicalBlaBlaTripKey0406(
                    tenantId = TripPublicationOutbox0387(context).tenantId,
                    profileUuid = profileUuid,
                    providerTripId = tripId,
                ).orEmpty()
            } else ""
        if (entityRevision > 0L) {
            val accounts = if (externalAccountId.isBlank()) emptyList() else {
                BlaBlaDynamicAccountRegistry(context).list().filter { account ->
                    account.id == externalAccountId &&
                        account.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true
                }
            }
            val accountIdentityConfirmed0455 = externalAccountId.isNotBlank() && accounts.size == 1
            val expectedStrongId0455 = if (
                accountIdentityConfirmed0455 &&
                profileUuid.isNotBlank() &&
                tripId.isNotBlank()
            ) {
                canonicalBlaBlaTripKey0406(
                    tenantId = TripPublicationOutbox0387(context).tenantId,
                    profileUuid = profileUuid,
                    providerTripId = tripId,
                ).orEmpty()
            } else ""
            val canonicalSnapshotIdentityAccepted0455 = strongExternalSnapshotIdentityMatches0387(
                canonicalTripId = resolvedInternalTripId,
                snapshotTrip = canonicalTripSnapshot,
                sourceTrip = source,
            )
            val canonicalIdentityAccepted0455 = externalIncrementalPublicationIdentityAccepted0455(
                resolvedInternalTripId = resolvedInternalTripId,
                expectedStrongId = expectedStrongId0455,
                boundInternalTripId = boundInternalTripId,
                canonicalTripSnapshot = canonicalTripSnapshot,
                source = source,
                accountIdentityConfirmed = accountIdentityConfirmed0455,
            )
            if (outboxEventId.isNotBlank()) {
                UnifiedDebugEventStore.record(
                    "PUBLIC_EVIDENCE_0421",
                    context.packageName,
                    buildString {
                        append("evidenceId=").append(publicationEvidenceId0421(outboxEventId, canonicalTripSnapshot?.canonicalRevision ?: 0L))
                        append(" traceId=").append(outboxEventId)
                        append(" correlationId=").append(outboxEventId)
                        append(" stage=INCREMENTAL_IDENTITY_GUARD")
                        append(" status=").append(if (canonicalIdentityAccepted0455) "OK" else "FAILED")
                        append(" reasonCode=").append(if (canonicalIdentityAccepted0455) "CANONICAL_PUBLICATION_IDENTITY_ACCEPTED" else "CANONICAL_PUBLICATION_IDENTITY_REJECTED")
                        append(" canonicalTripId=").append(seatSyncDiagnosticKey(resolvedInternalTripId))
                        append(" logicalRevision=").append(canonicalTripSnapshot?.canonicalRevision ?: 0L)
                        append(" transportRevision=").append(entityRevision)
                        append(" mutationId=").append(mutationId0421)
                        append(" idempotencyKey=").append(idempotencyKey0421)
                        append(" snapshotIdentity=").append(canonicalSnapshotIdentityAccepted0455)
                        append(" accountIdentity=").append(accountIdentityConfirmed0455)
                        append(" profileUuidPresent=").append(profileUuid.isNotBlank())
                        append(" tripIdPresent=").append(tripId.isNotBlank())
                        append(" previousStage=CANONICAL_REBASE_GUARD nextStage=CANONICAL_OPERATIONAL_GUARD")
                    },
                )
            }
            require(profileUuid.isNotBlank() && tripId.isNotBlank() && canonicalIdentityAccepted0455) {
                "Sincronização externa determinística exige snapshot canônico forte ou conta + profile UUID + tripId fortes."
            }
        }
        fun recordPreOperationalEvidence0458(
            stage: String,
            status: String,
            reasonCode: String,
            previousStage: String,
            nextStage: String,
            startedNs: Long,
            error: Throwable? = null,
        ) {
            if (outboxEventId.isBlank()) return
            runCatching {
                val causeResolution = error?.let(AgendaFailureEvidence::resolveCauseChain)
                val root = causeResolution?.chain?.lastOrNull()
                val sourceFrame = error?.stackTrace?.firstOrNull {
                    it.className.startsWith("br.com.mapeiaia.rotacerta")
                }
                val exceptionMessage = error?.message.orEmpty().let { raw ->
                    runCatching { UnifiedDebugEventStore.sanitizeForExport(raw) }
                        .getOrDefault("<sanitization-failed>")
                        .replace('"', '\'')
                        .take(240)
                }
                val rootMessage = root?.message.orEmpty().let { raw ->
                    runCatching { UnifiedDebugEventStore.sanitizeForExport(raw) }
                        .getOrDefault("<sanitization-failed>")
                        .replace('"', '\'')
                        .take(240)
                }
                UnifiedDebugEventStore.recordAlways(
                    "PUBLIC_EVIDENCE_0421",
                    context.packageName,
                    buildString {
                        append("evidenceId=").append(publicationEvidenceId0421(outboxEventId, canonicalTripSnapshot?.canonicalRevision ?: 0L))
                        append(" traceId=").append(outboxEventId)
                        append(" correlationId=").append(outboxEventId)
                        append(" stage=").append(stage)
                        append(" status=").append(status)
                        append(" reasonCode=").append(reasonCode)
                        append(" canonicalTripId=").append(seatSyncDiagnosticKey(resolvedInternalTripId))
                        append(" logicalRevision=").append(canonicalTripSnapshot?.canonicalRevision ?: 0L)
                        append(" transportRevision=").append(entityRevision)
                        append(" mutationId=").append(mutationId0421)
                        append(" idempotencyKey=").append(idempotencyKey0421)
                        append(" durationMs=").append(((System.nanoTime() - startedNs).coerceAtLeast(0L)) / 1_000_000L)
                        if (error != null) {
                            append(" exceptionClass=").append(error.javaClass.name)
                            append(" exceptionMessage=\"").append(exceptionMessage).append('\"')
                            append(" rootCauseClass=").append(root?.javaClass?.name.orEmpty())
                            append(" rootCauseMessage=\"").append(rootMessage).append('\"')
                            if (sourceFrame != null) {
                                append(" exceptionSource=")
                                    .append(sourceFrame.fileName ?: sourceFrame.className.substringAfterLast('.'))
                                    .append(':').append(sourceFrame.methodName)
                                    .append(':').append(sourceFrame.lineNumber)
                            }
                        }
                        append(" previousStage=").append(previousStage)
                        append(" nextStage=").append(nextStage)
                    },
                )
            }
        }

        fun <T> preOperationalEvidenceStep0457(
            stage: String,
            previousStage: String,
            nextStage: String,
            block: () -> T,
        ): T {
            val startedNs0458 = System.nanoTime()
            return try {
                val value = block()
                recordPreOperationalEvidence0458(
                    stage = stage,
                    status = "OK",
                    reasonCode = "PRE_OPERATIONAL_STEP_OK",
                    previousStage = previousStage,
                    nextStage = nextStage,
                    startedNs = startedNs0458,
                )
                value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                recordPreOperationalEvidence0458(
                    stage = stage,
                    status = "FAILED",
                    reasonCode = "PRE_OPERATIONAL_EXCEPTION",
                    previousStage = previousStage,
                    nextStage = "STOP",
                    startedNs = startedNs0458,
                    error = error,
                )
                throw error
            }
        }

        val settings = preOperationalEvidenceStep0457(
            stage = "ONLINE_SETTINGS_READ",
            previousStage = "INCREMENTAL_IDENTITY_GUARD",
            nextStage = "CANONICAL_SOURCE_RESOLUTION",
        ) {
            store.onlineSettings()
        }
        if (!settings.configured) {
            if (outboxEventId.isNotBlank()) {
                error("AGENDA_ONLINE_NOT_CONFIGURED_DURABLE_REPLAY")
            }
            return@withContext false
        }
        val canonical = preOperationalEvidenceStep0457(
            stage = "CANONICAL_SOURCE_RESOLUTION",
            previousStage = "ONLINE_SETTINGS_READ",
            nextStage = "CANONICAL_PROJECTION_BUILD",
        ) {
            canonicalTripSnapshot?.takeIf { trip ->
            resolvedTripRecordOrigin(trip) == TripRecordOrigin.EXTERNAL_BACKING &&
                !trip.deleted &&
                trip.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                trip.blablaTripId?.trim() == tripId
            } ?: store.trips().firstOrNull { trip ->
                resolvedTripRecordOrigin(trip) == TripRecordOrigin.EXTERNAL_BACKING &&
                    !trip.deleted &&
                    trip.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                    trip.blablaTripId?.trim() == tripId
            }
        }
        val synthesized = preOperationalEvidenceStep0457(
            stage = "CANONICAL_PROJECTION_BUILD",
            previousStage = "CANONICAL_SOURCE_RESOLUTION",
            nextStage = "DIAGNOSTIC_KEY_BUILD",
        ) {
            canonical?.let { canonicalTrip ->
                toCanonicalExternalProjection0406(
                    canonical = canonicalTrip,
                    source = source,
                    nowMillis = nowMillis,
                )
            } ?: run {
                val rotaCertaQuota = configuredRotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
                val blablaQuota = source.published_seats?.takeIf { it in 0..999 } ?: 0
                toPublicTrip(
                    source = source,
                    capacity = (blablaQuota + rotaCertaQuota).coerceIn(0, 999),
                    rotaCertaSeatAllocation = rotaCertaQuota,
                    nowMillis = nowMillis,
                )
            }
        }
        if (synthesized == null) {
            val projectionReason0460 = if (canonical != null) {
                "CANONICAL_PROJECTION_UNAVAILABLE"
            } else {
                "RAW_SOURCE_NOT_PUBLISHABLE"
            }
            recordPreOperationalEvidence0458(
                stage = "CANONICAL_PROJECTION_RESULT",
                status = "FAILED",
                reasonCode = projectionReason0460,
                previousStage = "CANONICAL_PROJECTION_BUILD",
                nextStage = "STOP",
                startedNs = System.nanoTime(),
            )
            if (outboxEventId.isNotBlank()) {
                error(projectionReason0460)
            }
            return@withContext false
        }
        val startedAt = System.nanoTime()
        val tripKey = preOperationalEvidenceStep0457(
            stage = "DIAGNOSTIC_KEY_BUILD",
            previousStage = "CANONICAL_PROJECTION_BUILD",
            nextStage = "DIAGNOSTIC_CONTEXT_BUILD",
        ) {
            sha256(synthesized.trip.publicToken).take(12)
        }
        val diagnosticStartedNs0458 = System.nanoTime()
        val failureContext = try {
            val value = AgendaFailureEvidence.tripContext(
                trip = synthesized.trip,
                bookings = synthesized.capacityClaims,
                tripKey = tripKey,
                publicIdentity = store.publicExternalBindings()
                    .firstOrNull { it.publicToken == synthesized.trip.publicToken }
                    ?.remoteTripId,
                origin = TripRecordOrigin.EXTERNAL_BACKING.name,
                revision = synthesized.snapshotRevision,
                confirmedSeatsOverride = synthesized.bookedSeats,
                realAvailableSeatsOverride = synthesized.realAvailableSeats,
            )
            recordPreOperationalEvidence0458(
                stage = "DIAGNOSTIC_CONTEXT_BUILD",
                status = "OK",
                reasonCode = "DIAGNOSTIC_CONTEXT_READY",
                previousStage = "DIAGNOSTIC_KEY_BUILD",
                nextStage = "REMOTE_API_CONTEXT_BUILD",
                startedNs = diagnosticStartedNs0458,
            )
            value
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Observability is fail-open: a diagnostic envelope must never block
            // the canonical publication pipeline.
            recordPreOperationalEvidence0458(
                stage = "DIAGNOSTIC_CONTEXT_BUILD",
                status = "DEGRADED",
                reasonCode = "DIAGNOSTIC_CONTEXT_FALLBACK",
                previousStage = "DIAGNOSTIC_KEY_BUILD",
                nextStage = "REMOTE_API_CONTEXT_BUILD",
                startedNs = diagnosticStartedNs0458,
                error = error,
            )
            AgendaFailureTripContext(
                tripKey = tripKey,
                canonicalIdentity = synthesized.trip.tripKey.ifBlank { synthesized.trip.id },
                publicIdentity = "<unresolved>",
                origin = TripRecordOrigin.EXTERNAL_BACKING.name,
                route = synthesized.trip.title,
                revision = synthesized.snapshotRevision,
                confirmedSeats = synthesized.bookedSeats,
                realAvailableSeats = synthesized.realAvailableSeats,
                snapshotVersion = synthesized.snapshotRevision.substringBefore(':').take(40),
            )
        }
        UnifiedDebugEventStore.record(
            "PUBLIC_AGENDA_INCREMENTAL_START",
            context.packageName,
            "tripKey=$tripKey sourceComplete=${synthesized.sourceComplete} revision=${synthesized.snapshotRevision.takeLast(12)} fullSyncRequested=false",
        )
        val api = preOperationalEvidenceStep0457(
            stage = "REMOTE_API_CONTEXT_BUILD",
            previousStage = "DIAGNOSTIC_CONTEXT_BUILD",
            nextStage = "PRIVATE_MIRROR_INPUT_BUILD",
        ) {
            TripRemoteApi(settings)
        }
        val serverCanonicalAuthority0468 =
            entityRevision > 0L &&
                outboxEventId.isNotBlank() &&
                resolvedInternalTripId.isNotBlank() &&
                profileUuid.isNotBlank() &&
                tripId.isNotBlank()
        val privateMirrorTrip0434 = if (serverCanonicalAuthority0468) {
            synthesized.trip.copy(
                tripKey = resolvedInternalTripId,
                recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
                canonicalRevision = 0L,
                canonicalStateHash = "",
            )
        } else {
            canonical ?: synthesized.trip
        }
        val privateMirrorBookings0434 = preOperationalEvidenceStep0457(
            stage = "PRIVATE_MIRROR_INPUT_BUILD",
            previousStage = "REMOTE_API_CONTEXT_BUILD",
            nextStage = "CANONICAL_OPERATIONAL_GUARD",
        ) {
            if (serverCanonicalAuthority0468) {
                synthesized.capacityClaims
            } else {
                canonicalMirrorBookings0441(
                    canonicalSourceAuthoritative = canonical != null,
                    storedCanonicalBookings = store.bookingsFor(privateMirrorTrip0434.id),
                    synthesizedCapacityClaims = synthesized.capacityClaims,
                )
            }
        }
        val canonicalTripId0434 = privateMirrorTrip0434.tripKey
            .ifBlank { resolvedInternalTripId }
            .ifBlank { privateMirrorTrip0434.id }
        val domainInventory0456 = operationalInventoryCapacity(privateMirrorTrip0434, privateMirrorBookings0434)
        val canonicalCapacityInvariant0456 = privateMirrorTrip0434.capacity == domainInventory0456
        if (outboxEventId.isNotBlank()) {
            UnifiedDebugEventStore.record(
                "PUBLIC_EVIDENCE_0421",
                context.packageName,
                buildString {
                    append("evidenceId=").append(publicationEvidenceId0421(outboxEventId, privateMirrorTrip0434.canonicalRevision))
                    append(" traceId=").append(outboxEventId)
                    append(" correlationId=").append(outboxEventId)
                    append(" stage=CANONICAL_OPERATIONAL_GUARD")
                    append(" status=").append(if (canonicalCapacityInvariant0456) "OK" else "FAILED")
                    append(" reasonCode=").append(
                        if (canonicalCapacityInvariant0456) {
                            "CANONICAL_CAPACITY_INVARIANT_CONFIRMED"
                        } else {
                            "CANONICAL_CAPACITY_INVARIANT_MISMATCH"
                        },
                    )
                    append(" canonicalTripId=").append(seatSyncDiagnosticKey(canonicalTripId0434))
                    append(" logicalRevision=").append(privateMirrorTrip0434.canonicalRevision)
                    append(" transportRevision=").append(entityRevision)
                    append(" mutationId=").append(mutationId0421)
                    append(" idempotencyKey=").append(idempotencyKey0421)
                    append(" capacity=").append(privateMirrorTrip0434.capacity)
                    append(" domainInventory=").append(domainInventory0456)
                    append(" publishedSeats=").append(privateMirrorTrip0434.publishedSeats ?: -1)
                    append(" rotaCertaSeatAllocation=").append(privateMirrorTrip0434.rotaCertaSeatAllocation ?: -1)
                    append(" previousStage=PRIVATE_MIRROR_INPUT_BUILD nextStage=PRIVATE_MIRROR_REQUEST")
                },
            )
        }
        val canonicalOperational0434 = preOperationalEvidenceStep0457(
            stage = "CANONICAL_OPERATIONAL_BUILD",
            previousStage = "CANONICAL_OPERATIONAL_GUARD",
            nextStage = "PRIVATE_MIRROR_REQUEST",
        ) {
            canonicalOperationalSnapshot0434(
                trip = privateMirrorTrip0434,
                bookings = privateMirrorBookings0434,
                nowMillis = privateMirrorTrip0434.updatedAtMillis.takeIf { it > 0L } ?: nowMillis,
            )
        }
        val privateMirrorEvidence0455 = outboxEventId.takeIf(String::isNotBlank)?.let { traceId ->
            RemotePublicationEvidenceContext0421(
                evidenceId = publicationEvidenceId0421(traceId, privateMirrorTrip0434.canonicalRevision),
                traceId = traceId,
                canonicalTripId = canonicalTripId0434,
                logicalRevision = privateMirrorTrip0434.canonicalRevision,
                transportRevision = entityRevision,
                mutationId = mutationId0421,
                idempotencyKey = idempotencyKey0421,
            )
        }
        if (!serverCanonicalAuthority0468) {
            syncPrivateAgendaMirror0434(
                api = api,
                trip = privateMirrorTrip0434,
                bookings = privateMirrorBookings0434,
                operationalSnapshot = canonicalOperational0434,
                canonicalTripId = canonicalTripId0434,
                correlationId = outboxEventId,
                syncOperationId = mutationId0421,
                idempotencyKey = idempotencyKey0421,
                evidence0421 = privateMirrorEvidence0455,
            )
        } else {
            UnifiedDebugEventStore.record(
                "BACKEND_CANONICAL_DIRECT_INGESTION_0468",
                context.packageName,
                "canonicalTripId=" + seatSyncDiagnosticKey(canonicalTripId0434) +
                    " privateMirrorSkipped=true transportRevision=" + entityRevision,
            )
        }
        val result = try {
            syncExternalCapacitySnapshot(
                context = context,
                store = store,
                api = api,
                synthesized = synthesized,
                traceId = AgendaTrace.currentTraceId(),
                parentOperationId = null,
                entityRevision = entityRevision,
                canonicalTripId = canonicalTripId0434,
                providedOperationalSnapshot0434 = canonicalOperational0434,
                outboxEventId = outboxEventId,
                mutationId0421 = mutationId0421,
                idempotencyKey0421 = idempotencyKey0421,
                seatAllocationVersion = seatAllocationVersion,
                remoteStateHint0402 = remoteStateHint0402,
                serverCanonicalAuthority0468 = serverCanonicalAuthority0468,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            UnifiedDebugEventStore.record(
                "PUBLIC_EXTERNAL_CAPACITY_INCREMENTAL_FAILED_DETAIL",
                context.packageName,
                AgendaFailureEvidence.describe(
                    error = error,
                    operation = "PUBLISH_INCREMENTAL_CAPACITY",
                    component = "PublicAgendaAutoSync0300",
                    method = "syncExternalTripIncremental",
                    trip = failureContext,
                ),
            )
            throw error
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        UnifiedDebugEventStore.record(
            "PUBLIC_AGENDA_INCREMENTAL_END",
            context.packageName,
            "tripKey=$tripKey changed=${result.changed} claims=${result.claimsApplied} sourceComplete=${synthesized.sourceComplete} durationMs=$elapsedMs fullSyncRequested=false",
        )
        result.changed
    }

    private suspend fun syncExternalCapacitySnapshot(
        context: Context,
        store: TripStore,
        api: TripRemoteApi,
        synthesized: PublicAgendaExternalTrip,
        traceId: String,
        parentOperationId: String?,
        entityRevision: Long = 0L,
        canonicalTripId: String = "",
        providedOperationalSnapshot0434: CanonicalOperationalSnapshot0434? = null,
        outboxEventId: String = "",
        mutationId0421: String = "",
        idempotencyKey0421: String = "",
        seatAllocationVersion: Long = 0L,
        existingBindingHint: PublicExternalTripBinding? = null,
        remoteStateHint0402: DriverTripSyncState0402? = null,
        serverCanonicalAuthority0468: Boolean = false,
    ): ExternalCapacitySnapshotSyncResult {
        val publicTrip = synthesized.trip
        val diagnosticTripKey = sha256(publicTrip.publicToken).take(12)
        val existingBinding = existingBindingHint
            ?: store.publicExternalBindingForStrongIdentity(synthesized.profileUuid, synthesized.blablaTripId)
            ?: store.publicExternalBindings().firstOrNull { it.publicToken == publicTrip.publicToken }

        if (!synthesized.sourceComplete) {
            if (serverCanonicalAuthority0468 && existingBinding == null) {
                error("SERVER_CANONICAL_INGESTION_REQUIRES_COMPLETE_INITIAL_SNAPSHOT")
            }
            if (existingBinding != null) {
                UnifiedDebugEventStore.record(
                    "PUBLIC_CAPACITY_FAIL_CLOSED",
                    context.packageName,
                    "tripKey=$diagnosticTripKey action=preserve_capacity_claims_project_canonical reason=incomplete_source previousBinding=true publishedSeatsKnown=${synthesized.publishedSeats != null} fullSyncRequested=false",
                )
            } else {
                val response = try {
                    api.publish(publicTrip.copy(capacityReliable = false))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // A missing local binding does not authorize downgrading a remote trip that may
                // already have a reliable snapshot. A duplicate/conflict therefore fails closed.
                UnifiedDebugEventStore.record(
                    "PUBLIC_CAPACITY_FAIL_CLOSED",
                    context.packageName,
                    "action=no_remote_mutation reason=incomplete_source_publish_failed " +
                        AgendaFailureEvidence.describe(
                            error = error,
                            operation = "EXTERNAL_CAPACITY_SNAPSHOT",
                            component = "PublicAgendaAutoSync0300",
                            method = "syncExternalCapacitySnapshot",
                            trip = AgendaFailureEvidence.tripContext(
                                trip = publicTrip,
                                bookings = synthesized.capacityClaims,
                                tripKey = diagnosticTripKey,
                                publicIdentity = existingBinding?.remoteTripId,
                                origin = TripRecordOrigin.EXTERNAL_BACKING.name,
                                revision = synthesized.snapshotRevision,
                                confirmedSeatsOverride = synthesized.bookedSeats,
                                realAvailableSeatsOverride = synthesized.realAvailableSeats,
                            ),
                        ),
                )
                    return ExternalCapacitySnapshotSyncResult(published = false, changed = false)
                }
                saveExternalBinding(
                    store = store,
                    remoteTripId = response.tripId,
                    publicToken = response.publicToken,
                    synthesized = synthesized,
                    effectiveTrip = publicTrip,
                    canonicalTripId = canonicalTripId,
                    entityRevision = entityRevision,
                    seatAllocationVersion = 0L,
                )
                UnifiedDebugEventStore.record(
                    "PUBLIC_CAPACITY_UNKNOWN_PUBLISHED",
                    context.packageName,
                    "tripKey=$diagnosticTripKey capacityReliable=false reason=first_snapshot_incomplete reservationBlocked=true",
                )
                return ExternalCapacitySnapshotSyncResult(published = true, changed = true)
            }
        }

        var remoteTripId = remoteStateHint0402?.remoteTripId ?: existingBinding?.remoteTripId ?: publicTrip.publicToken
        val observedStopIds = publicTrip.stops.sortedBy(TripStop::order).map(TripStop::id)
        var effectiveTrip = publicTrip.copy(remoteId = remoteTripId)
        var effectiveClaims = if (synthesized.sourceComplete) synthesized.capacityClaims else emptyList()
        var shapePreserved = false
        var createdPlaceholder = false
        val remoteStopIds0434 = remoteStateHint0402?.stops.orEmpty().sortedBy(TripStop::order).map(TripStop::id)
        if (remoteStopIds0434.isNotEmpty() && remoteStopIds0434 != observedStopIds) {
            UnifiedDebugEventStore.record(
                "PUBLIC_CAPACITY_CANONICAL_SHAPE_0434",
                context.packageName,
                "tripKey=$diagnosticTripKey action=canonical_overrides_remote_shape",
            )
        }

        fun expectedProjectionState0434(): Pair<List<Booking>, CanonicalOperationalSnapshot0434> {
            val expectedBookings = (store.bookingsFor(publicTrip.id) + effectiveClaims)
                .associateBy(Booking::id)
                .values
                .toList()
            val operational = providedOperationalSnapshot0434
                ?: canonicalOperationalSnapshot0434(
                    trip = effectiveTrip,
                    bookings = expectedBookings,
                    nowMillis = synthesized.trip.updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                )
            return expectedBookings to operational
        }

        fun expectedPublicProjectionPayload0434(): CanonicalPublicTripPayload0411 {
            val (expectedBookings, operational) = expectedProjectionState0434()
            return canonicalPublicProjectionPayload0411(
                    trip = effectiveTrip,
                    bookings = expectedBookings,
                    publicationRevision = entityRevision.takeIf { it > 0L }
                        ?: remoteStateHint0402?.publicationRevision
                        ?: effectiveTrip.publicationRevision,
                    nowMillis = synthesized.trip.updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    canonicalTripId = canonicalTripId.ifBlank { effectiveTrip.tripKey }.ifBlank { effectiveTrip.id },
                    operationalSnapshot = operational,
                )
        }

        fun expectedPublicProjectionHash0425(): String =
            canonicalPublicProjectionHash0411(expectedPublicProjectionPayload0434())

        val remoteProjectionMatches0425 = !serverCanonicalAuthority0468 &&
            remoteCanonicalProjectionMatches0436(
                remote = remoteStateHint0402,
                expectedPublicProjectionHash = expectedPublicProjectionHash0425(),
                snapshotRevision = synthesized.snapshotRevision,
                sourceComplete = synthesized.sourceComplete,
            )

        if (remoteProjectionMatches0425) {
            saveExternalBinding(
                store = store,
                remoteTripId = remoteTripId,
                publicToken = publicTrip.publicToken,
                synthesized = synthesized,
                effectiveTrip = effectiveTrip,
                canonicalTripId = canonicalTripId,
                entityRevision = maxOf(entityRevision, remoteStateHint0402?.publicationRevision ?: 0L),
                seatAllocationVersion = seatAllocationVersion,
            )
            UnifiedDebugEventStore.record(
                "PUBLIC_CAPACITY_REMOTE_REVISION_NO_OP_0402", context.packageName,
                "tripKey=$diagnosticTripKey revision=${synthesized.snapshotRevision.takeLast(12)} publicHashMatch=true putSkipped=true",
            )
            return ExternalCapacitySnapshotSyncResult(
                published = true,
                changed = false,
                shapePreserved = shapePreserved,
            )
        }

        if (
            remoteStateHint0402?.capacityReliable == true &&
            remoteStateHint0402.capacitySnapshotRevision == synthesized.snapshotRevision
        ) {
            UnifiedDebugEventStore.record(
                "PUBLIC_CAPACITY_REMOTE_REVISION_REPAIR_REQUIRED_0425",
                context.packageName,
                "tripKey=$diagnosticTripKey revision=${synthesized.snapshotRevision.takeLast(12)} publicHashPresent=${remoteStateHint0402.publicProjectionHash.isNotBlank()} publicHashMatch=false action=put_not_skipped",
            )
        }

        suspend fun reconcile(): DriverCapacitySnapshotResponse = api.reconcileCapacitySnapshot(
            remoteTripId = remoteTripId,
            trip = effectiveTrip,
            claims = effectiveClaims,
            claimNamespace = EXTERNAL_MIRROR_PREFIX,
            snapshotRevision = synthesized.snapshotRevision,
            entityRevision = entityRevision,
            canonicalTripId = canonicalTripId,
            outboxEventId = outboxEventId,
            mutationId0421 = mutationId0421,
            idempotencyKey0421 = idempotencyKey0421,
            expectedPublicProjectionHash0425 =
                if (serverCanonicalAuthority0468) "" else expectedPublicProjectionHash0425(),
            expectedPublicProjectionJson0434 =
                if (serverCanonicalAuthority0468) "" else
                    canonicalPublicProjectionJson0411(expectedPublicProjectionPayload0434()),
            sourceComplete = synthesized.sourceComplete,
            preserveManagedClaims0436 = !synthesized.sourceComplete && existingBinding != null,
            serverCanonicalAuthority0468 = serverCanonicalAuthority0468,
        )

        val response = try {
            reconcile()
        } catch (firstError: Throwable) {
            if (firstError is CancellationException) throw firstError
            when {
                isRemoteTripNotFound(firstError) && !serverCanonicalAuthority0468 -> {
                    val staleRemoteTripId = remoteTripId
                    val created = api.publish(publicTrip.copy(capacityReliable = false))
                    createdPlaceholder = true
                    remoteTripId = created.tripId
                    effectiveTrip = publicTrip.copy(
                        remoteId = remoteTripId,
                        publicToken = created.publicToken,
                        publicUrl = created.publicUrl,
                    )
                    effectiveClaims = synthesized.capacityClaims
                    shapePreserved = false
                    UnifiedDebugEventStore.record(
                        "PUBLIC_PROJECTION_REMOTE_MISSING_RECREATED_0410",
                        context.packageName,
                        "tripKey=$diagnosticTripKey staleRemotePresent=${staleRemoteTripId.isNotBlank()} localBindingPresent=${existingBinding != null} recreatedRemoteChanged=${staleRemoteTripId != remoteTripId} strongIdentity=true canonicalTripIdPresent=${canonicalTripId.isNotBlank()}",
                    )
                    reconcile()
                }
                (existingBinding != null || remoteStateHint0402 != null) && isImmutablePublicTripShapeFailure(firstError) -> {
                    UnifiedDebugEventStore.record(
                        "PUBLIC_CAPACITY_SERVER_SHAPE_CONFLICT_0402", context.packageName,
                        "tripKey=$diagnosticTripKey remoteIdentityPresent=true serverStatePresent=${remoteStateHint0402 != null} canonicalStops=${effectiveTrip.stops.size} action=preserve_remote_and_local_state noShapeMutation=true error=${firstError.message.orEmpty().take(240)}",
                    )
                    throw firstError
                }
                else -> throw firstError
            }
        }

        if (response.stale) {
            throw PublicationStaleRevision0387(response.entityRevision)
        }
        if (response.stopShapeMigrationCount0439 > 0) {
            UnifiedDebugEventStore.record(
                "PUBLIC_CAPACITY_CANONICAL_SHAPE_MIGRATED_0440",
                context.packageName,
                "tripKey=$diagnosticTripKey migratedBookings=${response.stopShapeMigrationCount0439} " +
                    "canonicalStops=${effectiveTrip.stops.size} transportRevision=$entityRevision " +
                    "logicalRevision=${effectiveTrip.canonicalRevision} strongIdentity=true",
            )
        }
        saveExternalBinding(
            store = store,
            remoteTripId = remoteTripId,
            publicToken = publicTrip.publicToken,
            synthesized = synthesized,
            effectiveTrip = effectiveTrip,
            canonicalTripId = canonicalTripId,
            entityRevision = entityRevision,
            seatAllocationVersion = seatAllocationVersion,
            serverCanonicalRevision0468 = response.canonicalRevision,
            serverCanonicalStateHash0468 = response.canonicalStateHash,
        )
        UnifiedDebugEventStore.record(
            if (response.changed) "PUBLIC_CAPACITY_INCREMENTAL_PUBLISHED" else "PUBLIC_CAPACITY_INCREMENTAL_NO_OP",
            context.packageName,
            "tripKey=$diagnosticTripKey revision=${synthesized.snapshotRevision.takeLast(12)} entityRevision=$entityRevision mutationId=${mutationId0421.take(56)} idempotencyKey=${idempotencyKey0421.take(56)} outboxEventId=${outboxEventId.take(56)} occupancyRevision=${response.occupancyRevision} sourceBlaBlaSeats=${synthesized.publishedSeats ?: -1} rotaCertaSeats=${effectiveTrip.rotaCertaSeatAllocation ?: 0} confirmedOccupiedSeats=${synthesized.bookedSeats} availableMin=${response.availableSeatsMinimum} availableMax=${response.availableSeatsMaximum} changed=${response.changed} stale=${response.stale} shapePreserved=$shapePreserved createdPlaceholder=$createdPlaceholder fullSyncRequested=false",
        )
        AgendaTrace.event(
            context,
            "PUBLIC_CAPACITY_SNAPSHOT_COMMITTED",
            "tripKey=$diagnosticTripKey changed=${response.changed} occupancyRevision=${response.occupancyRevision} availableMin=${response.availableSeatsMinimum} availableMax=${response.availableSeatsMaximum}",
            traceId,
            parentOperationId,
        )
        return ExternalCapacitySnapshotSyncResult(
            published = true,
            claimsApplied = if (response.changed && synthesized.sourceComplete) effectiveClaims.size else 0,
            changed = response.changed,
            shapePreserved = shapePreserved,
        )
    }

    private fun saveExternalBinding(
        store: TripStore,
        remoteTripId: String,
        publicToken: String,
        synthesized: PublicAgendaExternalTrip,
        effectiveTrip: Trip,
        canonicalTripId: String,
        entityRevision: Long,
        seatAllocationVersion: Long,
        serverCanonicalRevision0468: Long = 0L,
        serverCanonicalStateHash0468: String = "",
    ) {
        val existing = store.publicExternalBindingForStrongIdentity(
            synthesized.profileUuid,
            synthesized.blablaTripId,
        )
        val stableInternalTripId = existing?.bookingTripId?.takeIf(String::isNotBlank)
            ?: canonicalTripId.takeIf(String::isNotBlank)
            ?: "public-external:$remoteTripId"
        store.savePublicExternalBinding(
            PublicExternalTripBinding(
                remoteTripId = remoteTripId,
                publicToken = publicToken,
                bookingTripId = stableInternalTripId,
                profileUuid = synthesized.profileUuid,
                blablaTripId = synthesized.blablaTripId,
                blablaTripHref = synthesized.blablaTripHref,
                blablaPublicHref = synthesized.blablaPublicHref,
                title = effectiveTrip.title,
                departureAtMillis = effectiveTrip.departureAtMillis,
                capacity = effectiveTrip.capacity,
                stops = effectiveTrip.stops,
                canonicalRevision = serverCanonicalRevision0468.coerceAtLeast(0L)
                    .takeIf { it > 0L }
                    ?: effectiveTrip.canonicalRevision.coerceAtLeast(0L).takeIf { it > 0L }
                    ?: (existing?.canonicalRevision ?: 0L),
                seatAllocationVersionUsed = maxOf(existing?.seatAllocationVersionUsed ?: 0L, seatAllocationVersion),
                externalFingerprint = if (synthesized.sourceComplete) {
                    synthesized.snapshotRevision
                } else {
                    existing?.externalFingerprint.orEmpty()
                },
                stateHash = serverCanonicalStateHash0468.ifBlank { effectiveTrip.canonicalStateHash },
            ),
        )
    }

    private fun isRemoteTripNotFound(error: Throwable): Boolean =
        error is IllegalStateException && error.message.orEmpty().contains("HTTP 404")

    internal fun isImmutablePublicTripShapeFailure(error: Throwable): Boolean =
        error is IllegalStateException && error.message.orEmpty().let { message ->
            message.contains("A estrutura de paradas não pode mudar depois da primeira reserva.") ||
                message.contains("Capacidade e estrutura de paradas não podem mudar depois da primeira reserva.")
        }

    internal fun preserveExternalBindingShape(
        publicTrip: Trip,
        binding: PublicExternalTripBinding,
    ): Trip = publicTrip.copy(
        remoteId = binding.remoteTripId,
        stops = binding.stops,
    )

    internal fun remapExternalClaimsToBindingStructure(
        claims: List<Booking>,
        observedStops: List<TripStop>,
        preservedTrip: Trip,
    ): List<Booking> {
        val sourceById = observedStops.associateBy(TripStop::id)
        val targetStops = preservedTrip.stops.sortedBy(TripStop::order)
        if (targetStops.size < 2) return claims

        fun keys(stop: TripStop): Set<String> = sequenceOf(stop.name, stop.address)
            .map(::normalizePlace)
            .filter(String::isNotBlank)
            .toSet()

        fun targetFor(source: TripStop?): TripStop? {
            val sourceKeys = source?.let(::keys).orEmpty()
            if (sourceKeys.isEmpty()) return null
            return targetStops.firstOrNull { target -> keys(target).any(sourceKeys::contains) }
        }

        val first = targetStops.first()
        val last = targetStops.last()
        return claims.map { claim ->
            val requestedBoarding = targetFor(sourceById[claim.boardingStopId])
            val requestedDropoff = targetFor(sourceById[claim.dropoffStopId])
            val fromIndex = requestedBoarding?.let { targetStops.indexOf(it) } ?: -1
            val toIndex = requestedDropoff?.let { targetStops.indexOf(it) } ?: -1
            val boarding = if (fromIndex >= 0 && toIndex > fromIndex) requestedBoarding!! else first
            val dropoff = if (fromIndex >= 0 && toIndex > fromIndex) requestedDropoff!! else last
            claim.copy(
                tripId = preservedTrip.id,
                boardingStopId = boarding.id,
                dropoffStopId = dropoff.id,
            )
        }
    }

    private suspend fun syncLocalCapacityClaims(
        api: TripRemoteApi,
        remoteTripId: String,
        localTrip: Trip,
        localBookings: List<Booking>,
    ): Int {
        val mirrors = localCapacityMirrors(localTrip, localBookings)

        val currentMirrorIds = mirrors.map(Booking::id).toSet()
        val remoteMirrorBookings = api.listBookings(remoteTripId).bookings
            .filter { it.sourceReference.startsWith(LOCAL_MIRROR_PREFIX) }

        var synced = 0

        remoteMirrorBookings
            .filterNot { it.id in currentMirrorIds }
            .filterNot { it.status == BookingStatus.CANCELLED.name || it.status == BookingStatus.EXPIRED.name }
            .forEach { stale ->
                api.upsertDriverBooking(
                    remoteTripId = remoteTripId,
                    booking = stale.toLocalBooking(localTrip.id).copy(
                        passengerName = "Ocupação sincronizada",
                        passengerContact = "",
                        status = BookingStatus.CANCELLED,
                    ),
                )
                synced++
            }

        mirrors.forEach { mirror ->
            api.upsertDriverBooking(remoteTripId, mirror)
            synced++
        }

        return synced
    }

    /**
     * Transport contract is broader than capacity participation: terminal Rota Certa
     * bookings must reach the backend so a remote REQUESTED/CONFIRMED record can be
     * cancelled/rejected/expired instead of continuing to consume seats.
     */
    internal fun protectedBookingMustBeTransmitted0422(booking: Booking): Boolean =
        booking.source == BookingSource.ROTA_CERTA

    internal fun protectedBookingParticipatesInCapacitySnapshot0421(
        booking: Booking,
        nowMillis: Long,
    ): Boolean {
        if (booking.source != BookingSource.ROTA_CERTA || booking.seats <= 0) return false
        return when (booking.status) {
            BookingStatus.CONFIRMED,
            BookingStatus.REQUESTED,
            -> true
            BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
            BookingStatus.REJECTED,
            BookingStatus.CANCELLED,
            BookingStatus.EXPIRED,
            -> false
        }
    }

    internal fun localCapacityMirrors(
        localTrip: Trip,
        localBookings: List<Booking>,
    ): List<Booking> = localBookings
        .filterNot { it.source == BookingSource.ROTA_CERTA }
        .map { booking ->
            val fingerprint = sha256(booking.id).take(32)
            booking.copy(
                id = "mirror-$fingerprint",
                tripId = localTrip.id,
                passengerName = "Ocupação sincronizada",
                passengerContact = "",
                sourceReference = "$LOCAL_MIRROR_PREFIX$fingerprint",
                occupancyGroupId = booking.occupancyGroupId ?: "local:$fingerprint",
            )
        }

    private suspend fun syncExternalCapacityClaims(
        api: TripRemoteApi,
        remoteTripId: String,
        publicTrip: Trip,
        claims: List<Booking>,
    ): Int {
        val remoteBookings = api.listBookings(remoteTripId).bookings
        // External BlaBlaCar occupancy is mirrored only for passenger/segment placement.
        // The free-seat number already comes from BlaBlaCar, so it is never subtracted twice.
        val desiredClaims = claims
        val currentIds = desiredClaims.map(Booking::id).toSet()
        val legacyId = "blablacar-" + publicTrip.publicToken.take(40)
        val remoteMirrors = remoteBookings.filter { remote ->
            remote.source == BookingSource.BLABLACAR &&
                (remote.sourceReference.startsWith(EXTERNAL_MIRROR_PREFIX) || remote.id == legacyId)
        }
        var synced = 0
        remoteMirrors
            .filterNot { it.id in currentIds }
            .filterNot { it.status == BookingStatus.CANCELLED.name || it.status == BookingStatus.EXPIRED.name }
            .forEach { stale ->
                api.upsertDriverBooking(
                    remoteTripId,
                    stale.toLocalBooking(publicTrip.id).copy(
                        passengerName = "Ocupação BlaBlaCar sincronizada",
                        passengerContact = "",
                        status = BookingStatus.CANCELLED,
                    ),
                )
                synced++
            }
        desiredClaims.forEach { claim ->
            api.upsertDriverBooking(remoteTripId, claim)
            synced++
        }
        return synced
    }

    internal fun toCanonicalExternalProjection0406(
        canonical: Trip,
        source: BlaBlaCollectorTrip,
        nowMillis: Long = System.currentTimeMillis(),
    ): PublicAgendaExternalTrip? {
        if (canonical.deleted || canonical.status == TripStatus.CANCELLED) return null

        // The TripStore snapshot is already the authoritative domain object at this
        // point. A durable publication retry must not re-run collector discovery
        // eligibility (future departure, raw date parsing or raw route completeness)
        // through toPublicTrip(), otherwise a valid canonical card silently becomes
        // null merely because time advanced after the original collection.
        val verifiedPublishedSeats = source.published_seats?.takeIf { it in 0..999 }
        val passengerSeats = source.passengers.sumOf { it.seats.coerceAtLeast(1) }
        val bookedSeats = source.booked_seats.coerceAtLeast(passengerSeats).coerceIn(0, 999)
        val projectedTrip = canonical.copy(
            remoteId = canonical.remoteId ?: canonical.publicToken.takeIf(String::isNotBlank),
            recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
            deleted = false,
            deletedAtMillis = 0L,
        )
        val sourceReference = source.trip_id.orEmpty()
            .ifBlank { source.trip_href.orEmpty() }
            .ifBlank { "BLABLACAR:" + projectedTrip.publicToken }
        val claims = externalCapacityClaims(
            source = source,
            trip = projectedTrip,
            bookedSeats = bookedSeats,
            sourceReference = sourceReference,
        )
        val allocation = canonical.rotaCertaSeatAllocation?.coerceIn(0, 999) ?: 0
        return PublicAgendaExternalTrip(
            trip = projectedTrip,
            bookedSeats = bookedSeats,
            sourceReference = sourceReference,
            capacityClaims = claims,
            publishedSeats = verifiedPublishedSeats,
            profileUuid = source.profile_uuid.trim(),
            blablaTripId = source.trip_id.orEmpty().trim(),
            blablaTripHref = source.trip_href.orEmpty().trim(),
            blablaPublicHref = projectedTrip.blablaPublicUrl.orEmpty(),
            sourceComplete = verifiedPublishedSeats != null && source.passenger_roster_complete,
            snapshotRevision = canonical.externalSnapshotFingerprint.ifBlank {
                externalCapacitySnapshotRevision(source, allocation)
            },
            realAvailableSeats = (canonical.capacity - bookedSeats).coerceAtLeast(0),
        )
    }

    internal fun toPublicTrip(
        source: BlaBlaCollectorTrip,
        capacity: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        rotaCertaSeatAllocation: Int = 0,
    ): PublicAgendaExternalTrip? {
        val departure = parseDateTime(source.date, source.departure_time, zoneId) ?: return null
        if (departure <= nowMillis) return null

        val origin = source.actual_departure?.takeIf(String::isNotBlank)
            ?: source.search_from?.takeIf(String::isNotBlank)
            ?: return null
        val destination = source.actual_arrival?.takeIf(String::isNotBlank)
            ?: source.search_to?.takeIf(String::isNotBlank)
            ?: return null
        if (normalizePlace(origin) == normalizePlace(destination)) return null

        var arrival = parseDateTime(source.date, source.arrival_time, zoneId)
        if (arrival != null && arrival < departure) arrival += DAY_MILLIS

        val identity = stableIdentity(source)
        val token = "bb${sha256(identity).take(30)}"
        val safeCapacity = capacity.coerceIn(0, 999)
        val verifiedPublishedSeats = source.published_seats?.takeIf { it in 0..999 }
        val passengerSeats = source.passengers.sumOf { it.seats.coerceAtLeast(1) }
        val observedBooked = source.booked_seats.coerceAtLeast(passengerSeats)
        val booked = observedBooked.coerceIn(0, 999)

        val stopLabels = buildObservedStopLabels(origin, destination, source.itinerary_stops)
        val wholeTripPriceCents = parsePriceCents(source.price)
        val stops = stopLabels.mapIndexed { index, label ->
            val isFirst = index == 0
            val isLast = index == stopLabels.lastIndex
            TripStop(
                id = "stop-$index-$token",
                order = index,
                name = shortPlace(label),
                address = label,
                plannedDepartureMillis = departure.takeIf { isFirst },
                plannedArrivalMillis = arrival.takeIf { isLast },
                // A single BlaBlaCar price is a whole-trip observation. Never distribute it
                // over intermediate segments because that would invent per-segment prices.
                priceToNextCents = wholeTripPriceCents.takeIf { stopLabels.size == 2 && isFirst } ?: 0L,
            )
        }

        val trip = Trip(
            id = "public:$token",
            title = "${shortPlace(origin)} → ${shortPlace(destination)}",
            departureAtMillis = departure,
            capacity = safeCapacity,
            rotaCertaSeatAllocation = rotaCertaSeatAllocation.coerceIn(0, 999),
            status = TripStatus.PUBLISHED,
            stops = stops,
            publicToken = token,
            notes = "",
            remoteId = token,
            blablaProfileUuid = source.profile_uuid.trim().takeIf(String::isNotEmpty),
            blablaTripId = source.trip_id.orEmpty().trim().takeIf(String::isNotEmpty),
            blablaManageUrl = source.trip_href
                ?.takeIf(BlaBlaCollectorUrlModule::isManageTarget)
                ?.let(BlaBlaCollectorUrlModule::canonical)
                ?.takeIf(String::isNotBlank),
            blablaPublicUrl = BlaBlaCollectorUrlModule.publicTripForCollectorState(
                source.public_trip_href,
                source.trip_id,
                source.public_trip_href_binding,
            ),
            publicBookingEnabled = true,
            itineraryAuthoritative = source.itinerary_authoritative,
            publishedSeats = verifiedPublishedSeats,
            publicTimezoneId0411 = zoneId.id,

            capacityReliable = false,
        )
        val sourceReference = source.trip_id.orEmpty()
            .ifBlank { source.trip_href.orEmpty() }
            .ifBlank { "BLABLACAR:$token" }
        val claims = externalCapacityClaims(source, trip, booked, sourceReference)
        return PublicAgendaExternalTrip(
            trip = trip,
            bookedSeats = booked,
            sourceReference = sourceReference,
            capacityClaims = claims,
            publishedSeats = verifiedPublishedSeats,
            profileUuid = source.profile_uuid.trim(),
            blablaTripId = source.trip_id.orEmpty().trim(),
            blablaTripHref = source.trip_href.orEmpty().trim(),
            blablaPublicHref = trip.blablaPublicUrl.orEmpty(),
            sourceComplete = verifiedPublishedSeats != null && source.passenger_roster_complete,
            snapshotRevision = externalCapacitySnapshotRevision(source, rotaCertaSeatAllocation),
            realAvailableSeats = (safeCapacity - booked).coerceAtLeast(0),
        )
    }

    internal fun buildObservedStopLabels(
        origin: String,
        destination: String,
        itineraryStops: List<String>,
    ): List<String> {
        val result = mutableListOf<String>()
        fun addObserved(raw: String) {
            val value = raw.trim().takeIf(String::isNotBlank) ?: return
            val key = normalizePlace(value)
            if (key.isBlank() || result.any { normalizePlace(it) == key }) return
            result += value
        }
        addObserved(origin)
        itineraryStops.forEach(::addObserved)
        addObserved(destination)

        val originKey = normalizePlace(origin)
        val destinationKey = normalizePlace(destination)
        val middle = result.filter {
            val key = normalizePlace(it)
            key != originKey && key != destinationKey
        }
        return listOf(origin) + middle + listOf(destination)
    }

    internal fun externalCapacityClaims(
        source: BlaBlaCollectorTrip,
        trip: Trip,
        bookedSeats: Int,
        sourceReference: String,
    ): List<Booking> {
        val stops = trip.stops.sortedBy(TripStop::order)
        if (stops.size < 2 || bookedSeats <= 0) return emptyList()
        val first = stops.first()
        val last = stops.last()
        fun stopFor(label: String?): TripStop? {
            val key = label?.takeIf(String::isNotBlank)?.let(::normalizePlace).orEmpty()
            if (key.isBlank()) return null
            return stops.firstOrNull { normalizePlace(it.name) == key || normalizePlace(it.address) == key }
        }

        val claims = mutableListOf<Booking>()
        var representedSeats = 0
        source.passengers.forEachIndexed { index, passenger ->
            val seats = passenger.seats.coerceAtLeast(1)
            if (representedSeats >= bookedSeats) return@forEachIndexed
            val effectiveSeats = seats.coerceAtMost(bookedSeats - representedSeats)
            val from = stopFor(passenger.boarding)
            val to = stopFor(passenger.dropoff)
            val fromIndex = from?.let { stop -> stops.indexOfFirst { it.id == stop.id } } ?: -1
            val toIndex = to?.let { stop -> stops.indexOfFirst { it.id == stop.id } } ?: -1
            val boarding = if (fromIndex >= 0 && toIndex > fromIndex) from!! else first
            val dropoff = if (fromIndex >= 0 && toIndex > fromIndex) to!! else last
            val reservationKey = externalPassengerReservationKey(source.profile_uuid, passenger.booking_href)
                .orEmpty()
                .ifBlank { "blablacar:${trip.publicToken}:passenger:$index" }
            val claimHash = sha256(reservationKey).take(24)
            val claimId = "bbp-${trip.publicToken.take(24)}-$claimHash"
            claims += Booking(
                id = claimId,
                tripId = trip.id,
                passengerName = passenger.name.ifBlank { "Passageiro BlaBlaCar" },
                boardingStopId = boarding.id,
                dropoffStopId = dropoff.id,
                seats = effectiveSeats,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
                sourceReference = "$EXTERNAL_MIRROR_PREFIX${reservationKey.take(180)}",
                occupancyGroupId = "blablacar:$claimHash",
            )
            representedSeats += effectiveSeats
        }

        val residual = (bookedSeats - representedSeats).coerceAtLeast(0)
        if (residual > 0) {
            val residualKey = sha256("${source.profile_uuid}|${source.trip_id.orEmpty()}|residual").take(24)
            claims += Booking(
                id = "bbr-${trip.publicToken.take(24)}-$residualKey",
                tripId = trip.id,
                passengerName = "Ocupação BlaBlaCar não individualizada",
                boardingStopId = first.id,
                dropoffStopId = last.id,
                seats = residual,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
                sourceReference = "$EXTERNAL_MIRROR_PREFIX${sourceReference.take(150)}:residual",
                occupancyGroupId = "blablacar:$residualKey",
            )
        }
        return claims
    }
    internal fun externalCapacitySnapshotRevision(
        source: BlaBlaCollectorTrip,
        rotaCertaSeatAllocation: Int,
    ): String {
        fun stableHref(raw: String?): String = raw.orEmpty()
            .substringBefore("&search_uuid=")
            .substringBefore("?search_uuid=")
            .trim()
        val passengerSemantics = source.passengers.map { passenger ->
            buildString {
                append(stableHref(passenger.booking_href)).append('~')
                append(passenger.name.trim()).append('~').append(passenger.phone.orEmpty().trim()).append('~')
                append(passenger.seats.coerceAtLeast(1)).append('~')
                append(normalizePlace(passenger.boarding.orEmpty())).append('~')
                append(normalizePlace(passenger.dropoff.orEmpty()))
            }
        }.sorted()
        val semantic = buildString {
            append(source.profile_uuid.trim()).append('|')
            append(source.trip_id.orEmpty().trim()).append('|')
            append(source.date.trim()).append('|')
            append(source.departure_time.orEmpty().trim()).append('|').append(source.arrival_time.orEmpty().trim()).append('|')
            append(normalizePlace(source.search_from.orEmpty())).append('|').append(normalizePlace(source.search_to.orEmpty())).append('|')
            append(normalizePlace(source.actual_departure.orEmpty())).append('|').append(normalizePlace(source.actual_arrival.orEmpty())).append('|')
            append(source.price.orEmpty().trim()).append('|').append(source.availability.trim()).append('|')
            append(source.flags.map(String::trim).filter(String::isNotBlank).sorted().joinToString("~")).append('|')
            append(source.uuid_validation.trim()).append('|').append(source.identity_conflict).append('|')
            append(stableHref(source.trip_href)).append('|')
            append(stableHref(source.public_trip_href)).append('|')
            append(source.published_seats ?: -1).append('|').append(rotaCertaSeatAllocation.coerceIn(0, 999)).append('|')
            append(source.booked_seats.coerceAtLeast(0)).append('|').append(source.passenger_roster_complete).append('|')
            append(source.itinerary_authoritative).append('|').append(source.itinerary_stops.joinToString(">") { normalizePlace(it) }).append('|')
            passengerSemantics.forEach { passenger -> append(passenger).append(',') }
        }
        return "bbcap-v2:${sha256(semantic)}"
    }

    internal fun parsePriceCents(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return 0L
        val match = Regex("""(\d{1,4}(?:[.,]\d{1,2})?)""").find(value)?.groupValues?.getOrNull(1) ?: return 0L
        val normalized = match.replace(".", "").replace(",", ".")
        return ((normalized.toDoubleOrNull() ?: return 0L) * 100.0).toLong().coerceAtLeast(0L)
    }

    private fun parseDateTime(dateRaw: String, timeRaw: String?, zoneId: ZoneId): Long? = runCatching {
        val time = timeRaw?.trim()?.takeIf(String::isNotEmpty) ?: return@runCatching null
        LocalDate.parse(dateRaw.trim()).atTime(LocalTime.parse(time.take(5))).atZone(zoneId).toInstant().toEpochMilli()
    }.getOrNull()

    private fun stableIdentity(source: BlaBlaCollectorTrip): String = listOf(
        source.profile_uuid.trim(),
        source.trip_id.orEmpty().trim(),
        source.trip_href.orEmpty().trim(),
        source.date.trim(),
        source.departure_time.orEmpty().trim(),
        source.actual_departure.orEmpty().trim(),
        source.actual_arrival.orEmpty().trim(),
        source.search_from.orEmpty().trim(),
        source.search_to.orEmpty().trim(),
    ).joinToString("|")

    private fun shortPlace(value: String): String = value.substringBefore(',').trim().ifBlank { value.trim() }

    private fun normalizePlace(value: String): String = java.text.Normalizer
        .normalize(shortPlace(value), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val PUBLIC_LOCAL_STATUSES = setOf(
        TripStatus.PUBLISHED,
        TripStatus.FULL,
        TripStatus.STARTING,
        TripStatus.ACTIVE,
    )

    private const val LOCAL_MIRROR_PREFIX = "LOCAL_MIRROR:"
    private const val EXTERNAL_MIRROR_PREFIX = "BLABLACAR_SYNC:"
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
}
