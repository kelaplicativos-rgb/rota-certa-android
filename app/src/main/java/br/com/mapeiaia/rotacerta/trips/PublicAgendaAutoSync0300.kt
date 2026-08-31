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
                    "reason=${error.javaClass.simpleName}",
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
                    "canonicalPassengers=${canonicalPassengerProfiles.size} reason=${error.javaClass.simpleName}",
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
            val rotaCertaAllocation = configuredRotaCertaSeatAllocation.takeIf { it in 0..999 } ?: 0
            val withAllocation = original.copy(
                rotaCertaSeatAllocation = rotaCertaAllocation,
                publicBookingEnabled = true,
            )
            val publicTrip = withAllocation.copy(
                capacity = operationalInventoryCapacity(withAllocation, localBookings),
            )
            val localPublishOperation = AgendaTrace.operationStart(
                context,
                "LOCAL_TRIP_PUBLISH",
                "PublicAgendaAutoSync0300",
                traceId,
                syncOperation.operationId,
            )
            runCatching {
                val response = if (publicTrip.remoteId.isNullOrBlank()) {
                    api.publish(publicTrip)
                } else {
                    api.update(publicTrip)
                }
                store.saveTrip(
                    publicTrip.copy(
                        remoteId = response.tripId,
                        publicToken = response.publicToken,
                        publicUrl = response.publicUrl,
                    ),
                )
                response
            }.onSuccess { response ->
                localPublished++
                AgendaTrace.operationEnd(context, localPublishOperation, result = "published", processedCount = 1)
                val localCapacityOperation = AgendaTrace.operationStart(
                    context,
                    "LOCAL_CAPACITY_CLAIMS",
                    "PublicAgendaAutoSync0300",
                    traceId,
                    syncOperation.operationId,
                )
                runCatching {
                    syncLocalCapacityClaims(
                        api = api,
                        remoteTripId = response.tripId,
                        localTrip = original,
                        localBookings = store.bookingsFor(original.id),
                    )
                }.onSuccess { synced ->
                    seatClaimsSynced += synced
                    AgendaTrace.operationEnd(context, localCapacityOperation, result = "synced", processedCount = synced)
                    UnifiedDebugEventStore.record(
                        "PUBLIC_AGENDA_LOCAL_CAPACITY_SYNCED",
                        context.packageName,
                        "localTrip=${original.id} remoteTripPresent=true claimsSynced=$synced localBookings=${store.bookingsFor(original.id).size}",
                    )
                }.onFailure { error ->
                    if (error is CancellationException) {
                        AgendaTrace.operationCancelled(context, localCapacityOperation)
                        throw error
                    }
                    AgendaTrace.operationError(context, localCapacityOperation, error)
                    failures++
                    UnifiedDebugEventStore.record(
                        "PUBLIC_AGENDA_LOCAL_CAPACITY_SYNC_FAILED",
                        context.packageName,
                        "localTrip=${original.id} remoteTripPresent=true reason=${error.javaClass.simpleName}",
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    AgendaTrace.operationCancelled(context, localPublishOperation)
                    throw error
                }
                AgendaTrace.operationError(context, localPublishOperation, error)
                failures++
                val stackSite = error.stackTrace.firstOrNull {
                    it.className.startsWith("br.com.mapeiaia.rotacerta")
                }?.let { "${it.fileName}:${it.lineNumber}" }.orEmpty()
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_LOCAL_PUBLISH_FAILED",
                    context.packageName,
                    "trip=${original.id} source=${resolvedTripRecordOrigin(original).name} strongExternalIdentity=${canonicalExternalTripIdentityKey(original.blablaProfileUuid, original.blablaTripId, original.blablaManageUrl) != null} operation=LOCAL_TRIP_PUBLISH exception=${error.javaClass.simpleName} stackSite=$stackSite detail=${safeSyncFailureDetail0373(error)}",
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
            val publicTrip = synthesized.trip
            val diagnosticTripKey = sha256(publicTrip.publicToken).take(12)
            val existingBinding = store.publicExternalBindings().firstOrNull {
                it.publicToken == publicTrip.publicToken
            }
            var failureStage = "publish"
            var effectiveTrip = publicTrip
            var effectiveClaims = synthesized.capacityClaims
            var shapePreserved = false
            val externalPublishOperation = AgendaTrace.operationStart(
                context,
                "EXTERNAL_TRIP_PUBLISH",
                "PublicAgendaAutoSync0300",
                traceId,
                syncOperation.operationId,
            )
            try {
                val response = try {
                    val published = try {
                        api.publish(publicTrip)
                    } catch (publishError: Throwable) {
                        if (publishError is CancellationException) throw publishError
                        failureStage = "update_after_publish_failure"
                        externalRetries++
                        UnifiedDebugEventStore.record(
                            "PUBLIC_AGENDA_EXTERNAL_PUBLISH_RETRY",
                            context.packageName,
                            "index=${index + 1}/${externalTrips.size} tripKey=$diagnosticTripKey reason=${publishError.javaClass.simpleName} profileUuidPresent=${synthesized.profileUuid.isNotBlank()} blablaTripIdPresent=${synthesized.blablaTripId.isNotBlank()}",
                        )
                        AgendaTrace.event(
                            context,
                            "EXTERNAL_TRIP_UPDATE_RETRY",
                            "index=${index + 1} reasonClass=${publishError.javaClass.simpleName}",
                            traceId,
                            externalPublishOperation.operationId,
                        )
                        val updateOperation = AgendaTrace.operationStart(
                            context,
                            "EXTERNAL_TRIP_UPDATE",
                            "PublicAgendaAutoSync0300",
                            traceId,
                            externalPublishOperation.operationId,
                        )
                        try {
                            val updated = try {
                                api.update(publicTrip.copy(remoteId = publicTrip.publicToken))
                            } catch (updateError: Throwable) {
                                if (updateError is CancellationException) throw updateError
                                val binding = existingBinding
                                if (binding == null || !isImmutablePublicTripShapeFailure(updateError)) throw updateError

                                failureStage = "update_preserved_binding_shape"
                                effectiveTrip = preserveExternalBindingShape(publicTrip, binding)
                                effectiveClaims = remapExternalClaimsToBindingStructure(
                                    claims = synthesized.capacityClaims,
                                    observedStops = publicTrip.stops,
                                    preservedTrip = effectiveTrip,
                                )
                                shapePreserved = true
                                preservedShapes++
                                UnifiedDebugEventStore.record(
                                    "PUBLIC_AGENDA_EXTERNAL_SHAPE_PRESERVED",
                                    context.packageName,
                                    "index=${index + 1}/${externalTrips.size} tripKey=$diagnosticTripKey observedStops=${publicTrip.stops.size} preservedStops=${effectiveTrip.stops.size} observedCapacity=${publicTrip.capacity} preservedCapacity=${effectiveTrip.capacity} claims=${effectiveClaims.size}",
                                )
                                api.update(effectiveTrip)
                            }
                            AgendaTrace.operationEnd(context, updateOperation, result = "updated", processedCount = 1)
                            AgendaTrace.event(
                                context,
                                "EXTERNAL_TRIP_UPDATE_END",
                                "index=${index + 1} shapePreserved=$shapePreserved",
                                traceId,
                                updateOperation.operationId,
                            )
                            updated
                        } catch (error: CancellationException) {
                            AgendaTrace.operationCancelled(context, updateOperation)
                            throw error
                        } catch (error: Throwable) {
                            AgendaTrace.operationError(context, updateOperation, error)
                            throw error
                        }
                    }
                    AgendaTrace.operationEnd(context, externalPublishOperation, result = "published", processedCount = 1)
                    published
                } catch (error: CancellationException) {
                    AgendaTrace.operationCancelled(context, externalPublishOperation)
                    throw error
                } catch (error: Throwable) {
                    AgendaTrace.operationError(context, externalPublishOperation, error)
                    throw error
                }

                failureStage = "capacity_claims"
                val externalCapacityOperation = AgendaTrace.operationStart(
                    context,
                    "EXTERNAL_CAPACITY_CLAIMS",
                    "PublicAgendaAutoSync0300",
                    traceId,
                    syncOperation.operationId,
                )
                val syncedClaims = try {
                    syncExternalCapacityClaims(
                        api = api,
                        remoteTripId = response.tripId,
                        publicTrip = effectiveTrip,
                        claims = effectiveClaims,
                    ).also { synced ->
                        AgendaTrace.operationEnd(
                            context,
                            externalCapacityOperation,
                            result = if (synced > 0) "claims_applied" else "no_applicable_claims",
                            processedCount = synced,
                        )
                        UnifiedDebugEventStore.record(
                            "EXTERNAL_CAPACITY_CLAIMS_RESULT",
                            context.packageName,
                            "claimsFound=${effectiveClaims.size} claimsApplicable=$synced mutationsSent=0 mutationsConfirmed=0 scope=public_agenda_only",
                        )
                    }
                } catch (error: CancellationException) {
                    AgendaTrace.operationCancelled(context, externalCapacityOperation)
                    throw error
                } catch (error: Throwable) {
                    AgendaTrace.operationError(context, externalCapacityOperation, error)
                    throw error
                }
                seatClaimsSynced += syncedClaims
                if (synthesized.publishedSeats != null) {
                    failureStage = "capacity_reliable"
                    effectiveTrip = effectiveTrip.copy(capacityReliable = true)
                    api.update(effectiveTrip)
                    UnifiedDebugEventStore.record(
                        "PUBLIC_CAPACITY_RECONCILE_RESULT",
                        context.packageName,
                        "tripKey=" + diagnosticTripKey +
                            " operationalInventory=" + effectiveTrip.capacity +
                            " publishedSeats=" + synthesized.publishedSeats +
                            " capacityReliable=true claimsSynced=" + syncedClaims,
                    )
                }

                failureStage = "binding_save"
                val bindingOperation = AgendaTrace.operationStart(
                    context,
                    "PUBLIC_EXTERNAL_BINDING_SAVE",
                    "PublicAgendaAutoSync0300",
                    traceId,
                    syncOperation.operationId,
                )
                try {
                    store.savePublicExternalBinding(
                        PublicExternalTripBinding(
                            remoteTripId = response.tripId,
                            publicToken = response.publicToken,
                            bookingTripId = "public-external:${response.tripId}",
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
                    AgendaTrace.operationEnd(context, bindingOperation, result = "saved", processedCount = 1)
                } catch (error: Throwable) {
                    AgendaTrace.operationError(context, bindingOperation, error)
                    throw error
                }
                UnifiedDebugEventStore.record(
                    "PUBLIC_EXTERNAL_BINDING_SAVED",
                    context.packageName,
                    "remoteTripPresent=true profileUuidPresent=${synthesized.profileUuid.isNotBlank()} blablaTripIdPresent=${synthesized.blablaTripId.isNotBlank()} shapePreserved=$shapePreserved",
                )
                externalPublished++
            } catch (error: CancellationException) {
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_EXTERNAL_SYNC_CANCELLED",
                    context.packageName,
                    "index=${index + 1}/${externalTrips.size} tripKey=$diagnosticTripKey stage=$failureStage",
                )
                throw error
            } catch (error: Throwable) {
                failures++
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_EXTERNAL_SYNC_FAILED",
                    context.packageName,
                    "index=${index + 1}/${externalTrips.size} tripKey=$diagnosticTripKey stage=$failureStage reason=${error.javaClass.simpleName} claims=${effectiveClaims.size} bookedSeats=${synthesized.bookedSeats} profileUuidPresent=${synthesized.profileUuid.isNotBlank()} blablaTripIdPresent=${synthesized.blablaTripId.isNotBlank()} shapePreserved=$shapePreserved",
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

    private fun safeSyncFailureDetail0373(error: Throwable): String =
        error.message.orEmpty()
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .take(220)
            .ifBlank { "none" }

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
