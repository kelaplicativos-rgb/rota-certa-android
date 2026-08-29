package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

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
    val profileUuid: String = "",
    val blablaTripId: String = "",
    val blablaTripHref: String = "",
)

internal object PublicAgendaAutoSync0300 {
    suspend fun sync(
        context: Context,
        store: TripStore,
        configuredVehicleCapacity: Int,
        nowMillis: Long = System.currentTimeMillis(),
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
        val resolvedPublicProfile = PublicDriverProfileResolver(context).resolve(settings)
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
            .groupBy { passengerContactKey(it.agendaAccessWhatsapp) }
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
        val localTrips = store.trips()
            .filter { it.departureAtMillis > nowMillis }
            .filter { it.status in PUBLIC_LOCAL_STATUSES }
        AgendaTrace.operationEnd(context, localDiscoveryOperation, processedCount = localTrips.size)

        localTrips.forEach { original ->
            val publicTrip = original.copy(publicBookingEnabled = true)
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
                UnifiedDebugEventStore.record(
                    "PUBLIC_AGENDA_LOCAL_PUBLISH_FAILED",
                    context.packageName,
                    "localTrip=${original.id} reason=${error.javaClass.simpleName}",
                )
            }
        }

        val capacity = configuredVehicleCapacity.takeIf { it in 1..999 } ?: 4
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
        UnifiedDebugEventStore.record(
            "PUBLIC_AGENDA_ALL_CONNECTED_ACCOUNTS",
            context.packageName,
            "accounts=${connectedAccounts.size} trips=${allConnectedResponse?.trips?.size ?: 0} selectionFilter=false",
        )
        val externalDiscoveryOperation = AgendaTrace.operationStart(
            context,
            "EXTERNAL_TRIPS_DISCOVERY",
            "PublicAgendaAutoSync0300",
            traceId,
            syncOperation.operationId,
        )
        val externalTrips = allConnectedResponse
            ?.trips
            .orEmpty()
            .asSequence()
            .filterNot(BlaBlaCollectorTrip::identity_conflict)
            .mapNotNull { toPublicTrip(it, capacity, nowMillis) }
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
                        AgendaTrace.operationEnd(context, externalCapacityOperation, result = "synced", processedCount = synced)
                    }
                } catch (error: CancellationException) {
                    AgendaTrace.operationCancelled(context, externalCapacityOperation)
                    throw error
                } catch (error: Throwable) {
                    AgendaTrace.operationError(context, externalCapacityOperation, error)
                    throw error
                }
                seatClaimsSynced += syncedClaims

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

    internal fun isImmutablePublicTripShapeFailure(error: Throwable): Boolean =
        error is IllegalStateException &&
            error.message.orEmpty().contains(
                "Capacidade e estrutura de paradas não podem mudar depois da primeira reserva.",
            )

    internal fun preserveExternalBindingShape(
        publicTrip: Trip,
        binding: PublicExternalTripBinding,
    ): Trip = publicTrip.copy(
        remoteId = binding.remoteTripId,
        capacity = binding.capacity,
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
        val currentIds = claims.map(Booking::id).toSet()
        val legacyId = "blablacar-${publicTrip.publicToken.take(40)}"
        val remoteMirrors = api.listBookings(remoteTripId).bookings.filter { remote ->
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
        claims.forEach { claim ->
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
        val safeCapacity = capacity.coerceIn(1, 999)
        val passengerSeats = source.passengers.sumOf { it.seats.coerceAtLeast(1) }
        val observedBooked = source.booked_seats.coerceAtLeast(passengerSeats)
        val booked = if (source.availability.equals("full", true)) {
            observedBooked.coerceAtLeast(safeCapacity)
        } else {
            observedBooked
        }.coerceAtMost(safeCapacity)

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
            status = if (source.availability.equals("full", true) || booked >= safeCapacity) TripStatus.FULL else TripStatus.PUBLISHED,
            stops = stops,
            publicToken = token,
            notes = "",
            remoteId = token,
            publicBookingEnabled = true,
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
            profileUuid = source.profile_uuid.trim(),
            blablaTripId = source.trip_id.orEmpty().trim(),
            blablaTripHref = source.trip_href.orEmpty().trim(),
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
            val claimId = "bbp-${trip.publicToken.take(32)}-$index"
            claims += Booking(
                id = claimId,
                tripId = trip.id,
                passengerName = passenger.name.ifBlank { "Passageiro BlaBlaCar" },
                boardingStopId = boarding.id,
                dropoffStopId = dropoff.id,
                seats = effectiveSeats,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                capacityClaimType = CapacityClaimType.PASSENGER,
                sourceReference = "$EXTERNAL_MIRROR_PREFIX${sourceReference.take(180)}:passenger:$index",
                occupancyGroupId = "blablacar:${trip.publicToken}:passenger:$index",
            )
            representedSeats += effectiveSeats
        }

        val residual = (bookedSeats - representedSeats).coerceAtLeast(0)
        if (residual > 0) {
            claims += Booking(
                id = "bbr-${trip.publicToken.take(36)}",
                tripId = trip.id,
                passengerName = "Ocupação BlaBlaCar",
                boardingStopId = first.id,
                dropoffStopId = last.id,
                seats = residual,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                capacityClaimType = CapacityClaimType.RESERVED_SEAT,
                sourceReference = "$EXTERNAL_MIRROR_PREFIX${sourceReference.take(180)}:residual",
                occupancyGroupId = "blablacar:${trip.publicToken}:residual",
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
