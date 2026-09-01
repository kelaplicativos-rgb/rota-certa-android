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
            val localAllocation = configuredRotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
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
        val connectedAccountsOperation = AgendaTrace.operationStart(
            context,
            "CONNECTED_ACCOUNTS_READ",
            "PublicAgendaAutoSync0300",
            traceId,
            syncOperation.operationId,
        )
        val connectedAccounts = BlaBlaDynamicAccountRegistry(context).list()
        val allConnectedResponse = if (connectedAccounts.isNotEmpty()) {
            BlaBlaDynamicSessionStore(context).combinedResponse(connectedAccounts)
        } else {
            BlaBlaCollectorStateStore(context).lastResponseRecoveringDynamicSessions()
        }
        AgendaTrace.operationEnd(
            context,
            connectedAccountsOperation,
            result = "read",
            processedCount = connectedAccounts.size,
        )
        val internallyCancelledExternalReservationKeys =
            PassengerIdentityStore(context).internallyCancelledExternalReservationKeys()
        val allConnectedResponseForInternalAgenda = applyInternalCancellationTombstones(
            allConnectedResponse,
            internallyCancelledExternalReservationKeys,
        )
        UnifiedDebugEventStore.record(
            "PUBLIC_AGENDA_ALL_CONNECTED_ACCOUNTS",
            context.packageName,
            "accounts=${connectedAccounts.size} trips=${allConnectedResponse?.trips?.size ?: 0} selectionFilter=false internalCancellationTombstones=${internallyCancelledExternalReservationKeys.size}",
        )
        val externalDiscoveryOperation = AgendaTrace.operationStart(
            context,
            "EXTERNAL_TRIPS_DISCOVERY",
            "PublicAgendaAutoSync0300",
            traceId,
            syncOperation.operationId,
        )
        val externalTrips = allConnectedResponseForInternalAgenda
            ?.trips
            .orEmpty()
            .asSequence()
            .filterNot(BlaBlaCollectorTrip::identity_conflict)
            .mapNotNull { source ->
                val observedPassengerSeats = source.passengers.sumOf { it.seats.coerceAtLeast(1) }
                val observedOccupiedSeats = source.booked_seats.coerceAtLeast(observedPassengerSeats)
                val blablaQuota = source.published_seats?.takeIf { it in 0..999 } ?: 0
                val rotaCertaQuota = configuredRotaCertaAllocation
                val operationalInventory = (blablaQuota + rotaCertaQuota).coerceIn(0, 999)
                val availableSeats = (operationalInventory - observedOccupiedSeats).coerceAtLeast(0)
                UnifiedDebugEventStore.record(
                    "CAPACITY_PUBLIC_SOURCE_RESOLVED",
                    context.packageName,
                    "tripKey=${sha256(source.profile_uuid + "|" + source.trip_id.orEmpty()).take(12)} profileUuidPresent=${source.profile_uuid.isNotBlank()} blablaTripIdPresent=${!source.trip_id.isNullOrBlank()} blablaQuota=$blablaQuota rotaCertaQuota=$rotaCertaQuota operationalInventory=$operationalInventory occupied=$observedOccupiedSeats available=$availableSeats capacitySource=blablacar_quota_plus_rota_certa_quota",
                )
                toPublicTrip(
                    source = source,
                    capacity = operationalInventory,
                    rotaCertaSeatAllocation = rotaCertaQuota,
                    nowMillis = nowMillis,
                )
            }
            .filterNot { synthesized ->
                localTrips.any { local -> samePhysicalTrip(local, synthesized.trip) }
            }
            .distinctBy { it.trip.publicToken }
            .take(100)
            .toList()
        AgendaTrace.operationEnd(context, externalDiscoveryOperation, processedCount = externalTrips.size)

        externalTrips.forEachIndexed { index, synthesized ->
            val diagnosticTripKey = sha256(synthesized.trip.publicToken).take(12)
            val resolvedPublicIdentity = store.publicExternalBindings()
                .firstOrNull { it.publicToken == synthesized.trip.publicToken }
                ?.remoteTripId
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
            "accounts=${connectedAccounts.size} totalTrips=${localTrips.size + externalTrips.size} processed=${localPublished + externalPublished} localPublished=$localPublished externalPublished=$externalPublished claims=$seatClaimsSynced failures=$failures cancelled=0 retries=$externalRetries preservedShape=$preservedShapes",
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
    ): Boolean = withContext(Dispatchers.IO) {
        val settings = store.onlineSettings()
        if (!settings.configured) return@withContext false
        val original = snapshotTrip ?: store.trips().firstOrNull { it.id == localTripId } ?: return@withContext false
        if (!original.isCanonicalLocalPublishSource() || original.departureAtMillis <= nowMillis || original.status !in PUBLIC_LOCAL_STATUSES) {
            return@withContext false
        }
        val localBookings = snapshotBookings ?: store.bookingsFor(original.id)
        val allocation = configuredRotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
        val withAllocation = original.copy(
            rotaCertaSeatAllocation = allocation,
        )
        var publicTrip = withAllocation.copy(
            capacity = operationalInventoryCapacity(withAllocation, localBookings),
            capacityReliable = true,
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
        var remoteTripId = publicTrip.remoteId?.takeIf(String::isNotBlank) ?: publicTrip.publicToken
        var created = false
        val startedAt = System.nanoTime()

        suspend fun reconcile(): DriverCapacitySnapshotResponse = api.reconcileCapacitySnapshot(
            remoteTripId = remoteTripId,
            trip = publicTrip.copy(remoteId = remoteTripId),
            claims = mirrors,
            protectedBookings = if (entityRevision > 0L) {
                localBookings.filter { it.source == BookingSource.ROTA_CERTA }
            } else {
                emptyList()
            },
            claimNamespace = LOCAL_MIRROR_PREFIX,
            snapshotRevision = revision,
            entityRevision = entityRevision,
            canonicalTripId = original.id,
            outboxEventId = outboxEventId,
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
        if (entityRevision > 0L && response.stale) {
            throw PublicationStaleRevision0387(response.entityRevision)
        }
        UnifiedDebugEventStore.record(
            if (response.changed) "PUBLIC_LOCAL_CAPACITY_INCREMENTAL_PUBLISHED" else "PUBLIC_LOCAL_CAPACITY_INCREMENTAL_NO_OP",
            context.packageName,
            "tripKey=${sha256(original.id).take(12)} revision=${revision.takeLast(12)} entityRevision=$entityRevision outboxEventId=${outboxEventId.take(56)} occupancyRevision=${response.occupancyRevision} rotaCertaSeats=$allocation confirmedOccupiedSeats=${operationalSeatSummary(publicTrip, localBookings).confirmedPassengerSeats} availableMin=${response.availableSeatsMinimum} availableMax=${response.availableSeatsMaximum} changed=${response.changed} stale=${response.stale} createdPlaceholder=$created durationMs=$elapsedMs fullSyncRequested=false",
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
            append(trip.departureAtMillis).append('|').append(trip.status.name).append('|')
            append(trip.publicBookingEnabled).append('|').append(trip.itineraryAuthoritative).append('|')
            append(trip.capacityReliable).append('|').append(trip.publishedSeats ?: -1).append('|')
            append(rotaCertaSeatAllocation.coerceIn(0, 999)).append('|')
            trip.stops.sortedBy(TripStop::order).forEach { stop ->
                append(stop.id).append('~').append(stop.order).append('~').append(normalizePlace(stop.name)).append('~')
                append(normalizePlace(stop.address)).append('~').append(stop.priceToNextCents).append(',')
            }
            bookings.sortedBy(Booking::id).forEach { booking ->
                append(booking.id).append('~').append(booking.boardingStopId).append('~').append(booking.dropoffStopId).append('~')
                append(booking.seats).append('~').append(booking.status.name).append('~')
                append(booking.operationalStatus.name).append('~').append(booking.paymentStatus.name).append('~')
                append(booking.lastDriverSelection.trim()).append('~').append(booking.source.name).append('~')
                append(booking.capacityClaimType.name).append('~').append(booking.sourceReference).append('~')
                append(booking.occupancyGroupId.orEmpty()).append(',')
            }
        }
        return "localcap-v1:${sha256(semantic)}"
    }

    suspend fun syncExternalTripIncremental(
        context: Context,
        store: TripStore,
        source: BlaBlaCollectorTrip,
        configuredRotaCertaSeatAllocation: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
        entityRevision: Long = 0L,
        outboxEventId: String = "",
        externalAccountId: String = "",
        canonicalTripId: String = "",
        seatAllocationVersion: Long = 0L,
    ): Boolean = withContext(Dispatchers.IO) {
        if (source.identity_conflict) return@withContext false
        val profileUuid = source.profile_uuid.trim()
        val tripId = source.trip_id?.trim().orEmpty()
        val boundInternalTripId = store.publicExternalBindingForStrongIdentity(profileUuid, tripId)?.bookingTripId.orEmpty()
        val resolvedInternalTripId = canonicalTripId.takeIf(String::isNotBlank)
            ?: boundInternalTripId.takeIf(String::isNotBlank)
            ?: if (entityRevision > 0L) {
                strongExternalCanonicalTripId0387(
                    TripPublicationOutbox0387(context).tenantId,
                    externalAccountId,
                    profileUuid,
                    tripId,
                )
            } else ""
        if (entityRevision > 0L) {
            val accounts = BlaBlaDynamicAccountRegistry(context).list().filter { account ->
                account.id == externalAccountId && account.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true
            }
            val expectedStrongId = strongExternalCanonicalTripId0387(
                TripPublicationOutbox0387(context).tenantId,
                externalAccountId,
                profileUuid,
                tripId,
            )
            require(
                profileUuid.isNotBlank() && tripId.isNotBlank() && externalAccountId.isNotBlank() && accounts.size == 1 &&
                    resolvedInternalTripId.isNotBlank() &&
                    (resolvedInternalTripId == expectedStrongId || resolvedInternalTripId == boundInternalTripId)
            ) {
                "Sincronização externa determinística exige internalTripId + conta + profile UUID + tripId fortes."
            }
        }
        val settings = store.onlineSettings()
        if (!settings.configured) return@withContext false
        val rotaCertaQuota = configuredRotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
        val blablaQuota = source.published_seats?.takeIf { it in 0..999 } ?: 0
        val synthesized = toPublicTrip(
            source = source,
            capacity = (blablaQuota + rotaCertaQuota).coerceIn(0, 999),
            rotaCertaSeatAllocation = rotaCertaQuota,
            nowMillis = nowMillis,
        ) ?: return@withContext false
        if (store.trips().filter(Trip::isCanonicalLocalPublishSource).any { samePhysicalTrip(it, synthesized.trip) }) {
            return@withContext false
        }

        val startedAt = System.nanoTime()
        val tripKey = sha256(synthesized.trip.publicToken).take(12)
        val failureContext = AgendaFailureEvidence.tripContext(
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
        UnifiedDebugEventStore.record(
            "PUBLIC_AGENDA_INCREMENTAL_START",
            context.packageName,
            "tripKey=$tripKey sourceComplete=${synthesized.sourceComplete} revision=${synthesized.snapshotRevision.takeLast(12)} fullSyncRequested=false",
        )
        val result = try {
            syncExternalCapacitySnapshot(
                context = context,
                store = store,
                api = TripRemoteApi(settings),
                synthesized = synthesized,
                traceId = AgendaTrace.currentTraceId(),
                parentOperationId = null,
                entityRevision = entityRevision,
                canonicalTripId = resolvedInternalTripId,
                outboxEventId = outboxEventId,
                seatAllocationVersion = seatAllocationVersion,
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
        outboxEventId: String = "",
        seatAllocationVersion: Long = 0L,
    ): ExternalCapacitySnapshotSyncResult {
        val publicTrip = synthesized.trip
        val diagnosticTripKey = sha256(publicTrip.publicToken).take(12)
        val existingBinding = store.publicExternalBindingForStrongIdentity(
            synthesized.profileUuid,
            synthesized.blablaTripId,
        ) ?: store.publicExternalBindings().firstOrNull { it.publicToken == publicTrip.publicToken }

        if (!synthesized.sourceComplete) {
            if (existingBinding != null) {
                UnifiedDebugEventStore.record(
                    "PUBLIC_CAPACITY_FAIL_CLOSED",
                    context.packageName,
                    "tripKey=$diagnosticTripKey action=preserve_previous_snapshot reason=incomplete_source previousBinding=true publishedSeatsKnown=${synthesized.publishedSeats != null} fullSyncRequested=false",
                )
                return ExternalCapacitySnapshotSyncResult(published = true, changed = false)
            }
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
            saveExternalBinding(store, response.tripId, response.publicToken, synthesized, publicTrip)
            UnifiedDebugEventStore.record(
                "PUBLIC_CAPACITY_UNKNOWN_PUBLISHED",
                context.packageName,
                "tripKey=$diagnosticTripKey capacityReliable=false reason=first_snapshot_incomplete reservationBlocked=true",
            )
            return ExternalCapacitySnapshotSyncResult(published = true, changed = true)
        }

        var remoteTripId = existingBinding?.remoteTripId ?: publicTrip.publicToken
        var effectiveTrip = publicTrip.copy(remoteId = remoteTripId)
        var effectiveClaims = synthesized.capacityClaims
        var shapePreserved = false
        var createdPlaceholder = false

        suspend fun reconcile(): DriverCapacitySnapshotResponse = api.reconcileCapacitySnapshot(
            remoteTripId = remoteTripId,
            trip = effectiveTrip,
            claims = effectiveClaims,
            claimNamespace = EXTERNAL_MIRROR_PREFIX,
            snapshotRevision = synthesized.snapshotRevision,
            entityRevision = entityRevision,
            canonicalTripId = canonicalTripId,
            outboxEventId = outboxEventId,
        )

        val response = try {
            reconcile()
        } catch (firstError: Throwable) {
            if (firstError is CancellationException) throw firstError
            when {
                existingBinding == null && isRemoteTripNotFound(firstError) -> {
                    val created = api.publish(publicTrip.copy(capacityReliable = false))
                    createdPlaceholder = true
                    remoteTripId = created.tripId
                    effectiveTrip = publicTrip.copy(remoteId = remoteTripId)
                    reconcile()
                }
                existingBinding != null && isImmutablePublicTripShapeFailure(firstError) -> {
                    effectiveTrip = preserveExternalBindingShape(publicTrip, existingBinding)
                    effectiveClaims = remapExternalClaimsToBindingStructure(
                        claims = synthesized.capacityClaims,
                        observedStops = publicTrip.stops,
                        preservedTrip = effectiveTrip,
                    )
                    remoteTripId = existingBinding.remoteTripId
                    shapePreserved = true
                    reconcile()
                }
                else -> throw firstError
            }
        }

        if (entityRevision > 0L && response.stale) {
            throw PublicationStaleRevision0387(response.entityRevision)
        }
        saveExternalBinding(store, remoteTripId, publicTrip.publicToken, synthesized, effectiveTrip)
        UnifiedDebugEventStore.record(
            if (response.changed) "PUBLIC_CAPACITY_INCREMENTAL_PUBLISHED" else "PUBLIC_CAPACITY_INCREMENTAL_NO_OP",
            context.packageName,
            "tripKey=$diagnosticTripKey revision=${synthesized.snapshotRevision.takeLast(12)} entityRevision=$entityRevision outboxEventId=${outboxEventId.take(56)} occupancyRevision=${response.occupancyRevision} sourceBlaBlaSeats=${synthesized.publishedSeats ?: -1} rotaCertaSeats=${effectiveTrip.rotaCertaSeatAllocation ?: 0} confirmedOccupiedSeats=${synthesized.bookedSeats} availableMin=${response.availableSeatsMinimum} availableMax=${response.availableSeatsMaximum} changed=${response.changed} stale=${response.stale} shapePreserved=$shapePreserved createdPlaceholder=$createdPlaceholder fullSyncRequested=false",
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
            claimsApplied = if (response.changed) effectiveClaims.size else 0,
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
    ) {
        store.savePublicExternalBinding(
            PublicExternalTripBinding(
                remoteTripId = remoteTripId,
                publicToken = publicToken,
                bookingTripId = "public-external:$remoteTripId",
                profileUuid = synthesized.profileUuid,
                blablaTripId = synthesized.blablaTripId,
                blablaTripHref = synthesized.blablaTripHref,
                blablaPublicHref = synthesized.blablaPublicHref,
                title = effectiveTrip.title,
                departureAtMillis = effectiveTrip.departureAtMillis,
                capacity = effectiveTrip.capacity,
                stops = effectiveTrip.stops,
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
            blablaPublicUrl = BlaBlaCollectorUrlModule.publicTrip(source.public_trip_href, source.trip_id),
            publicBookingEnabled = true,
            itineraryAuthoritative = source.itinerary_authoritative,
            publishedSeats = verifiedPublishedSeats,
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
        val semantic = buildString {
            append(source.profile_uuid.trim()).append('|')
            append(source.trip_id.orEmpty().trim()).append('|')
            append(source.date.trim()).append('|').append(source.departure_time.orEmpty().trim()).append('|')
            append(source.actual_departure.orEmpty().trim()).append('|').append(source.actual_arrival.orEmpty().trim()).append('|')
            append(source.price.orEmpty().trim()).append('|').append(source.availability.trim()).append('|')
            append(source.published_seats ?: -1).append('|').append(rotaCertaSeatAllocation.coerceIn(0, 999)).append('|')
            append(source.booked_seats.coerceAtLeast(0)).append('|').append(source.passenger_roster_complete).append('|')
            append(source.itinerary_authoritative).append('|').append(source.itinerary_stops.joinToString(">") { normalizePlace(it) }).append('|')
            source.passengers.forEachIndexed { index, passenger ->
                append(index).append('~').append(passenger.booking_href.orEmpty().trim()).append('~')
                append(passenger.seats.coerceAtLeast(1)).append('~')
                append(normalizePlace(passenger.boarding.orEmpty())).append('~')
                append(normalizePlace(passenger.dropoff.orEmpty())).append(',')
            }
        }
        return "bbcap-v1:${sha256(semantic)}"
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

    private fun samePhysicalTrip(left: Trip, right: Trip): Boolean {
        if (abs(left.departureAtMillis - right.departureAtMillis) > 45L * 60L * 1000L) return false
        val leftStops = left.stops.sortedBy(TripStop::order)
        val rightStops = right.stops.sortedBy(TripStop::order)
        val leftOrigin = leftStops.firstOrNull()?.name.orEmpty()
        val leftDestination = leftStops.lastOrNull()?.name.orEmpty()
        val rightOrigin = rightStops.firstOrNull()?.name.orEmpty()
        val rightDestination = rightStops.lastOrNull()?.name.orEmpty()
        return normalizePlace(leftOrigin) == normalizePlace(rightOrigin) &&
            normalizePlace(leftDestination) == normalizePlace(rightDestination)
    }

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
